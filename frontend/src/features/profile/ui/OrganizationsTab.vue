<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import { useAuthStore } from "@/infra/store";
import type { SocialLink } from "@/domain";

const props = defineProps<{
  selectedOrganizationId?: string | null;
}>();

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "info"];
}>();

const authStore = useAuthStore();
const { t } = useI18n();
const isSaving = ref(false);
const isCreateOpen = ref(false);
const inviteByOrg = reactive<Record<string, string>>({});
const edits = reactive<Record<string, { displayName: string; bio: string; socialLinks: SocialLink[] }>>({});

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
        displayName: organization.displayName,
        bio: organization.bio || "",
        socialLinks: (organization.socialLinks || []).map((link) => ({ ...link })),
      };
    });
  },
  { immediate: true, deep: true },
);

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
  isSaving.value = true;
  try {
    await authStore.updateOrganization(orgId, {
      displayName: edit.displayName.trim(),
      bio: edit.bio.trim() || null,
      socialLinks: edit.socialLinks.map((link) => ({ label: link.label.trim(), url: link.url.trim() })).filter((link) => link.label || link.url),
    });
    emit("message", t("organizations.updated"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isSaving.value = false;
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
    inviteByOrg[orgId] = "";
    emit("message", t("organizations.invited"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
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
    <div class="flex items-center justify-between gap-3">
      <div>
        <h2 class="m-0 text-base font-bold text-[var(--text)]">{{ t("organizations.management") }}</h2>
        <p class="m-0 mt-1 text-sm text-[var(--muted)]">{{ t("organizations.managementHint") }}</p>
      </div>
      <PButton :label="t('organizations.create')" icon="pi pi-plus" @click="isCreateOpen = true" />
    </div>

    <div v-if="authStore.organizationInvitations.length" class="grid gap-3">
      <h2 class="m-0 text-base font-bold text-[var(--text)]">{{ t("organizations.invitations") }}</h2>
      <article v-for="invitation in authStore.organizationInvitations" :key="invitation.id" class="grid gap-3 rounded-xl bg-[var(--surface)] p-4">
        <div>
          <strong>{{ invitation.organization.displayName }}</strong>
          <p class="m-0 text-sm text-[var(--muted)]">/o/{{ invitation.organization.orgName }} · {{ invitation.role }}</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <PButton :label="t('common.accept')" size="small" @click="respond(invitation.id, true)" />
          <PButton :label="t('common.decline')" size="small" severity="secondary" variant="text" @click="respond(invitation.id, false)" />
        </div>
      </article>
    </div>

    <div v-if="!authStore.organizations.length" class="min-h-[320px] grid place-items-center rounded-2xl bg-[var(--surface)] p-8 text-center">
      <div class="grid gap-3 justify-items-center max-w-[360px]">
        <span class="w-16 h-16 rounded-2xl bg-[var(--surface-muted)] flex items-center justify-center text-2xl text-[var(--muted)]">
          <i class="pi pi-building"></i>
        </span>
        <h2 class="m-0 text-xl font-bold text-[var(--text)]">{{ t("organizations.emptyTitle") }}</h2>
        <p class="m-0 text-sm text-[var(--muted)]">{{ t("organizations.emptyHint") }}</p>
        <PButton :label="t('organizations.create')" icon="pi pi-plus" @click="isCreateOpen = true" />
      </div>
    </div>

    <div v-else class="grid gap-3">
      <h2 class="m-0 text-base font-bold text-[var(--text)]">{{ t("organizations.mine") }}</h2>
      <article v-for="organization in visibleOrganizations" :key="organization.id" class="grid gap-4 rounded-xl bg-[var(--surface)] p-4">
        <div class="flex items-start justify-between gap-3">
          <div>
            <strong>{{ organization.displayName }}</strong>
            <p class="m-0 text-sm text-[var(--muted)]">/o/{{ organization.orgName }} · {{ organization.role }}</p>
          </div>
          <span class="px-2 py-1 rounded-full bg-[var(--surface-muted)] text-xs font-bold text-[var(--muted)]">{{ organization.role }}</span>
        </div>

        <div v-if="edits[organization.id]" class="grid gap-3">
          <PInputText v-model="edits[organization.id].displayName" :placeholder="t('organizations.displayName')" />
          <PTextarea v-model="edits[organization.id].bio" auto-resize rows="3" :placeholder="t('organizations.bio')" />

          <section class="grid gap-2">
            <div class="flex items-center justify-between gap-2">
              <strong class="text-sm">{{ t("profile.socialLinks") }}</strong>
              <PButton icon="pi pi-plus" rounded variant="text" severity="secondary" :disabled="edits[organization.id].socialLinks.length >= 10" @click="addSocialLink(organization.id)" />
            </div>
            <article v-for="(link, index) in edits[organization.id].socialLinks" :key="index" class="grid grid-cols-1 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)_auto] gap-2">
              <PInputText v-model="link.label" :placeholder="t('profile.socialLinkLabel')" />
              <PInputText v-model="link.url" :placeholder="t('profile.socialLinkUrl')" />
              <PButton icon="pi pi-trash" rounded variant="text" severity="secondary" @click="removeSocialLink(organization.id, index)" />
            </article>
          </section>

          <PButton
            :label="t('common.save')"
            icon="pi pi-check"
            :loading="isSaving"
            class="justify-self-start"
            :disabled="organization.role !== 'OWNER'"
            @click="saveOrganization(organization.id)"
          />
        </div>

        <div v-if="organization.role === 'OWNER'" class="flex gap-2">
          <PInputText v-model="inviteByOrg[organization.id]" class="flex-1" :placeholder="t('organizations.inviteUsername')" />
          <PButton icon="pi pi-send" :label="t('organizations.invite')" @click="invite(organization.id)" />
        </div>
      </article>
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
