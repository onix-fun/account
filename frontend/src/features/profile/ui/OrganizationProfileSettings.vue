<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import { useAuthStore } from "@/infra/store";
import type { Organization, SocialLink } from "@/domain";

const props = defineProps<{
  organization: Organization;
}>();

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "info"];
}>();

const authStore = useAuthStore();
const { t } = useI18n();
const isSaving = ref(false);
const isUploading = ref(false);
const avatarInput = ref<HTMLInputElement | null>(null);
const edit = reactive<{ orgName: string; displayName: string; bio: string; socialLinks: SocialLink[] }>({
  orgName: "",
  displayName: "",
  bio: "",
  socialLinks: [],
});

const canEdit = computed(() => props.organization.role === "OWNER");
const isDirty = computed(() => (
  edit.orgName.trim() !== props.organization.orgName ||
  edit.displayName.trim() !== props.organization.displayName ||
  edit.bio.trim() !== (props.organization.bio || "") ||
  JSON.stringify(normalizedLinks(edit.socialLinks)) !== JSON.stringify(props.organization.socialLinks || [])
));

watch(
  () => props.organization,
  (organization) => {
    edit.orgName = organization.orgName;
    edit.displayName = organization.displayName;
    edit.bio = organization.bio || "";
    edit.socialLinks = (organization.socialLinks || []).map((link) => ({ ...link }));
  },
  { immediate: true },
);

function initials(): string {
  return props.organization.displayName.slice(0, 1).toUpperCase();
}

function normalizedLinks(links: SocialLink[]): SocialLink[] {
  return links
    .map((link) => ({ label: link.label.trim(), url: link.url.trim() }))
    .filter((link) => link.label || link.url);
}

function addSocialLink() {
  if (!canEdit.value || edit.socialLinks.length >= 10) return;
  edit.socialLinks.push({ label: "", url: "" });
}

function removeSocialLink(index: number) {
  if (!canEdit.value) return;
  edit.socialLinks.splice(index, 1);
}

async function save() {
  if (!canEdit.value || !isDirty.value) return;
  isSaving.value = true;
  try {
    await authStore.updateOrganization(props.organization.id, {
      orgName: edit.orgName.trim(),
      displayName: edit.displayName.trim(),
      bio: edit.bio.trim() || null,
      socialLinks: normalizedLinks(edit.socialLinks),
    });
    emit("message", t("organizations.updated"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isSaving.value = false;
  }
}

async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] || null;
  input.value = "";
  if (!file || !canEdit.value) return;
  isUploading.value = true;
  try {
    await authStore.uploadOrganizationAvatar(props.organization.id, file);
    emit("message", t("profile.avatarUploaded"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isUploading.value = false;
  }
}
</script>

<template>
  <section class="grid gap-4">
    <input ref="avatarInput" class="hidden" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadAvatar" />

    <article class="org-overview">
      <button
        type="button"
        class="org-avatar"
        :disabled="!canEdit || isUploading"
        :aria-label="t('profile.changePhoto')"
        @click="avatarInput?.click()"
      >
        <img v-if="organization.avatarUrl" :src="organization.avatarUrl" alt="" />
        <i v-else-if="isUploading" class="pi pi-spinner pi-spin"></i>
        <span v-else>{{ initials() }}</span>
      </button>
      <div class="min-w-0">
        <h2>{{ organization.displayName }}</h2>
        <p>{{ organization.orgName }} · {{ organization.role }}</p>
      </div>
      <span v-if="organization.role !== 'OWNER'" class="readonly-pill">{{ t("profile.readonly") }}</span>
    </article>

    <article class="settings-card">
      <div class="grid gap-3 sm:grid-cols-2">
        <label class="field">
          <span>{{ t("organizations.orgName") }}</span>
          <PInputText v-model="edit.orgName" :disabled="!canEdit" />
        </label>
        <label class="field">
          <span>{{ t("organizations.displayName") }}</span>
          <PInputText v-model="edit.displayName" :disabled="!canEdit" />
        </label>
      </div>

      <label class="field">
        <span>{{ t("organizations.bio") }}</span>
        <PTextarea v-model="edit.bio" :disabled="!canEdit" auto-resize rows="4" />
      </label>
    </article>

    <article class="settings-card">
      <div class="flex items-center justify-between gap-3">
        <div class="min-w-0">
          <h3>{{ t("profile.socialLinks") }}</h3>
          <p>{{ t("profile.socialLinksHint") }}</p>
        </div>
        <PButton icon="pi pi-plus" rounded variant="text" severity="secondary" :disabled="!canEdit || edit.socialLinks.length >= 10" @click="addSocialLink" />
      </div>

      <div class="grid gap-2">
        <div v-if="!edit.socialLinks.length" class="text-sm text-[var(--subtle)] px-1">{{ t("profile.noSocialLinks") }}</div>
        <article v-for="(link, index) in edit.socialLinks" :key="index" class="grid grid-cols-1 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_auto] gap-2 items-center">
          <PInputText v-model="link.label" :disabled="!canEdit" :placeholder="t('profile.socialLinkLabel')" />
          <PInputText v-model="link.url" :disabled="!canEdit" :placeholder="t('profile.socialLinkUrl')" />
          <PButton icon="pi pi-trash" rounded variant="text" severity="secondary" :disabled="!canEdit" @click="removeSocialLink(index)" />
        </article>
      </div>
    </article>

    <PButton
      :label="t('common.save')"
      icon="pi pi-check"
      :loading="isSaving"
      :disabled="!canEdit || !isDirty"
      class="justify-self-start"
      @click="save"
    />
  </section>
</template>

<style scoped>
.org-overview,
.settings-card {
  border-radius: 18px;
  background: var(--surface);
  padding: 16px;
}

.org-overview {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
}

.org-avatar {
  width: 64px;
  height: 64px;
  border: 0;
  border-radius: 20px;
  background: var(--surface-muted);
  color: var(--text);
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  cursor: pointer;
}

.org-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.org-overview h2,
.settings-card h3 {
  margin: 0;
  color: var(--text);
  font-weight: 900;
}

.org-overview p,
.settings-card p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
  font-weight: 600;
}

.readonly-pill {
  border-radius: 999px;
  background: var(--surface-muted);
  color: var(--muted);
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 900;
}

.settings-card {
  display: grid;
  gap: 14px;
}

.field {
  min-width: 0;
  display: grid;
  gap: 7px;
}

.field span {
  color: var(--muted);
  font-size: 12px;
  font-weight: 900;
}

@media (max-width: 560px) {
  .org-overview {
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .readonly-pill {
    grid-column: 1 / -1;
    justify-self: start;
  }
}
</style>
