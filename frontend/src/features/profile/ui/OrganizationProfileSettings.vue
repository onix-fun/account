<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import { useAuthStore } from "@/infra/store";
import type { Organization, SocialLink } from "@/domain";
import AvatarCropper from "@/features/avatar/ui/AvatarCropper.vue";
import SocialLinksEditor from "@/features/profile/ui/SocialLinksEditor.vue";
import { hasDuplicateSocialLinks, hasInvalidSocialLinks, normalizeSocialLinks } from "@/shared/lib/socialLinks";

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
const cropFile = ref<File | null>(null);
const avatarPreviewUrl = ref<string | null>(null);
const edit = reactive<{ orgName: string; displayName: string; bio: string; socialLinks: SocialLink[] }>({
  orgName: "",
  displayName: "",
  bio: "",
  socialLinks: [],
});

const canEdit = computed(() => props.organization.role === "OWNER");
const avatarPreview = computed(() => avatarPreviewUrl.value || props.organization.avatarUrl || "");
const isDirty = computed(() => (
  edit.orgName.trim() !== props.organization.orgName ||
  edit.displayName.trim() !== props.organization.displayName ||
  edit.bio.trim() !== (props.organization.bio || "") ||
  JSON.stringify(normalizedLinks(edit.socialLinks)) !== JSON.stringify(props.organization.socialLinks || [])
));
const hasSocialLinkErrors = computed(() => hasInvalidSocialLinks(edit.socialLinks) || hasDuplicateSocialLinks(edit.socialLinks));

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

onBeforeUnmount(() => {
  revokeAvatarPreview();
});

function initials(): string {
  return props.organization.displayName.slice(0, 1).toUpperCase();
}

function normalizedLinks(links: SocialLink[]): SocialLink[] {
  return normalizeSocialLinks(links);
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
  if (hasSocialLinkErrors.value) {
    emit("message", t("profile.socialLinkValidationError"), "error");
    return;
  }
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

function openAvatarPicker() {
  if (canEdit.value && !isUploading.value) avatarInput.value?.click();
}

function onAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] || null;
  input.value = "";
  if (!file || !canEdit.value) return;

  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
    emit("message", t("profile.avatarTypeError"), "error");
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    emit("message", t("profile.avatarSizeError"), "error");
    return;
  }

  cropFile.value = file;
}

async function uploadCroppedAvatar(file: File, previewUrl: string) {
  revokeAvatarPreview();
  avatarPreviewUrl.value = previewUrl;
  cropFile.value = null;
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

function revokeAvatarPreview() {
  if (avatarPreviewUrl.value) URL.revokeObjectURL(avatarPreviewUrl.value);
  avatarPreviewUrl.value = null;
}
</script>

<template>
  <section class="grid gap-4">
    <input ref="avatarInput" class="hidden" type="file" accept="image/jpeg,image/png,image/webp" @change="onAvatarChange" />

    <UiSurface as="article" class="settings-card">
      <div class="avatar-row">
        <button
          type="button"
          class="org-avatar"
          :disabled="!canEdit || isUploading"
          :aria-label="t('profile.changePhoto')"
          @click="openAvatarPicker"
        >
          <img v-if="avatarPreview" :src="avatarPreview" alt="" />
          <i v-else-if="isUploading" class="pi pi-spinner pi-spin"></i>
          <span v-else>{{ initials() }}</span>
        </button>
        <div class="min-w-0">
          <h3>{{ t("profile.changePhoto") }}</h3>
          <p>{{ t("profile.avatarHint") }}</p>
        </div>
        <PButton
          icon="pi pi-image"
          :label="t('profile.changePhoto')"
          severity="secondary"
          variant="outlined"
          :loading="isUploading"
          :disabled="!canEdit"
          @click="openAvatarPicker"
        />
      </div>

      <span v-if="organization.role !== 'OWNER'" class="readonly-pill">{{ t("profile.readonly") }}</span>

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
    </UiSurface>

    <UiSurface as="article" class="settings-card">
      <div class="flex items-center justify-between gap-3">
        <div class="min-w-0">
          <h3>{{ t("profile.socialLinks") }}</h3>
          <p>{{ t("profile.socialLinksHint") }}</p>
        </div>
        <PButton icon="pi pi-plus" rounded variant="text" severity="secondary" :disabled="!canEdit || edit.socialLinks.length >= 10" @click="addSocialLink" />
      </div>

      <SocialLinksEditor v-model="edit.socialLinks" :disabled="!canEdit || isSaving" @remove="removeSocialLink" />
    </UiSurface>

    <PButton
      :label="t('common.save')"
      icon="pi pi-check"
      :loading="isSaving"
      :disabled="!canEdit || !isDirty || hasSocialLinkErrors"
      class="justify-self-start"
      @click="save"
    />

    <AvatarCropper
      v-if="cropFile"
      :file="cropFile"
      @cancel="cropFile = null"
      @apply="uploadCroppedAvatar"
    />
  </section>
</template>

<style scoped>
.settings-card {
  display: grid;
  gap: 14px;
}

.avatar-row {
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

.settings-card h3 {
  margin: 0;
  color: var(--text);
  font-weight: 900;
}

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
  .avatar-row {
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .avatar-row :deep(.p-button) {
    grid-column: 1 / -1;
    justify-self: start;
  }

  .readonly-pill {
    grid-column: 1 / -1;
    justify-self: start;
  }
}
</style>
