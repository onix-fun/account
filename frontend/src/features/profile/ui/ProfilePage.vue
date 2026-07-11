<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { useToast } from "primevue/usetoast";
import { apiErrorMessage } from "@/api/client";
import { AuthService } from "@/api/services/AuthService";
import type { Organization } from "@/domain";
import { trustedRedirectUrl } from "@/infra/navigation/trustedRedirect";
import { useAuthStore, useProfileSocialStore } from "@/infra/store";
import { isUsername } from "@/shared/lib/validation";
import AvatarCropper from "@/features/avatar/ui/AvatarCropper.vue";
import AccountHeader from "@/features/profile/ui/AccountHeader.vue";
import AccountSwitchModal from "@/features/profile/ui/AccountSwitchModal.vue";
import BlockedUsersTab from "@/features/profile/ui/BlockedUsersTab.vue";
import CloseFriendsTab from "@/features/profile/ui/CloseFriendsTab.vue";
import NotificationsPage from "@/features/profile/ui/NotificationsPage.vue";
import OrganizationAdminLayout, { type OrganizationAdminTab } from "@/features/profile/ui/OrganizationAdminLayout.vue";
import OrganizationMembers from "@/features/profile/ui/OrganizationMembers.vue";
import OrganizationProfileSettings from "@/features/profile/ui/OrganizationProfileSettings.vue";
import OrganizationSelectorScreen from "@/features/profile/ui/OrganizationSelectorScreen.vue";
import ProfileListPage from "@/features/profile/ui/ProfileListPage.vue";
import ProfileMobileMenu from "@/features/profile/ui/ProfileMobileMenu.vue";
import ProfileNav, { type ProfileTab } from "@/features/profile/ui/ProfileNav.vue";
import ProfileSearchOverlay from "@/features/profile/ui/ProfileSearchOverlay.vue";
import ProfileTopCard from "@/features/profile/ui/ProfileTopCard.vue";
import SessionsTab from "@/features/sessions/ui/SessionsTab.vue";
import SocialSettingsTab from "@/features/profile/ui/SocialSettingsTab.vue";
import SystemTab from "@/features/profile/ui/SystemTab.vue";

type ProfileView = ProfileTab | "followers" | "following" | "notifications" | "search";
type EditableProfileField = "username" | "firstName" | "lastName" | "bio" | "birthDate";
type UsernameAvailability = "idle" | "checking" | "available" | "taken" | "invalid";
type AccountMode = "user" | "organization";

const authStore = useAuthStore();
const socialStore = useProfileSocialStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const toast = useToast();

const activeTab = ref<ProfileTab>("profile");
const activeOrganizationTab = ref<OrganizationAdminTab>("profile");
const accountMode = ref<AccountMode>("user");
const isSavingProfile = ref(false);
const isUploadingAvatar = ref(false);
const isAccountModalOpen = ref(false);
const isOrganizationSelectorOpen = ref(false);
const selectedOrganizationId = ref<string | null>(null);
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
  birthDate: "",
});
const socialLinks = ref<Array<{ label: string; url: string }>>([]);

const editingFields = reactive<Record<EditableProfileField, boolean>>({
  username: false,
  firstName: false,
  lastName: false,
  bio: false,
  birthDate: false,
});

const backUrl = computed(() => trustedRedirectUrl(route.query.redirect));
const avatarPreview = computed(() => avatarPreviewUrl.value || authStore.currentUser?.avatarUrl || "");
const selectedOrganization = computed<Organization | null>(() => {
  const activeOrgId = authStore.activeOwner?.ownerType === "ORGANIZATION" ? authStore.activeOwner.ownerId : null;
  const id = selectedOrganizationId.value || activeOrgId;
  return authStore.organizations.find((organization) => organization.id === id) || null;
});
const selectedOrganizationNotificationOwner = computed(() => selectedOrganization.value
  ? { ownerType: "ORGANIZATION" as const, ownerId: selectedOrganization.value.id }
  : null
);
const queryView = computed<ProfileView | null>(() => {
  const value = route.query.view;
  return isProfileView(value) ? value : null;
});
const contentView = computed<ProfileView>(() => queryView.value ?? activeTab.value);
const hideAccountHeader = computed(() => contentView.value === "notifications" || contentView.value === "search");
const showExternalBack = computed(() => Boolean(backUrl.value) && !queryView.value);
const showOrganizationSelector = computed(() => !queryView.value && accountMode.value === "organization" && (isOrganizationSelectorOpen.value || !selectedOrganization.value));
const isProfileDirty = computed(() => {
  const user = authStore.currentUser;
  return (
    profileForm.firstName !== (user?.firstName || "") ||
    profileForm.lastName !== (user?.lastName || "") ||
    profileForm.bio !== (user?.bio || "") ||
    profileForm.birthDate !== (user?.birthDate || "") ||
    JSON.stringify(normalizedSocialLinks(socialLinks.value)) !== JSON.stringify(user?.socialLinks || []) ||
    profileForm.username.trim() !== (user?.username || "")
  );
});
const canSaveUsername = computed(() => {
  const username = profileForm.username.trim();
  if (username.toLowerCase() === authStore.currentUser?.username.toLowerCase()) return true;
  return usernameAvailability.value === "available";
});
const editableProfileFields = computed<
  Array<{ key: EditableProfileField; label: string; type: "text" | "date" | "textarea"; autocomplete?: string }>
>(() => [
  { key: "username", label: t("auth.username"), type: "text", autocomplete: "username" },
  { key: "firstName", label: t("auth.firstName"), type: "text", autocomplete: "given-name" },
  { key: "lastName", label: t("auth.lastName"), type: "text", autocomplete: "family-name" },
  { key: "birthDate", label: t("profile.birthDate"), type: "date" },
  { key: "bio", label: t("profile.bio"), type: "textarea" },
]);

watch(() => authStore.currentUser, syncProfileForm, { immediate: true });
watch(
  () => authStore.activeOwner,
  (owner) => {
    if (owner?.ownerType === "ORGANIZATION") {
      accountMode.value = "organization";
      selectedOrganizationId.value = owner.ownerId;
    }
  },
  { immediate: true },
);
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
  profileForm.birthDate = authStore.currentUser?.birthDate || "";
  socialLinks.value = (authStore.currentUser?.socialLinks || []).map((link) => ({ ...link }));
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
  router.replace({ query: nextQuery });
}

function isProfileView(value: unknown): value is ProfileView {
  return (
    value === "profile" ||
    value === "close" ||
    value === "blocked" ||
    value === "settings" ||
    value === "sessions" ||
    value === "system" ||
    value === "followers" ||
    value === "following" ||
    value === "notifications" ||
    value === "search"
  );
}

async function showUserProfile() {
  if (authStore.currentUser && authStore.activeOwner?.ownerType === "ORGANIZATION") {
    await authStore.switchOwner("USER", authStore.currentUser.id);
    await Promise.allSettled([socialStore.loadSettings(), socialStore.loadBlockedUsers(), socialStore.refreshSummary()]);
  }
  accountMode.value = "user";
  isOrganizationSelectorOpen.value = false;
}

function openOrganizationMode() {
  accountMode.value = "organization";
  isOrganizationSelectorOpen.value = true;
}

async function selectOrganization(organization: Organization) {
  selectedOrganizationId.value = organization.id;
  await authStore.switchOwner("ORGANIZATION", organization.id);
  await Promise.allSettled([socialStore.loadSettings(), socialStore.loadBlockedUsers(), socialStore.refreshSummary()]);
  accountMode.value = "organization";
  activeOrganizationTab.value = "profile";
  isOrganizationSelectorOpen.value = false;
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
  editingFields.birthDate = false;
}

async function saveProfile() {
  isSavingProfile.value = true;
  try {
    await authStore.updateProfile({
      username: profileForm.username.trim(),
      firstName: profileForm.firstName,
      lastName: profileForm.lastName,
      bio: profileForm.bio,
      birthDate: profileForm.birthDate || null,
      socialLinks: normalizedSocialLinks(socialLinks.value),
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

function normalizedSocialLinks(links: Array<{ label: string; url: string }>) {
  return links
    .map((link) => ({ label: link.label.trim(), url: link.url.trim() }))
    .filter((link) => link.label || link.url);
}

function addSocialLink() {
  if (socialLinks.value.length >= 10) return;
  socialLinks.value = [...socialLinks.value, { label: "", url: "" }];
}

function removeSocialLink(index: number) {
  socialLinks.value = socialLinks.value.filter((_, current) => current !== index);
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
    <AccountHeader
      v-if="!hideAccountHeader"
      :mode="accountMode"
      :user="authStore.currentUser"
      :summary="socialStore.summary"
      :back-url="showExternalBack ? backUrl : null"
      @user-mode="showUserProfile"
      @organization-mode="openOrganizationMode"
      @account="isAccountModalOpen = true"
      @notifications="openView('notifications')"
      @search="openView('search')"
    />

    <ProfileTopCard
      v-if="!queryView && accountMode === 'user'"
      :user="authStore.currentUser"
      :summary="socialStore.summary"
      :avatar-preview="avatarPreview"
      :is-uploading-avatar="isUploadingAvatar"
      @avatar="openAvatarPicker"
      @open-view="openView"
    />

    <input
      ref="fileInput"
      class="hidden"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      @change="onAvatarChange"
    />

    <OrganizationSelectorScreen
      v-if="showOrganizationSelector"
      :selected-organization-id="selectedOrganization?.id || null"
      @select="selectOrganization"
      @message="setMessage"
    />

    <div
      v-else
      class="grid grid-cols-1 justify-center items-start gap-7"
      :class="queryView || accountMode === 'organization' ? 'max-w-[720px] w-full mx-auto' : 'lg:grid-cols-[52px_minmax(0,720px)]'"
    >
      <div v-if="!queryView && accountMode === 'user'" class="hidden lg:block">
        <ProfileNav v-model:active-tab="activeTab" />
      </div>
      <ProfileMobileMenu v-if="!queryView && accountMode === 'user'" @open-view="openView" />

      <div class="min-w-0" :class="{ 'hidden lg:block': !queryView && accountMode === 'user' }">
        <section v-if="!queryView && accountMode === 'organization'" class="grid gap-4">
          <OrganizationAdminLayout
            v-if="selectedOrganization"
            v-model:active-tab="activeOrganizationTab"
            :organization="selectedOrganization"
            @choose="isOrganizationSelectorOpen = true"
          >
            <OrganizationProfileSettings
              v-if="activeOrganizationTab === 'profile'"
              :organization="selectedOrganization"
              @message="setMessage"
            />
            <section v-else-if="activeOrganizationTab === 'social'" class="grid gap-4">
              <article class="grid gap-3 bg-[var(--surface)] rounded-[18px] p-4">
                <div class="grid gap-2 sm:grid-cols-2">
                  <PButton :label="t('social.followers')" icon="pi pi-users" severity="secondary" variant="outlined" @click="openView('followers')" />
                  <PButton :label="t('social.following')" icon="pi pi-user-plus" severity="secondary" variant="outlined" @click="openView('following')" />
                </div>
              </article>
              <SocialSettingsTab :notification-owner="selectedOrganizationNotificationOwner" @message="setMessage" />
            </section>
            <BlockedUsersTab v-else-if="activeOrganizationTab === 'blocked'" @message="setMessage" />
            <OrganizationMembers v-else-if="activeOrganizationTab === 'members'" :organization="selectedOrganization" @message="setMessage" />
          </OrganizationAdminLayout>
        </section>

        <ProfileSearchOverlay
          v-else-if="contentView === 'search'"
          visible
          page
          @close="closeView"
          @message="setMessage"
        />

        <NotificationsPage
          v-else-if="contentView === 'notifications'"
          @back="closeView"
          @message="setMessage"
        />

        <ProfileListPage
          v-else-if="contentView === 'followers'"
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
          v-else-if="contentView === 'following'"
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

        <section v-else-if="contentView === 'profile'" class="grid gap-4">
          <div class="flex items-center justify-between gap-3 min-h-[40px]">
            <PButton v-if="queryView" icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="-ml-2" @click="closeView" />
            <h2 class="text-base font-bold m-0 text-[var(--text)]">{{ t("profile.profile") }}</h2>
          </div>

          <form class="grid gap-2" @submit.prevent>
            <div class="grid gap-1.5" aria-label="Profile details">
              <article
                v-for="field in editableProfileFields"
                :key="field.key"
                class="grid grid-cols-[minmax(0,1fr)_auto] sm:grid-cols-[132px_minmax(0,1fr)_auto] items-center gap-3 sm:gap-4 bg-[var(--surface)] p-3 sm:p-4 rounded-xl transition-colors border-0"
                :class="{ 'bg-[var(--surface-active)]': editingFields[field.key] }"
              >
                <label class="col-span-2 sm:col-span-1 sm:col-start-1 sm:row-start-1 text-[13px] font-bold text-[var(--muted)]" :for="`profile-${field.key}`">{{ field.label }}</label>

                <PButton
                  class="col-start-2 row-start-2 self-center justify-self-end sm:col-start-3 sm:row-start-1 sm:self-start shrink-0"
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

                <div class="col-start-1 row-start-2 min-w-0 sm:col-span-1 sm:col-start-2 sm:row-start-1">
                  <PInputText
                    v-if="editingFields[field.key] && (field.type === 'text' || field.type === 'date')"
                    :id="`profile-${field.key}`"
                    v-model="profileForm[field.key]"
                    :type="field.type === 'date' ? 'date' : 'text'"
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
              </article>
            </div>

            <section class="grid gap-3 bg-[var(--surface)] p-3 sm:p-4 rounded-xl">
              <div class="flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <h3 class="m-0 text-[15px] font-bold text-[var(--text)]">{{ t("profile.socialLinks") }}</h3>
                  <p class="m-0 mt-1 text-xs text-[var(--muted)]">{{ t("profile.socialLinksHint") }}</p>
                </div>
                <div class="flex items-center gap-1">
                  <PButton icon="pi pi-plus" rounded variant="text" severity="secondary" :disabled="socialLinks.length >= 10 || isSavingProfile" @click="addSocialLink" />
                  <PButton icon="pi pi-check" rounded variant="text" severity="secondary" :loading="isSavingProfile" :disabled="!isProfileDirty" @click="saveProfile" />
                </div>
              </div>

              <div class="grid gap-2">
                <div v-if="!socialLinks.length" class="text-sm text-[var(--subtle)] px-1">{{ t("profile.noSocialLinks") }}</div>
                <article v-for="(link, index) in socialLinks" :key="index" class="grid grid-cols-1 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_auto] gap-2 items-center">
                  <PInputText v-model="link.label" :placeholder="t('profile.socialLinkLabel')" maxlength="60" class="w-full" />
                  <PInputText v-model="link.url" :placeholder="t('profile.socialLinkUrl')" class="w-full" />
                  <PButton icon="pi pi-trash" rounded variant="text" severity="secondary" :disabled="isSavingProfile" @click="removeSocialLink(index)" />
                </article>
              </div>
            </section>
          </form>
        </section>

        <section v-else-if="contentView === 'close'" class="grid gap-4">
          <PButton v-if="queryView" icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="-ml-2 justify-self-start" @click="closeView" />
          <CloseFriendsTab @message="setMessage" />
        </section>

        <section v-else-if="contentView === 'blocked'" class="grid gap-4">
          <PButton v-if="queryView" icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="-ml-2 justify-self-start" @click="closeView" />
          <BlockedUsersTab @message="setMessage" />
        </section>

        <section v-else-if="contentView === 'settings'" class="grid gap-4">
          <PButton v-if="queryView" icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="-ml-2 justify-self-start" @click="closeView" />
          <SocialSettingsTab @message="setMessage" />
        </section>

        <section v-else-if="contentView === 'sessions'" class="grid gap-4">
          <PButton v-if="queryView" icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="-ml-2 justify-self-start" @click="closeView" />
          <SessionsTab @message="setMessage" />
        </section>

        <section v-else class="grid gap-4">
          <PButton v-if="queryView" icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="-ml-2 justify-self-start" @click="closeView" />
          <SystemTab @message="setMessage" />
        </section>
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
