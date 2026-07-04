<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { useToast } from "primevue/usetoast";
import { apiErrorMessage } from "@/api/client";
import { AuthService } from "@/api/services/AuthService";
import { trustedRedirectUrl } from "@/infra/navigation/trustedRedirect";
import { useAuthStore, useProfileSocialStore } from "@/infra/store";
import { isUsername } from "@/shared/lib/validation";
import AvatarCropper from "@/features/avatar/ui/AvatarCropper.vue";
import AccountSwitchModal from "@/features/profile/ui/AccountSwitchModal.vue";
import BlockedUsersTab from "@/features/profile/ui/BlockedUsersTab.vue";
import CloseFriendsTab from "@/features/profile/ui/CloseFriendsTab.vue";
import NotificationsPage from "@/features/profile/ui/NotificationsPage.vue";
import ProfileListPage from "@/features/profile/ui/ProfileListPage.vue";
import ProfileNav, { type ProfileTab } from "@/features/profile/ui/ProfileNav.vue";
import ProfileSearchOverlay from "@/features/profile/ui/ProfileSearchOverlay.vue";
import ProfileTopCard from "@/features/profile/ui/ProfileTopCard.vue";
import RequestsTab from "@/features/profile/ui/RequestsTab.vue";
import SessionsTab from "@/features/sessions/ui/SessionsTab.vue";
import SocialSettingsTab from "@/features/profile/ui/SocialSettingsTab.vue";
import SystemTab from "@/features/profile/ui/SystemTab.vue";

type ProfileView = "followers" | "following" | "notifications";
type EditableProfileField = "username" | "firstName" | "lastName" | "bio";
type UsernameAvailability = "idle" | "checking" | "available" | "taken" | "invalid";

const authStore = useAuthStore();
const socialStore = useProfileSocialStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const toast = useToast();

const activeTab = ref<ProfileTab>("profile");
const isSavingProfile = ref(false);
const isUploadingAvatar = ref(false);
const isAccountModalOpen = ref(false);
const isSearchOpen = ref(false);
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

const backUrl = computed(() => trustedRedirectUrl(route.query.redirect));
const avatarPreview = computed(() => avatarPreviewUrl.value || authStore.currentUser?.avatarUrl || "");
const queryView = computed<ProfileView | null>(() => {
  const value = route.query.view;
  return value === "followers" || value === "following" || value === "notifications" ? value : null;
});
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
watch(queryView, (view) => {
  if (view === "followers") socialStore.loadFollowers().catch((cause) => setMessage(apiErrorMessage(cause), "error"));
  if (view === "following") socialStore.loadFollowing().catch((cause) => setMessage(apiErrorMessage(cause), "error"));
  if (view === "notifications") socialStore.loadNotifications().catch((cause) => setMessage(apiErrorMessage(cause), "error"));
}, { immediate: true });

onMounted(async () => {
  await Promise.allSettled([
    authStore.fetchSessions(),
    socialStore.refreshSummary(),
    socialStore.loadSettings(),
  ]).then((results) => {
    results.forEach((result) => {
      if (result.status === "rejected") setMessage(apiErrorMessage(result.reason), "error");
    });
  });
});

onBeforeUnmount(() => {
  if (usernameCheckTimeout) clearTimeout(usernameCheckTimeout);
  revokeAvatarPreview();
});

function syncProfileForm() {
  profileForm.username = authStore.currentUser?.username || "";
  profileForm.firstName = authStore.currentUser?.firstName || "";
  profileForm.lastName = authStore.currentUser?.lastName || "";
  profileForm.bio = authStore.currentUser?.bio || "";
}

function setMessage(message: string, tone: "success" | "error" | "warn" | "warning" | "info" = "success") {
  const warning = tone === "warn" || tone === "warning";
  toast.add({
    severity: tone === "error" ? "error" : warning ? "warn" : tone === "info" ? "info" : "success",
    summary: tone === "error" ? t("common.error") : t("common.success"),
    detail: message,
    life: 5000,
  });
}

function openView(view: ProfileView) {
  router.push({ query: { ...route.query, view } });
}

function closeView() {
  const nextQuery = { ...route.query };
  delete nextQuery.view;
  router.push({ query: nextQuery });
}

function openFieldEdit(field: EditableProfileField) {
  editingFields[field] = true;
}

async function confirmFieldEdit(field: EditableProfileField) {
  if (field === "username" && !canSaveUsername.value) return;
  if (!isProfileDirty.value) {
    editingFields[field] = false;
    return;
  }
  await saveProfile();
}

function closeFieldEdits() {
  editingFields.username = false;
  editingFields.firstName = false;
  editingFields.lastName = false;
  editingFields.bio = false;
}

async function saveProfile() {
  isSavingProfile.value = true;
  try {
    await authStore.updateProfile({
      username: profileForm.username.trim(),
      firstName: profileForm.firstName,
      lastName: profileForm.lastName,
      bio: profileForm.bio,
    });
    await socialStore.refreshSummary().catch(() => undefined);
    closeFieldEdits();
    setMessage(t("profile.profileUpdated"));
  } catch (cause) {
    setMessage(apiErrorMessage(cause), "error");
  } finally {
    isSavingProfile.value = false;
  }
}

function openAvatarPicker() {
  fileInput.value?.click();
}

function onAvatarChange(event: Event) {
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
}

async function uploadCroppedAvatar(file: File, previewUrl: string) {
  revokeAvatarPreview();
  avatarPreviewUrl.value = previewUrl;
  cropFile.value = null;
  isUploadingAvatar.value = true;
  try {
    await authStore.uploadAvatar(file);
    await socialStore.refreshSummary().catch(() => undefined);
    setMessage(t("profile.avatarUploaded"));
  } catch (cause) {
    setMessage(apiErrorMessage(cause), "error");
  } finally {
    isUploadingAvatar.value = false;
  }
}

function revokeAvatarPreview() {
  if (avatarPreviewUrl.value) URL.revokeObjectURL(avatarPreviewUrl.value);
  avatarPreviewUrl.value = null;
}
</script>

<template>
  <main class="w-full max-w-[940px] mx-auto px-4 py-10 sm:py-16 grid gap-6">
    <nav v-if="backUrl" class="w-full max-w-[800px] mx-auto flex items-center min-h-[38px]" aria-label="External navigation">
      <PButton :as="'a'" :href="backUrl" variant="text" icon="pi pi-arrow-left" :label="t('common.back')" severity="secondary" class="-ml-2" />
    </nav>

    <ProfileTopCard
      v-if="!queryView"
      :user="authStore.currentUser"
      :summary="socialStore.summary"
      :avatar-preview="avatarPreview"
      :is-uploading-avatar="isUploadingAvatar"
      @avatar="openAvatarPicker"
      @switch-account="isAccountModalOpen = true"
      @open-view="openView"
    />

    <input
      ref="fileInput"
      class="hidden"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      @change="onAvatarChange"
    />

    <div
      class="grid grid-cols-1 justify-center items-start gap-7"
      :class="queryView ? 'max-w-[720px] w-full mx-auto' : 'lg:grid-cols-[52px_minmax(0,720px)]'"
    >
      <ProfileNav v-if="!queryView" v-model:active-tab="activeTab" @search="isSearchOpen = true" />

      <div class="min-w-0">
        <NotificationsPage
          v-if="queryView === 'notifications'"
          @back="closeView"
          @message="setMessage"
        />

        <ProfileListPage
          v-else-if="queryView === 'followers'"
          :title="t('social.followers')"
          :items="socialStore.followers.items"
          :total-count="socialStore.followers.totalCount"
          :page="socialStore.followers.page"
          :limit="socialStore.followers.limit"
          :is-loading="socialStore.followers.isLoading"
          :empty-text="t('social.noFollowers')"
          show-friend-filter
          @back="closeView"
          @page="(page) => socialStore.loadFollowers(page)"
          @message="setMessage"
        />

        <ProfileListPage
          v-else-if="queryView === 'following'"
          :title="t('social.following')"
          :items="socialStore.following.items"
          :total-count="socialStore.following.totalCount"
          :page="socialStore.following.page"
          :limit="socialStore.following.limit"
          :is-loading="socialStore.following.isLoading"
          :empty-text="t('social.noFollowing')"
          show-friend-filter
          @back="closeView"
          @page="(page) => socialStore.loadFollowing(page)"
          @message="setMessage"
        />

        <section v-else-if="activeTab === 'profile'" class="grid gap-4">
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

        <RequestsTab v-else-if="activeTab === 'requests'" @message="setMessage" />
        <CloseFriendsTab v-else-if="activeTab === 'close'" @message="setMessage" />
        <BlockedUsersTab v-else-if="activeTab === 'blocked'" @message="setMessage" />
        <SocialSettingsTab v-else-if="activeTab === 'settings'" @message="setMessage" />
        <SessionsTab v-else-if="activeTab === 'sessions'" @message="setMessage" />
        <SystemTab v-else @message="setMessage" />
      </div>
    </div>

    <ProfileSearchOverlay
      :visible="isSearchOpen"
      @close="isSearchOpen = false"
      @message="setMessage"
    />
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
