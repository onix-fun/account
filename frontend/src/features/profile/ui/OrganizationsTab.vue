<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import { AuthService } from "@/api/services/AuthService";
import { useAuthStore } from "@/infra/store";
import type { OrganizationMember, SocialLink } from "@/domain";
import SocialLinksEditor from "@/features/profile/ui/SocialLinksEditor.vue";
import { hasDuplicateSocialLinks, hasInvalidSocialLinks, normalizeSocialLinks } from "@/shared/lib/socialLinks";

const props = defineProps<{
  selectedOrganizationId?: string | null;
  openCreateToken?: number;
}>();

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "info"];
}>();

const authStore = useAuthStore();
const { t } = useI18n();
const isSaving = ref(false);
const uploadingOrgId = ref<string | null>(null);
const isCreateOpen = ref(false);
const avatarInput = ref<HTMLInputElement | null>(null);
const avatarTargetOrgId = ref<string | null>(null);
const inviteByOrg = reactive<Record<string, string>>({});
const edits = reactive<Record<string, { orgName: string; displayName: string; bio: string; socialLinks: SocialLink[] }>>({});
const membersByOrg = reactive<Record<string, OrganizationMember[]>>({});
const busyMemberKey = ref<string | null>(null);

const form = reactive({
  orgName: "",
  displayName: "",
  bio: "",
});

const visibleOrganizations = computed(() => {
  if (!props.selectedOrganizationId) return authStore.organizations;
  return authStore.organizations.filter((organization) => organization.id === props.selectedOrganizationId);
});

watch(
  () => authStore.organizations,
  (organizations) => {
    organizations.forEach((organization) => {
      edits[organization.id] = {
        orgName: organization.orgName,
        displayName: organization.displayName,
        bio: organization.bio || "",
        socialLinks: (organization.socialLinks || []).map((link) => ({ ...link })),
      };
      loadMembers(organization.id).catch(() => undefined);
    });
  },
  { immediate: true, deep: true },
);
watch(() => props.openCreateToken, () => {
  if (props.openCreateToken) isCreateOpen.value = true;
});

async function createOrganization() {
  if (!form.orgName.trim() || !form.displayName.trim()) return;
  isSaving.value = true;
  try {
    await authStore.createOrganization({
      orgName: form.orgName.trim(),
      displayName: form.displayName.trim(),
      bio: form.bio.trim() || undefined,
    });
    form.orgName = "";
    form.displayName = "";
    form.bio = "";
    isCreateOpen.value = false;
    emit("message", t("organizations.created"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isSaving.value = false;
  }
}

async function saveOrganization(orgId: string) {
  const edit = edits[orgId];
  if (!edit) return;
  if (hasInvalidSocialLinks(edit.socialLinks) || hasDuplicateSocialLinks(edit.socialLinks)) {
    emit("message", t("profile.socialLinkValidationError"), "error");
    return;
  }
  isSaving.value = true;
  try {
    await authStore.updateOrganization(orgId, {
      orgName: edit.orgName.trim(),
      displayName: edit.displayName.trim(),
      bio: edit.bio.trim() || null,
      socialLinks: normalizeSocialLinks(edit.socialLinks),
    });
    emit("message", t("organizations.updated"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isSaving.value = false;
  }
}

function openAvatarUpload(orgId: string) {
  avatarTargetOrgId.value = orgId;
  avatarInput.value?.click();
}

async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] || null;
  input.value = "";
  const orgId = avatarTargetOrgId.value;
  avatarTargetOrgId.value = null;
  if (!file || !orgId) return;
  uploadingOrgId.value = orgId;
  try {
    await authStore.uploadOrganizationAvatar(orgId, file);
    emit("message", t("profile.avatarUploaded"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    uploadingOrgId.value = null;
  }
}

function addSocialLink(orgId: string) {
  const edit = edits[orgId];
  if (!edit || edit.socialLinks.length >= 10) return;
  edit.socialLinks.push({ label: "", url: "" });
}

function removeSocialLink(orgId: string, index: number) {
  const edit = edits[orgId];
  if (!edit) return;
  edit.socialLinks.splice(index, 1);
}

async function invite(orgId: string) {
  const username = inviteByOrg[orgId]?.trim();
  if (!username) return;
  try {
    await authStore.inviteOrganizationMember(orgId, { username, role: "CONTRIBUTOR" });
    await loadMembers(orgId);
    inviteByOrg[orgId] = "";
    emit("message", t("organizations.invited"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  }
}

async function loadMembers(orgId: string) {
  membersByOrg[orgId] = await AuthService.organizationMembers(orgId);
}

async function setMemberRole(orgId: string, userId: string, role: "OWNER" | "CONTRIBUTOR") {
  busyMemberKey.value = `${orgId}:${userId}:role`;
  try {
    await AuthService.updateOrganizationMember(orgId, userId, role);
    await loadMembers(orgId);
    await authStore.loadOrganizationContext();
    emit("message", t("organizations.updated"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyMemberKey.value = null;
  }
}

async function removeMember(orgId: string, userId: string) {
  busyMemberKey.value = `${orgId}:${userId}:remove`;
  try {
    await AuthService.removeOrganizationMember(orgId, userId);
    await loadMembers(orgId);
    await authStore.loadOrganizationContext();
    emit("message", t("organizations.updated"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyMemberKey.value = null;
  }
}

async function respond(invitationId: string, accept: boolean) {
  try {
    await authStore.respondOrganizationInvitation(invitationId, accept);
    emit("message", accept ? t("organizations.accepted") : t("organizations.declined"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  }
}
</script>

<template>
  <section class="grid gap-5">
    <input ref="avatarInput" class="hidden" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadAvatar" />

    <div class="ui-section-header">
      <div>
        <h2 class="ui-section-title">{{ t("organizations.management") }}</h2>
        <p class="ui-section-caption">{{ t("organizations.managementHint") }}</p>
      </div>
      <PButton :label="t('organizations.create')" icon="pi pi-plus" @click="isCreateOpen = true" />
    </div>

    <div v-if="authStore.organizationInvitations.length" class="grid gap-3">
      <h2 class="m-0 text-base font-bold text-[var(--text)]">{{ t("organizations.invitations") }}</h2>
      <UiFlatRow v-for="invitation in authStore.organizationInvitations" :key="invitation.id" class="grid gap-3 p-4">
        <div>
          <strong>{{ invitation.organization.displayName }}</strong>
          <p class="m-0 text-sm text-[var(--muted)]">{{ invitation.organization.orgName }} · {{ invitation.role }}</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <PButton :label="t('common.accept')" size="small" @click="respond(invitation.id, true)" />
          <PButton :label="t('common.decline')" size="small" severity="secondary" variant="text" @click="respond(invitation.id, false)" />
        </div>
      </UiFlatRow>
    </div>

    <div v-if="!authStore.organizations.length" class="ui-empty min-h-[320px]">
      <div class="grid gap-3 justify-items-center max-w-[360px]">
        <UiIconTile tone="info" class="!w-16 !h-16 !rounded-2xl text-2xl">
          <i class="pi pi-building"></i>
        </UiIconTile>
        <h2 class="m-0 text-xl font-bold text-[var(--text)]">{{ t("organizations.emptyTitle") }}</h2>
        <p class="m-0 text-sm text-[var(--muted)]">{{ t("organizations.emptyHint") }}</p>
        <PButton :label="t('organizations.create')" icon="pi pi-plus" @click="isCreateOpen = true" />
      </div>
    </div>

    <div v-else class="grid gap-3">
      <h2 class="m-0 text-base font-bold text-[var(--text)]">{{ t("organizations.mine") }}</h2>
      <UiSurface v-for="organization in visibleOrganizations" :key="organization.id" as="article" class="grid gap-4">
        <div class="flex items-start justify-between gap-3">
          <div class="flex items-center gap-3 min-w-0">
            <button
              type="button"
              class="w-14 h-14 rounded-2xl border-0 bg-[var(--surface-muted)] flex items-center justify-center font-bold overflow-hidden shrink-0"
              :disabled="organization.role !== 'OWNER' || uploadingOrgId === organization.id"
              @click="openAvatarUpload(organization.id)"
            >
              <img v-if="organization.avatarUrl" :src="organization.avatarUrl" alt="" class="w-full h-full object-cover" />
              <i v-else-if="uploadingOrgId === organization.id" class="pi pi-spinner pi-spin"></i>
              <span v-else>{{ organization.displayName.slice(0, 1).toUpperCase() }}</span>
            </button>
            <div class="min-w-0">
              <strong class="block truncate">{{ organization.displayName }}</strong>
            <p class="m-0 text-sm text-[var(--muted)]">{{ organization.orgName }} · {{ organization.role }}</p>
            </div>
          </div>
          <span class="px-2 py-1 rounded-full bg-[var(--surface-muted)] text-xs font-bold text-[var(--muted)]">{{ organization.role }}</span>
        </div>

        <div v-if="edits[organization.id]" class="grid gap-3">
          <div class="grid gap-3 sm:grid-cols-2">
            <PInputText v-model="edits[organization.id].orgName" :disabled="organization.role !== 'OWNER'" :placeholder="t('organizations.orgName')" />
            <PInputText v-model="edits[organization.id].displayName" :disabled="organization.role !== 'OWNER'" :placeholder="t('organizations.displayName')" />
          </div>
          <PTextarea v-model="edits[organization.id].bio" :disabled="organization.role !== 'OWNER'" auto-resize rows="3" :placeholder="t('organizations.bio')" />

          <section class="grid gap-2">
            <div class="flex items-center justify-between gap-2">
              <strong class="text-sm">{{ t("profile.socialLinks") }}</strong>
              <PButton icon="pi pi-plus" rounded variant="text" severity="secondary" :disabled="organization.role !== 'OWNER' || edits[organization.id].socialLinks.length >= 10" @click="addSocialLink(organization.id)" />
            </div>
            <SocialLinksEditor
              v-model="edits[organization.id].socialLinks"
              :disabled="organization.role !== 'OWNER' || isSaving"
              @remove="(index) => removeSocialLink(organization.id, index)"
            />
          </section>

          <PButton
            :label="t('common.save')"
            icon="pi pi-check"
            :loading="isSaving"
            class="justify-self-start"
            :disabled="organization.role !== 'OWNER' || hasInvalidSocialLinks(edits[organization.id].socialLinks) || hasDuplicateSocialLinks(edits[organization.id].socialLinks)"
            @click="saveOrganization(organization.id)"
          />
        </div>

        <div v-if="organization.role === 'OWNER'" class="flex gap-2">
          <PInputText v-model="inviteByOrg[organization.id]" class="flex-1" :placeholder="t('organizations.inviteUsername')" />
          <PButton icon="pi pi-send" :label="t('organizations.invite')" @click="invite(organization.id)" />
        </div>

        <section class="grid gap-2">
          <div class="flex items-center justify-between gap-2">
            <strong class="text-sm">{{ t("organizations.members") }}</strong>
            <PButton icon="pi pi-refresh" rounded variant="text" severity="secondary" @click="loadMembers(organization.id)" />
          </div>
          <article
            v-for="member in membersByOrg[organization.id] || []"
            :key="member.userId"
            class="grid grid-cols-[40px_minmax(0,1fr)_auto] items-center gap-3 rounded-xl bg-[var(--surface-muted)] p-3"
          >
            <span class="w-10 h-10 rounded-xl bg-[var(--surface)] flex items-center justify-center overflow-hidden font-bold">
              <img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" class="w-full h-full object-cover" />
              <span v-else>{{ member.username.slice(0, 1).toUpperCase() }}</span>
            </span>
            <span class="min-w-0">
              <strong class="block truncate">{{ member.firstName || member.lastName ? [member.firstName, member.lastName].filter(Boolean).join(" ") : member.username }}</strong>
              <small class="block truncate text-[var(--muted)]">@{{ member.username }} · {{ member.role }}</small>
            </span>
            <span class="flex items-center gap-1">
              <PButton
                v-if="organization.role === 'OWNER'"
                :label="member.role === 'OWNER' ? 'Contributor' : 'Owner'"
                size="small"
                variant="text"
                severity="secondary"
                :loading="busyMemberKey === `${organization.id}:${member.userId}:role`"
                @click="setMemberRole(organization.id, member.userId, member.role === 'OWNER' ? 'CONTRIBUTOR' : 'OWNER')"
              />
              <PButton
                v-if="organization.role === 'OWNER'"
                icon="pi pi-trash"
                rounded
                variant="text"
                severity="secondary"
                :loading="busyMemberKey === `${organization.id}:${member.userId}:remove`"
                @click="removeMember(organization.id, member.userId)"
              />
            </span>
          </article>
        </section>
      </UiSurface>
    </div>

    <PDialog
      :visible="isCreateOpen"
      modal
      dismissable-mask
      class="mobile-fullscreen-dialog w-full max-w-[520px]"
      :header="t('organizations.create')"
      @update:visible="isCreateOpen = false"
    >
      <section class="grid gap-3 p-1">
        <PButton icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="mobile-dialog-back justify-self-start" @click="isCreateOpen = false" />
        <div class="grid gap-3 sm:grid-cols-2">
          <PInputText v-model="form.orgName" :placeholder="t('organizations.orgName')" />
          <PInputText v-model="form.displayName" :placeholder="t('organizations.displayName')" />
        </div>
        <PTextarea v-model="form.bio" auto-resize rows="3" :placeholder="t('organizations.bio')" />
        <PButton :label="t('organizations.create')" icon="pi pi-plus" :loading="isSaving" class="justify-self-start" @click="createOrganization" />
      </section>
    </PDialog>
  </section>
</template>
