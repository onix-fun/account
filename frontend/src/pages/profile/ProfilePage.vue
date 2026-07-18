<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { useToast } from "primevue/usetoast";
import { apiErrorMessage } from "@/shared/api/client";
import { AuthService } from "@/shared/api/services/AuthService";
import type { Organization } from "@/shared/model/domain";
import { trustedRedirectUrl } from "@/shared/lib/trustedRedirect";
import { useAuthStore, useProfileSocialStore } from "@/shared/model/store";
import { isUsername } from "@/shared/lib/validation";
import AvatarCropper from "@/features/avatar/ui/AvatarCropper.vue";
import { AccountSwitchModal } from "@/features/account-switch";
import { BlockedUsersTab, CloseFriendsTab, ConnectionsTab } from "@/features/connections";
import { NotificationsPage } from "@/features/notifications";
import {
  OrganizationAdminLayout,
  OrganizationMembers,
  OrganizationProfileSettings,
  OrganizationSelectorScreen,
  type OrganizationAdminTab,
} from "@/features/organizations";
import { SocialSettingsTab } from "@/features/privacy-settings";
import { SocialLinksEditor } from "@/features/social-links-edit";
import AccountHeader from "@/features/profile/ui/AccountHeader.vue";
import ProfileMobileMenu from "@/features/profile/ui/ProfileMobileMenu.vue";
import ProfileNav, { type ProfileTab } from "@/features/profile/ui/ProfileNav.vue";
import ProfileSearchOverlay from "@/features/profile/ui/ProfileSearchOverlay.vue";
import SessionsTab from "@/features/sessions/ui/SessionsTab.vue";
import SocialPlatformIcon from "@/features/social-links-edit/ui/SocialPlatformIcon.vue";
import SystemTab from "@/features/profile/ui/SystemTab.vue";
import { userInitials } from "@/shared/model/user";
import { describeSocialLink, hasDuplicateSocialLinks, hasInvalidSocialLinks, normalizeSocialLinks } from "@/features/social-links-edit/model";

type ConnectionsFilter = "followers" | "following" | "friends";
type ProfileQueryView = ProfileTab | "followers" | "following" | "notifications" | "search";
type ProfileContentView = ProfileTab | "notifications" | "search";
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
const isEditingSocialLinks = ref(false);
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
const queryView = computed<ProfileQueryView | null>(() => {
  const value = route.query.view;
  return isProfileView(value) ? value : null;
});
const contentView = computed<ProfileContentView>(() => {
  if (queryView.value === "followers" || queryView.value === "following") return "connections";
  return queryView.value ?? activeTab.value;
});
const connectionsInitialFilter = computed<ConnectionsFilter>(() => {
  if (queryView.value === "following") return "following";
  return "followers";
});
const hideAccountHeader = computed(() => Boolean(queryView.value));
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
const hasSocialLinkErrors = computed(() => hasInvalidSocialLinks(socialLinks.value) || hasDuplicateSocialLinks(socialLinks.value));
const socialLinkViews = computed(() => socialLinks.value.map(describeSocialLink));
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
const displayInitials = computed(() => userInitials(authStore.currentUser));

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
watch(contentView, (view) => {
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
  isEditingSocialLinks.value = false;
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

function openView(view: ProfileQueryView) {
  router.push({ query: { ...route.query, view } });
}

function closeView() {
  const nextQuery = { ...route.query };
  delete nextQuery.view;
  router.replace({ query: nextQuery });
}

function isProfileView(value: unknown): value is ProfileQueryView {
  return (
    value === "profile" ||
    value === "connections" ||
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

async function confirmSocialLinksEdit() {
  if (hasSocialLinkErrors.value) return;
  if (!isProfileDirty.value) {
    isEditingSocialLinks.value = false;
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
  if (hasSocialLinkErrors.value) {
    setMessage(t("profile.socialLinkValidationError"), "error");
    return;
  }
  isSavingProfile.value = true;
  try {
    await authStore.updateProfile({
      username: profileForm.username.trim(),
      firstName: profileForm.firstName,
      lastName: profileForm.lastName,
      bio: profileForm.bio,
      birthDate: profileForm.birthDate || null,
      socialLinks: normalizeSocialLinks(socialLinks.value),
    });
    await socialStore.refreshSummary().catch(() => undefined);
    closeFieldEdits();
    isEditingSocialLinks.value = false;
    setMessage(t("profile.profileUpdated"));
  } catch (cause) {
    setMessage(apiErrorMessage(cause), "error");
  } finally {
    isSavingProfile.value = false;
  }
}

function normalizedSocialLinks(links: Array<{ label: string; url: string }>) {
  return normalizeSocialLinks(links);
}

function addSocialLink() {
  if (socialLinks.value.length >= 10) return;
  isEditingSocialLinks.value = true;
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
  <main class="ui-shell">
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
      <div v-if="!queryView && accountMode === 'user'" class="profile-nav-column hidden lg:block">
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
            <ConnectionsTab
              v-else-if="activeOrganizationTab === 'connections'"
              initial-filter="followers"
              @message="setMessage"
            />
            <section v-else-if="activeOrganizationTab === 'social'" class="grid gap-4">
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

        <ConnectionsTab
          v-else-if="contentView === 'connections'"
          :initial-filter="connectionsInitialFilter"
          :show-back="Boolean(queryView)"
          @back="closeView"
          @message="setMessage"
        />

        <section v-else-if="contentView === 'profile'" class="profile-screen">
          <div class="fullscreen-page-heading">
            <PButton v-if="queryView" icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="-ml-2 justify-self-start" @click="closeView" />
            <UiSectionHeader :title="t('profile.profile')" />
          </div>

          <form class="profile-form" @submit.prevent>
            <UiSurface as="section" class="profile-avatar-section">
              <button
                class="profile-avatar-button"
                type="button"
                :disabled="isUploadingAvatar"
                :aria-label="t('profile.changePhoto')"
                @click="openAvatarPicker"
              >
                <span class="profile-avatar-frame">
                  <img v-if="avatarPreview" :src="avatarPreview" alt="" />
                  <span v-else>{{ displayInitials }}</span>
                </span>
                <span class="profile-avatar-action" aria-hidden="true">
                  <i :class="isUploadingAvatar ? 'pi pi-spinner pi-spin' : 'pi pi-camera'"></i>
                </span>
              </button>
              <div class="min-w-0">
                <h3 class="m-0 text-[15px] font-extrabold text-[var(--text)]">{{ t("profile.changePhoto") }}</h3>
                <p class="m-0 mt-1 text-[12px] font-semibold text-[var(--muted)]">{{ t("profile.avatarHint") }}</p>
              </div>
              <PButton
                icon="pi pi-camera"
                :label="t('profile.changePhoto')"
                severity="secondary"
                variant="outlined"
                :loading="isUploadingAvatar"
                @click="openAvatarPicker"
              />
            </UiSurface>

            <div class="ui-list profile-details-list" aria-label="Profile details">
              <UiFlatRow
                v-for="field in editableProfileFields"
                :key="field.key"
                as="article"
                :active="editingFields[field.key]"
                muted
                class="profile-field-row grid grid-cols-[minmax(0,1fr)_auto] sm:grid-cols-[132px_minmax(0,1fr)_auto] items-center"
                :class="{ 'profile-bio-row': field.key === 'bio' }"
              >
                <label
                  class="text-[13px] font-bold text-[var(--muted)]"
                  :class="field.key === 'bio'
                    ? 'col-start-1 row-start-1 col-span-1 sm:col-start-1 sm:col-span-2 sm:row-start-1'
                    : 'col-span-2 sm:col-span-1 sm:col-start-1 sm:row-start-1'"
                  :for="`profile-${field.key}`"
                >
                  <span>{{ field.label }}</span>
                  <small v-if="field.key === 'bio'" class="profile-field-caption">{{ t("profile.bioHint") }}</small>
                </label>

                <PButton
                  class="justify-self-end sm:col-start-3 sm:row-start-1 sm:self-start shrink-0"
                  :class="field.key === 'bio' ? 'col-start-2 row-start-1 self-start' : 'col-start-2 row-start-2 self-center'"
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

                <div
                  class="profile-field-value col-start-1 row-start-2 min-w-0"
                  :class="field.key === 'bio'
                    ? 'profile-bio-value col-span-2 sm:col-start-1 sm:col-span-3 sm:row-start-2'
                    : 'sm:col-span-1 sm:col-start-2 sm:row-start-1'"
                >
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
                    :class="{ 'profile-bio-textarea': field.key === 'bio' }"
                    maxlength="500"
                    rows="4"
                    autofocus
                  ></textarea>
                  <p
                    v-else
                    class="m-0 text-[15px] font-semibold leading-relaxed break-words whitespace-pre-wrap"
                    :class="[
                      field.key === 'bio' ? '' : 'px-3',
                      { 'text-[var(--subtle)]': !profileForm[field.key] },
                    ]"
                  >
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
              </UiFlatRow>
            </div>

            <UiFlatRow
              as="section"
              :active="isEditingSocialLinks"
              muted
              class="profile-social-row grid grid-cols-[minmax(0,1fr)_auto] items-start"
            >
              <div class="col-start-1 row-start-1 min-w-0">
                <strong class="text-[13px] font-bold text-[var(--muted)]">{{ t("profile.socialLinks") }}</strong>
                <p class="m-0 mt-1 text-[12px] font-semibold text-[var(--subtle)] leading-snug">{{ t("profile.socialLinksHint") }}</p>
              </div>

              <PButton
                class="col-start-2 row-start-1 self-start justify-self-end shrink-0"
                :disabled="isSavingProfile || (isEditingSocialLinks && hasSocialLinkErrors)"
                :icon="isEditingSocialLinks ? 'pi pi-check' : 'pi pi-pencil'"
                variant="text"
                severity="secondary"
                rounded
                :aria-label="t('profile.socialLinks')"
                @click="isEditingSocialLinks ? confirmSocialLinksEdit() : (isEditingSocialLinks = true)"
              >
                <template #icon>
                  <i :class="isSavingProfile && isEditingSocialLinks ? 'pi pi-spinner pi-spin' : isEditingSocialLinks ? 'pi pi-check' : 'pi pi-pencil'"></i>
                </template>
              </PButton>

              <div class="col-start-1 col-span-2 row-start-2 min-w-0">
                <div v-if="!isEditingSocialLinks" class="social-links-preview" :class="{ empty: !socialLinkViews.length }">
                  <span v-if="!socialLinkViews.length">{{ t("profile.notSpecified") }}</span>
                  <a
                    v-for="link in socialLinkViews"
                    v-else
                    :key="`${link.label}-${link.url}`"
                    :href="link.url"
                    target="_blank"
                    rel="noreferrer"
                    class="social-link-chip"
                    :style="{ '--social-chip-color': link.meta.color }"
                  >
                    <span><SocialPlatformIcon :platform="link.meta.key" /></span>
                    <strong>{{ link.preview }}</strong>
                  </a>
                </div>

                <div v-else class="grid gap-3">
                  <SocialLinksEditor v-model="socialLinks" :disabled="isSavingProfile" @remove="removeSocialLink" />
                  <div class="flex flex-wrap items-center gap-2">
                    <PButton icon="pi pi-plus" rounded variant="text" severity="secondary" :disabled="socialLinks.length >= 10 || isSavingProfile" @click="addSocialLink" />
                  </div>
                </div>
              </div>
            </UiFlatRow>
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

<style scoped>
.profile-nav-column {
  align-self: start;
}

.fullscreen-page-heading {
  display: grid;
  justify-items: start;
  gap: 8px;
}

.fullscreen-page-heading :deep(.ui-section-header) {
  width: 100%;
}

.profile-screen,
.profile-form {
  display: grid;
}

.profile-screen {
  gap: 22px;
}

.profile-form {
  gap: 16px;
}

.profile-details-list {
  gap: 10px;
}

.profile-field-row,
.profile-social-row {
  column-gap: 14px;
  row-gap: 12px;
  padding: 14px;
}

@media (min-width: 1024px) {
  .profile-nav-column {
    position: sticky;
    top: calc(env(safe-area-inset-top, 0px) + 24px);
    z-index: 20;
  }
}

.profile-avatar-section {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 18px;
}

.profile-avatar-section :deep(.p-button) {
  grid-column: 1 / -1;
  justify-self: start;
}

.profile-avatar-button {
  position: relative;
  width: 72px;
  height: 72px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  padding: 0;
  display: inline-grid;
  place-items: center;
  cursor: pointer;
}

.profile-avatar-button:disabled {
  cursor: progress;
}

.profile-avatar-button:focus-visible {
  outline: 0;
  box-shadow: 0 0 0 4px var(--focus-ring);
}

.profile-avatar-frame {
  width: 72px;
  height: 72px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--surface-muted);
  color: var(--text);
  display: grid;
  place-items: center;
  font-size: 22px;
  font-weight: 850;
}

.profile-avatar-frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-avatar-action {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 30px;
  height: 30px;
  border-radius: 999px;
  background: var(--text);
  color: var(--bg);
  display: grid;
  place-items: center;
  font-size: 13px;
  box-shadow: var(--shadow-sm);
}

.profile-bio-row {
  column-gap: 0;
  row-gap: 14px;
}

.profile-field-caption {
  display: block;
  margin-top: 5px;
  color: var(--subtle);
  font-size: 12px;
  line-height: 1.25;
  font-weight: 600;
}

.profile-bio-row :deep(.p-textarea) {
  margin: 0;
}

.profile-bio-value {
  padding-left: 0;
  margin-left: 0;
}

.profile-bio-row .profile-bio-textarea {
  display: block;
  text-indent: 0;
}

.social-links-preview {
  min-height: 40px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  padding: 0 12px;
}

.social-links-preview.empty {
  color: var(--subtle);
  font-size: 15px;
  font-weight: 600;
}

.social-link-chip {
  min-width: 0;
  max-width: 100%;
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 10px 5px 6px;
  border-radius: 999px;
  background: var(--surface);
  color: var(--text);
  text-decoration: none;
  font-size: 13px;
  font-weight: 800;
  transition: background var(--motion), transform var(--motion-fast);
}

.social-link-chip:hover {
  background: var(--surface-active);
  transform: translateY(-1px);
}

.social-link-chip span {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  background: color-mix(in srgb, var(--social-chip-color) 14%, transparent);
  color: var(--social-chip-color);
  font-size: 13px;
}

.social-link-chip span :deep(svg) {
  width: 13px;
  height: 13px;
}

.social-link-chip strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (min-width: 640px) {
  .profile-screen {
    gap: 24px;
  }

  .profile-form {
    gap: 18px;
  }

  .profile-details-list {
    gap: 12px;
  }

  .profile-field-row,
  .profile-social-row {
    column-gap: 18px;
    row-gap: 14px;
    padding: 18px;
  }

  .profile-avatar-section {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .profile-avatar-section :deep(.p-button) {
    grid-column: auto;
    justify-self: end;
  }
}
</style>
