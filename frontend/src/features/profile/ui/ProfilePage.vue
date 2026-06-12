<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import { AuthService } from "@/api/services/AuthService";
import { trustedRedirectUrl } from "@/infra/navigation/trustedRedirect";
import { useAuthStore } from "@/infra/store";
import AvatarCropper from "@/features/avatar/ui/AvatarCropper.vue";
import SessionsTab from "@/features/sessions/ui/SessionsTab.vue";
import SystemTab from "@/features/profile/ui/SystemTab.vue";
import AccountSwitchModal from "@/features/profile/ui/AccountSwitchModal.vue";
import { userDisplayName, userInitials } from "@/shared/lib/user";
import { isUsername } from "@/shared/lib/validation";

import { useToast } from "primevue/usetoast";

type ProfileTab = "profile" | "sessions" | "system";
type EditableProfileField = "username" | "firstName" | "lastName" | "bio";
type UsernameAvailability = "idle" | "checking" | "available" | "taken" | "invalid";

const authStore = useAuthStore();
const route = useRoute();
const { t } = useI18n();
const toast = useToast();
const activeTab = ref<ProfileTab>("profile");
const isSavingProfile = ref(false);
const isUploadingAvatar = ref(false);
const isAccountModalOpen = ref(false);
const cropFile = ref<File | null>(null);
const avatarPreviewUrl = ref<string | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const usernameAvailability = ref<UsernameAvailability>("idle");
let usernameCheckTimeout: ReturnType<typeof setTimeout> | null = null;
let usernameCheckSequence = 0;

const profileForm = reactive({
  username: "",
  firstName: "",
  lastName: "",
  bio: "",
});
const editingFields = reactive<Record<EditableProfileField, boolean>>({
  username: false,
  firstName: false,
  lastName: false,
  bio: false,
});

const displayName = computed(() => userDisplayName(authStore.currentUser));
const displayInitials = computed(() => userInitials(authStore.currentUser));
const avatarPreview = computed(() => avatarPreviewUrl.value || authStore.currentUser?.avatarUrl || "");
const backUrl = computed(() => trustedRedirectUrl(route.query.redirect));
const isProfileDirty = computed(() => {
  const user = authStore.currentUser;
  return (
    profileForm.firstName !== (user?.firstName || "") ||
    profileForm.lastName !== (user?.lastName || "") ||
    profileForm.bio !== (user?.bio || "") ||
    profileForm.username.trim() !== (user?.username || "")
  );
});
const canSaveUsername = computed(() => {
  const username = profileForm.username.trim();
  if (username.toLowerCase() === authStore.currentUser?.username.toLowerCase()) return true;
  return usernameAvailability.value === "available";
});
const editableProfileFields = computed<
  Array<{ key: EditableProfileField; label: string; type: "text" | "textarea"; autocomplete?: string }>
>(() => [
  { key: "username", label: t("auth.username"), type: "text", autocomplete: "username" },
  { key: "firstName", label: t("auth.firstName"), type: "text", autocomplete: "given-name" },
  { key: "lastName", label: t("auth.lastName"), type: "text", autocomplete: "family-name" },
  { key: "bio", label: t("profile.bio"), type: "textarea" },
]);

const syncProfileForm = () => {
  profileForm.username = authStore.currentUser?.username || "";
  profileForm.firstName = authStore.currentUser?.firstName || "";
  profileForm.lastName = authStore.currentUser?.lastName || "";
  profileForm.bio = authStore.currentUser?.bio || "";
};

watch(() => authStore.currentUser, syncProfileForm, { immediate: true });
watch(
  () => profileForm.username,
  (value) => {
    if (usernameCheckTimeout) clearTimeout(usernameCheckTimeout);
    const username = value.trim();
    const sequence = ++usernameCheckSequence;

    if (username.toLowerCase() === authStore.currentUser?.username.toLowerCase()) {
      usernameAvailability.value = "idle";
      return;
    }
    if (!isUsername(username)) {
      usernameAvailability.value = "invalid";
      return;
    }

    usernameAvailability.value = "checking";
    usernameCheckTimeout = setTimeout(async () => {
      try {
        const available = await AuthService.isUsernameAvailable(username);
        if (sequence === usernameCheckSequence) usernameAvailability.value = available ? "available" : "taken";
      } catch {
        if (sequence === usernameCheckSequence) usernameAvailability.value = "idle";
      }
    }, 450);
  },
);

onMounted(async () => {
  await authStore.fetchSessions().catch((cause) => {
    setMessage(apiErrorMessage(cause), "error");
  });
});

onBeforeUnmount(() => {
  if (usernameCheckTimeout) clearTimeout(usernameCheckTimeout);
  revokeAvatarPreview();
});

function setMessage(message: string, tone: "success" | "error" | "warn" | "info" = "success") {
  toast.add({
    severity: tone === "error" ? "error" : tone === "warn" ? "warn" : "success",
    summary: tone === "error" ? t("common.error") : t("common.success"),
    detail: message,
    life: 5000,
  });
}

const openFieldEdit = (field: EditableProfileField) => {
  editingFields[field] = true;
};

const confirmFieldEdit = async (field: EditableProfileField) => {
  if (field === "username" && !canSaveUsername.value) return;
  if (!isProfileDirty.value) {
    editingFields[field] = false;
    return;
  }
  await saveProfile();
};

const closeFieldEdits = () => {
  editingFields.username = false;
  editingFields.firstName = false;
  editingFields.lastName = false;
  editingFields.bio = false;
};

const saveProfile = async () => {
  isSavingProfile.value = true;
  try {
    await authStore.updateProfile({
      username: profileForm.username.trim(),
      firstName: profileForm.firstName,
      lastName: profileForm.lastName,
      bio: profileForm.bio,
    });
    closeFieldEdits();
    setMessage(t("profile.profileUpdated"));
  } catch (cause) {
    setMessage(apiErrorMessage(cause), "error");
  } finally {
    isSavingProfile.value = false;
  }
};

const openAvatarPicker = () => {
  fileInput.value?.click();
};

const onAvatarChange = (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] || null;
  input.value = "";
  if (!file) return;

  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
    setMessage(t("profile.avatarTypeError"), "error");
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    setMessage(t("profile.avatarSizeError"), "error");
    return;
  }

  cropFile.value = file;
};

const uploadCroppedAvatar = async (file: File, previewUrl: string) => {
  revokeAvatarPreview();
  avatarPreviewUrl.value = previewUrl;
  cropFile.value = null;
  isUploadingAvatar.value = true;
  try {
    await authStore.uploadAvatar(file);
    setMessage(t("profile.avatarUploaded"));
  } catch (cause) {
    setMessage(apiErrorMessage(cause), "error");
  } finally {
    isUploadingAvatar.value = false;
  }
};

const revokeAvatarPreview = () => {
  if (avatarPreviewUrl.value) URL.revokeObjectURL(avatarPreviewUrl.value);
  avatarPreviewUrl.value = null;
};

</script>

<template>
  <main class="w-full max-w-[940px] mx-auto px-4 py-10 sm:py-16 grid gap-6">
    <nav v-if="backUrl" class="w-full max-w-[800px] mx-auto flex items-center min-h-[38px]" aria-label="External navigation">
      <PButton :as="'a'" :href="backUrl" variant="text" icon="pi pi-arrow-left" :label="t('common.back')" severity="secondary" class="-ml-2" />
    </nav>

    <header class="w-full max-w-[800px] mx-auto flex items-center justify-between gap-4 bg-[var(--surface)] rounded-2xl p-5 shadow-sm border-0">
      <div class="flex items-center gap-4 min-w-0">
        <button
          class="relative w-18 h-18 rounded-full overflow-hidden shrink-0 group focus:outline-none focus:ring-3 focus:ring-[var(--focus-ring)] focus:ring-offset-2 border-0"
          type="button"
          :disabled="isUploadingAvatar"
          :aria-label="t('profile.changePhoto')"
          @click="openAvatarPicker"
        >
          <span class="w-full h-full bg-[var(--surface-muted)] flex items-center justify-center text-2xl font-bold text-[var(--text)] border-0">
            <img v-if="avatarPreview" :src="avatarPreview" alt="" class="w-full h-full object-cover border-0" />
            <span v-else>{{ displayInitials }}</span>
          </span>
          <span class="absolute inset-0 bg-black/60 flex items-center justify-center text-white text-xl opacity-0 group-hover:opacity-100 transition-opacity border-0" aria-hidden="true">
            <i :class="isUploadingAvatar ? 'pi pi-spinner pi-spin' : 'pi pi-camera'"></i>
          </span>
        </button>
        <input
          ref="fileInput"
          class="hidden"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          @change="onAvatarChange"
        />
        <div class="min-w-0">
          <h1 class="text-2xl font-bold m-0 text-[var(--text)] truncate">{{ displayName }}</h1>
          <p class="m-0 mt-1 text-sm text-[var(--muted)] truncate">{{ authStore.currentUser?.email }}</p>
        </div>
      </div>
      <PButton
        icon="pi pi-users"
        variant="text"
        severity="secondary"
        class="w-10 h-10 border-0"
        :aria-label="t('profile.switchAccount')"
        :title="t('profile.switchAccount')"
        @click="isAccountModalOpen = true"
      />
    </header>

    <div class="grid grid-cols-1 lg:grid-cols-[52px_minmax(0,720px)] justify-center items-start gap-7">
      <nav class="lg:sticky lg:top-6 flex lg:flex-col items-center justify-center gap-2" aria-label="Profile sections">
        <PButton
          v-for="tab in (['profile', 'sessions', 'system'] as ProfileTab[])"
          :key="tab"
          :icon="tab === 'profile' ? 'pi pi-user' : tab === 'sessions' ? 'pi pi-desktop' : 'pi pi-cog'"
          :variant="activeTab === tab ? 'primary' : 'text'"
          :severity="activeTab === tab ? undefined : 'secondary'"
          class="w-11 h-11 border-0"
          :aria-label="t(`profile.${tab}`)"
          :title="t(`profile.${tab}`)"
          @click="activeTab = tab"
        />
      </nav>

      <div class="min-w-0">
        <section v-if="activeTab === 'profile'" class="grid gap-4">
          <div class="flex items-center justify-between gap-3 min-h-[40px]">
            <h2 class="text-base font-bold m-0 text-[var(--text)]">{{ t("profile.profile") }}</h2>
          </div>

          <form class="grid gap-2" @submit.prevent>
            <div class="grid gap-1.5" aria-label="Profile details">
              <article
                v-for="field in editableProfileFields"
                :key="field.key"
                class="grid grid-cols-[1fr_auto] sm:grid-cols-[132px_1fr_auto] items-center gap-3 sm:gap-4 bg-[var(--surface)] p-3 sm:p-4 rounded-xl transition-colors border-0"
                :class="{ 'bg-[var(--surface-active)]': editingFields[field.key] }"
              >
                <label class="text-[13px] font-bold text-[var(--muted)]" :for="`profile-${field.key}`">{{ field.label }}</label>
                
                <div class="col-span-2 sm:col-span-1 min-w-0">
                  <PInputText
                    v-if="editingFields[field.key] && field.type === 'text'"
                    :id="`profile-${field.key}`"
                    v-model="profileForm[field.key]"
                    class="w-full"
                    :autocomplete="field.autocomplete"
                    autofocus
                  />
                  <textarea
                    v-else-if="editingFields[field.key]"
                    :id="`profile-${field.key}`"
                    v-model="profileForm[field.key]"
                    class="p-textarea w-full"
                    maxlength="500"
                    rows="4"
                    autofocus
                  ></textarea>
                  <p v-else class="m-0 text-[15px] font-semibold leading-relaxed break-words whitespace-pre-wrap px-3" :class="{ 'text-[var(--subtle)]': !profileForm[field.key] }">
                    {{ profileForm[field.key] || t("profile.notSpecified") }}
                  </p>

                  <div
                    v-if="field.key === 'username' && editingFields.username && usernameAvailability !== 'idle'"
                    class="flex items-center gap-1.5 mt-2 text-xs font-semibold"
                    :class="{
                      'text-[var(--success)]': usernameAvailability === 'available',
                      'text-[var(--danger)]': usernameAvailability === 'taken' || usernameAvailability === 'invalid',
                    }"
                    aria-live="polite"
                  >
                    <i v-if="usernameAvailability === 'checking'" class="pi pi-spinner pi-spin"></i>
                    <i v-else-if="usernameAvailability === 'available'" class="pi pi-check"></i>
                    <i v-else class="pi pi-times"></i>
                    {{
                      usernameAvailability === "checking"
                        ? t("profile.usernameChecking")
                        : usernameAvailability === "available"
                          ? t("auth.usernameAvailable")
                          : usernameAvailability === "taken"
                            ? t("auth.usernameTaken")
                            : t("errors.VALIDATION_USERNAME_TOO_SHORT")
                    }}
                  </div>
                </div>

                <PButton
                  class="self-center sm:self-start"
                  :disabled="isSavingProfile || (field.key === 'username' && editingFields.username && !canSaveUsername)"
                  icon="pi pi-pencil"
                  variant="text"
                  severity="secondary"
                  rounded
                  @click="editingFields[field.key] ? confirmFieldEdit(field.key) : openFieldEdit(field.key)"
                >
                  <template #icon>
                     <i :class="isSavingProfile && editingFields[field.key] ? 'pi pi-spinner pi-spin' : editingFields[field.key] ? 'pi pi-check' : 'pi pi-pencil'"></i>
                  </template>
                </PButton>
              </article>
            </div>
          </form>
        </section>

        <SessionsTab v-else-if="activeTab === 'sessions'" @message="setMessage" />
        <SystemTab v-else @message="setMessage" />
      </div>
    </div>

    <AccountSwitchModal :visible="isAccountModalOpen" @close="isAccountModalOpen = false" />
    <AvatarCropper
      v-if="cropFile"
      :file="cropFile"
      @cancel="cropFile = null"
      @apply="uploadCroppedAvatar"
    />
    <PToast />
  </main>
</template>
