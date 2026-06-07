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
import LocaleSwitcher from "@/shared/ui/LocaleSwitcher.vue";

type ProfileTab = "profile" | "sessions" | "system";
type MessageTone = "success" | "error" | "warning";
type EditableProfileField = "username" | "firstName" | "lastName" | "bio";
type UsernameAvailability = "idle" | "checking" | "available" | "taken" | "invalid";

const authStore = useAuthStore();
const route = useRoute();
const { t } = useI18n();
const activeTab = ref<ProfileTab>("profile");
const profileMessage = ref("");
const messageTone = ref<MessageTone>("success");
const isSavingProfile = ref(false);
const isUploadingAvatar = ref(false);
const isAccountModalOpen = ref(false);
const cropFile = ref<File | null>(null);
const avatarPreviewUrl = ref<string | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const usernameAvailability = ref<UsernameAvailability>("idle");
let usernameCheckTimeout: ReturnType<typeof setTimeout> | null = null;
let usernameCheckSequence = 0;
let messageTimeout: ReturnType<typeof setTimeout> | null = null;

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
  if (messageTimeout) clearTimeout(messageTimeout);
  revokeAvatarPreview();
});

function setMessage(message: string, tone: MessageTone = "success") {
  if (messageTimeout) clearTimeout(messageTimeout);
  profileMessage.value = message;
  messageTone.value = tone;
  messageTimeout = setTimeout(clearMessage, 5000);
}

function clearMessage() {
  if (messageTimeout) clearTimeout(messageTimeout);
  messageTimeout = null;
  profileMessage.value = "";
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
  profileMessage.value = "";
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
  profileMessage.value = "";
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
  <main class="profile-page">
    <nav v-if="backUrl" class="profile-backbar" aria-label="External navigation">
      <a :href="backUrl" class="btn btn-ghost back-btn">
        <i class="pi pi-arrow-left"></i>
        {{ t("common.back") }}
      </a>
    </nav>

    <header class="profile-hero">
      <div class="identity-block">
        <button
          class="avatar-edit"
          type="button"
          :disabled="isUploadingAvatar"
          :aria-label="t('profile.changePhoto')"
          @click="openAvatarPicker"
        >
          <span class="avatar lg">
            <img v-if="avatarPreview" :src="avatarPreview" alt="" />
            <span v-else>{{ displayInitials }}</span>
          </span>
          <span class="avatar-edit-overlay" aria-hidden="true">
            <i :class="isUploadingAvatar ? 'pi pi-spinner pi-spin' : 'pi pi-camera'"></i>
          </span>
        </button>
        <input
          ref="fileInput"
          class="visually-hidden"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          @change="onAvatarChange"
        />
        <div>
          <h1>{{ displayName }}</h1>
          <p>{{ authStore.currentUser?.email }}</p>
        </div>
      </div>
      <div class="profile-hero-actions">
        <LocaleSwitcher class="profile-locale" />
        <button
          class="icon-button quiet account-switch-button"
          type="button"
          :aria-label="t('profile.switchAccount')"
          :title="t('profile.switchAccount')"
          @click="isAccountModalOpen = true"
        >
          <i class="pi pi-users"></i>
        </button>
      </div>
    </header>

    <Transition name="profile-toast">
      <aside
        v-if="profileMessage"
        class="profile-toast"
        :class="messageTone"
        :role="messageTone === 'error' ? 'alert' : 'status'"
        aria-live="polite"
      >
        <span class="profile-toast-icon" aria-hidden="true">
          <i
            :class="
              messageTone === 'success'
                ? 'pi pi-check'
                : messageTone === 'error'
                  ? 'pi pi-times'
                  : 'pi pi-exclamation-triangle'
            "
          ></i>
        </span>
        <span class="profile-toast-message">{{ profileMessage }}</span>
        <button
          class="profile-toast-close"
          type="button"
          :aria-label="t('common.close')"
          @click="clearMessage"
        >
          <i class="pi pi-times"></i>
        </button>
      </aside>
    </Transition>

    <div class="profile-shell">
      <nav class="profile-icon-tabs" aria-label="Profile sections">
        <button
          type="button"
          :class="{ active: activeTab === 'profile' }"
          :aria-label="t('profile.profile')"
          :title="t('profile.profile')"
          @click="activeTab = 'profile'"
        >
          <i class="pi pi-user"></i>
          <span class="visually-hidden">{{ t("profile.profile") }}</span>
        </button>
        <button
          type="button"
          :class="{ active: activeTab === 'sessions' }"
          :aria-label="t('profile.sessions')"
          :title="t('profile.sessions')"
          @click="activeTab = 'sessions'"
        >
          <i class="pi pi-desktop"></i>
          <span class="visually-hidden">{{ t("profile.sessions") }}</span>
        </button>
        <button
          type="button"
          :class="{ active: activeTab === 'system' }"
          :aria-label="t('system.title')"
          :title="t('system.title')"
          @click="activeTab = 'system'"
        >
          <i class="pi pi-cog"></i>
          <span class="visually-hidden">{{ t("system.title") }}</span>
        </button>
      </nav>

      <div class="profile-stage">
        <section v-if="activeTab === 'profile'" class="tab-panel">
          <div class="section-toolbar">
            <h2>{{ t("profile.profile") }}</h2>
          </div>

          <form class="profile-form inline-profile-form" @submit.prevent>
            <div class="profile-detail-list" aria-label="Profile details">
              <article
                v-for="field in editableProfileFields"
                :key="field.key"
                class="profile-edit-row"
                :class="{ editing: editingFields[field.key] }"
              >
                <label class="profile-edit-label" :for="`profile-${field.key}`">{{ field.label }}</label>
                <div class="profile-edit-value">
                  <input
                    v-if="editingFields[field.key] && field.type === 'text'"
                    :id="`profile-${field.key}`"
                    v-model="profileForm[field.key]"
                    class="input inline-input"
                    :autocomplete="field.autocomplete"
                    autofocus
                  />
                  <textarea
                    v-else-if="editingFields[field.key]"
                    :id="`profile-${field.key}`"
                    v-model="profileForm[field.key]"
                    class="textarea inline-textarea"
                    maxlength="500"
                    rows="4"
                    autofocus
                  ></textarea>
                  <p v-else :class="{ muted: !profileForm[field.key] }">
                    {{ profileForm[field.key] || t("profile.notSpecified") }}
                  </p>
                  <span
                    v-if="field.key === 'username' && editingFields.username && usernameAvailability !== 'idle'"
                    class="validation-message username-availability"
                    :class="{
                      'text-success': usernameAvailability === 'available',
                      'text-danger': usernameAvailability === 'taken' || usernameAvailability === 'invalid',
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
                  </span>
                </div>
                <button
                  class="icon-button quiet edit-field-button"
                  :disabled="isSavingProfile || (field.key === 'username' && editingFields.username && !canSaveUsername)"
                  type="button"
                  :aria-label="editingFields[field.key] ? t('profile.finishEditing') : t('profile.editField')"
                  :title="editingFields[field.key] ? t('profile.finishEditing') : t('profile.editField')"
                  @click="editingFields[field.key] ? confirmFieldEdit(field.key) : openFieldEdit(field.key)"
                >
                  <i
                    :class="
                      isSavingProfile && editingFields[field.key]
                        ? 'pi pi-spinner pi-spin'
                        : editingFields[field.key]
                          ? 'pi pi-check'
                          : 'pi pi-pencil'
                    "
                  ></i>
                </button>
              </article>
            </div>
          </form>
        </section>

        <SessionsTab v-else-if="activeTab === 'sessions'" @message="setMessage" />
        <SystemTab v-else @message="setMessage" />
      </div>
    </div>

    <AccountSwitchModal v-if="isAccountModalOpen" @close="isAccountModalOpen = false" />
    <AvatarCropper
      v-if="cropFile"
      :file="cropFile"
      @cancel="cropFile = null"
      @apply="uploadCroppedAvatar"
    />
  </main>
</template>
