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
import AccountSwitchModal from "@/features/profile/ui/AccountSwitchModal.vue";
import BlockedUsersTab from "@/features/profile/ui/BlockedUsersTab.vue";
import CloseFriendsTab from "@/features/profile/ui/CloseFriendsTab.vue";
import NotificationsPage from "@/features/profile/ui/NotificationsPage.vue";
import OrganizationsTab from "@/features/profile/ui/OrganizationsTab.vue";
import ProfileListPage from "@/features/profile/ui/ProfileListPage.vue";
import ProfileMobileMenu from "@/features/profile/ui/ProfileMobileMenu.vue";
import ProfileNav, { type ProfileTab } from "@/features/profile/ui/ProfileNav.vue";
import ProfileSearchOverlay from "@/features/profile/ui/ProfileSearchOverlay.vue";
import ProfileTopCard from "@/features/profile/ui/ProfileTopCard.vue";
import RequestsTab from "@/features/profile/ui/RequestsTab.vue";
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
const accountMode = ref<AccountMode>("user");
const isSavingProfile = ref(false);
const isUploadingAvatar = ref(false);
const isAccountModalOpen = ref(false);
const isOrganizationSelectorOpen = ref(false);
const isSearchOpen = ref(false);
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
const showRequests = computed(() => socialStore.privacy.isPrivate);
const selectedOrganization = computed<Organization | null>(() => {
  const activeOrgId = authStore.activeOwner?.ownerType === "ORGANIZATION" ? authStore.activeOwner.ownerId : null;
  const id = selectedOrganizationId.value || activeOrgId;
  return authStore.organizations.find((organization) => organization.id === id) || null;
});
const queryView = computed<ProfileView | null>(() => {
  const value = route.query.view;
  return isProfileView(value) ? value : null;
});
const contentView = computed<ProfileView>(() => queryView.value ?? activeTab.value);
const showExternalBack = computed(() => Boolean(backUrl.value) && !queryView.value);
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
watch(showRequests, (enabled) => {
  if (enabled) return;
  if (activeTab.value === "requests") activeTab.value = "profile";
  if (queryView.value === "requests") closeView();
});

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
    value === "requests" ||
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
  }
  accountMode.value = "user";
}

function openOrganizationMode() {
  accountMode.value = "organization";
  isOrganizationSelectorOpen.value = true;
}

async function selectOrganization(organization: Organization) {
  selectedOrganizationId.value = organization.id;
  await authStore.switchOwner("ORGANIZATION", organization.id);
  accountMode.value = "organization";
  isOrganizationSelectorOpen.value = false;
}

function organizationInitials(organization: Organization): string {
  return organization.displayName.slice(0, 1).toUpperCase();
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
    <nav v-if="showExternalBack" class="w-full max-w-[800px] mx-auto flex items-center min-h-[38px]" aria-label="External navigation">
      <PButton :as="'a'" :href="backUrl" variant="text" icon="pi pi-arrow-left" :label="t('common.back')" severity="secondary" class="-ml-2" />
    </nav>

    <section v-if="!queryView" class="w-full max-w-[800px] mx-auto grid gap-3">
      <div class="grid grid-cols-2 gap-2 rounded-2xl bg-[var(--surface)] p-1 shadow-sm">
        <button
          type="button"
          class="min-h-[44px] rounded-xl border-0 font-bold cursor-pointer transition-colors"
          :class="accountMode === 'user' ? 'bg-[var(--text)] text-[var(--surface)]' : 'bg-transparent text-[var(--muted)] hover:bg-[var(--surface-muted)]'"
          @click="showUserProfile"
        >
          {{ t("organizations.userMode") }}
        </button>
        <button
          type="button"
          class="min-h-[44px] rounded-xl border-0 font-bold cursor-pointer transition-colors"
          :class="accountMode === 'organization' ? 'bg-[var(--text)] text-[var(--surface)]' : 'bg-transparent text-[var(--muted)] hover:bg-[var(--surface-muted)]'"
          @click="openOrganizationMode"
        >
          {{ t("organizations.organizationMode") }}
        </button>
      </div>
    </section>

    <ProfileTopCard
      v-if="!queryView && accountMode === 'user'"
      :user="authStore.currentUser"
      :summary="socialStore.summary"
      :avatar-preview="avatarPreview"
      :is-uploading-avatar="isUploadingAvatar"
      @avatar="openAvatarPicker"
      @switch-account="isAccountModalOpen = true"
      @open-view="openView"
    />

    <header v-if="!queryView && accountMode === 'organization' && selectedOrganization" class="w-full max-w-[800px] mx-auto grid gap-3 bg-[var(--surface)] rounded-2xl p-5 shadow-sm">
      <div class="flex items-center justify-between gap-3">
        <div class="flex items-center gap-3 min-w-0">
          <span class="w-14 h-14 rounded-2xl bg-[var(--surface-muted)] flex items-center justify-center text-lg font-bold overflow-hidden shrink-0">
            <img v-if="selectedOrganization.avatarUrl" :src="selectedOrganization.avatarUrl" alt="" class="w-full h-full object-cover" />
            <span v-else>{{ organizationInitials(selectedOrganization) }}</span>
          </span>
          <div class="min-w-0">
            <h1 class="m-0 text-2xl font-bold text-[var(--text)] truncate">{{ selectedOrganization.displayName }}</h1>
            <p class="m-0 mt-1 text-sm text-[var(--muted)] truncate">/o/{{ selectedOrganization.orgName }} · {{ selectedOrganization.role }}</p>
          </div>
        </div>
        <PButton icon="pi pi-building" variant="text" severity="secondary" class="w-10 h-10 border-0" :aria-label="t('organizations.choose')" @click="isOrganizationSelectorOpen = true" />
      </div>
    </header>

    <input
      ref="fileInput"
      class="hidden"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      @change="onAvatarChange"
    />

    <div
      class="grid grid-cols-1 justify-center items-start gap-7"
      :class="queryView || accountMode === 'organization' ? 'max-w-[720px] w-full mx-auto' : 'lg:grid-cols-[52px_minmax(0,720px)]'"
    >
      <div v-if="!queryView && accountMode === 'user'" class="hidden lg:block">
        <ProfileNav v-model:active-tab="activeTab" :show-requests="showRequests" @search="isSearchOpen = true" />
      </div>
      <ProfileMobileMenu v-if="!queryView && accountMode === 'user'" :show-requests="showRequests" @search="openView('search')" @open-view="openView" />

      <div class="min-w-0" :class="{ 'hidden lg:block': !queryView && accountMode === 'user' }">
        <section v-if="!queryView && accountMode === 'organization'" class="grid gap-4">
          <OrganizationsTab :selected-organization-id="selectedOrganization?.id || null" @message="setMessage" />
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

        <section v-else-if="contentView === 'requests'" class="grid gap-4">
          <PButton v-if="queryView" icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="-ml-2 justify-self-start" @click="closeView" />
          <RequestsTab @message="setMessage" />
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

    <ProfileSearchOverlay
      :visible="isSearchOpen"
      @close="isSearchOpen = false"
      @message="setMessage"
    />
    <AccountSwitchModal :visible="isAccountModalOpen" @close="isAccountModalOpen = false" />
    <PDialog
      :visible="isOrganizationSelectorOpen"
      modal
      dismissable-mask
      class="mobile-fullscreen-dialog w-full max-w-[520px]"
      :header="t('organizations.choose')"
      @update:visible="isOrganizationSelectorOpen = false"
    >
      <section class="grid gap-3 p-1">
        <PButton icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="mobile-dialog-back justify-self-start" @click="isOrganizationSelectorOpen = false" />
        <div v-if="!authStore.organizations.length" class="min-h-[280px] grid place-items-center text-center p-6 rounded-2xl bg-[var(--surface-muted)]">
          <div class="grid gap-3 justify-items-center">
            <span class="w-16 h-16 rounded-2xl bg-[var(--surface)] flex items-center justify-center text-2xl text-[var(--muted)]">
              <i class="pi pi-building"></i>
            </span>
            <h2 class="m-0 text-xl font-bold text-[var(--text)]">{{ t("organizations.emptyTitle") }}</h2>
            <p class="m-0 text-sm text-[var(--muted)] max-w-[320px]">{{ t("organizations.emptyHint") }}</p>
          </div>
        </div>

        <button
          v-for="organization in authStore.organizations"
          :key="organization.id"
          type="button"
          class="w-full min-h-[68px] grid grid-cols-[48px_minmax(0,1fr)_20px] items-center gap-3 p-3 rounded-2xl border-0 text-left bg-[var(--surface-raised)] hover:bg-[var(--surface-active)] text-[var(--text)] cursor-pointer"
          @click="selectOrganization(organization)"
        >
          <span class="w-12 h-12 rounded-2xl bg-[var(--surface-muted)] flex items-center justify-center font-bold overflow-hidden">
            <img v-if="organization.avatarUrl" :src="organization.avatarUrl" alt="" class="w-full h-full object-cover" />
            <span v-else>{{ organizationInitials(organization) }}</span>
          </span>
          <span class="min-w-0">
            <strong class="block truncate">{{ organization.displayName }}</strong>
            <small class="block truncate text-[var(--muted)]">/o/{{ organization.orgName }} · {{ organization.role }}</small>
          </span>
          <i v-if="selectedOrganization?.id === organization.id" class="pi pi-check text-[var(--success)]"></i>
        </button>

        <button
          type="button"
          class="w-full min-h-[64px] flex items-center gap-3 p-3 rounded-2xl border-0 bg-[var(--surface-raised)] hover:bg-[var(--surface-active)] text-[var(--text)] cursor-pointer text-left"
          @click="isOrganizationSelectorOpen = false"
        >
          <span class="w-12 h-12 rounded-2xl bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)]">
            <i class="pi pi-plus"></i>
          </span>
          <strong>{{ t("organizations.create") }}</strong>
        </button>
      </section>
    </PDialog>
    <AvatarCropper
      v-if="cropFile"
      :file="cropFile"
      @cancel="cropFile = null"
      @apply="uploadCroppedAvatar"
    />
    <PToast />
  </main>
</template>
