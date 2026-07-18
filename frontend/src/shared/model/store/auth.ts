import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type { AuthSession, Organization, OrganizationInvitation, OwnerIdentity, OwnerType, User } from "@/shared/model/domain";
import { AuthService, type RegistrationStartedResponse } from "@/shared/api/services/AuthService";
import { apiErrorMessage } from "@/shared/api/client";
import { setLocale, type SupportedLocale } from "@/shared/i18n";
import {
  clearActiveOwnerPreference,
  rememberActiveOwnerPreference,
  rememberUserOwnerPreference,
} from "@/shared/lib/activeOwnerPreference";

export const useAuthStore = defineStore("auth", () => {
  const currentUser = ref<User | null>(AuthService.getStoredSession());
  const storedAccounts = ref<User[]>(AuthService.getStoredAccounts());
  const sessions = ref<AuthSession[]>([]);
  const activeOwner = ref<OwnerIdentity | null>(null);
  const organizations = ref<Organization[]>([]);
  const organizationInvitations = ref<OrganizationInvitation[]>([]);
  const isLoading = ref(false);
  const error = ref<string | null>(null);
  const isCompletingRegistrationProfile = ref(false);

  const isAuthenticated = computed(() => Boolean(currentUser.value));
  const displayName = computed(() => {
    const user = currentUser.value;
    if (!user) return "";
    return [user.firstName, user.lastName].filter(Boolean).join(" ") || user.username;
  });

  const syncAccounts = () => {
    storedAccounts.value = AuthService.getStoredAccounts();
  };

  const applyUserLocale = () => {
    const locale = currentUser.value?.preferredLocale;
    if (locale === "ru" || locale === "en") setLocale(locale);
  };

  const rememberCurrentOwner = (owner: OwnerIdentity | null) => {
    if (!currentUser.value || !owner) return;
    rememberActiveOwnerPreference(currentUser.value.id, owner);
  };

  const resetActiveOwnerToCurrentUser = async () => {
    if (!currentUser.value) {
      clearActiveOwnerPreference();
      return;
    }
    const owner = await AuthService.switchOwner("USER", currentUser.value.id);
    activeOwner.value = owner;
    if (owner) rememberCurrentOwner(owner);
    else rememberUserOwnerPreference(currentUser.value.id);
  };

  const initAuth = async () => {
    error.value = null;

    isLoading.value = true;
    try {
      currentUser.value = await AuthService.refresh();
      applyUserLocale();
      syncAccounts();
      if (currentUser.value) await loadOrganizationContext();
    } finally {
      isLoading.value = false;
    }
  };

  const switchAccount = async (userId: string) => {
    sessions.value = [];
    currentUser.value = await AuthService.switchAccount(userId);
    applyUserLocale();
    syncAccounts();
    await resetActiveOwnerToCurrentUser();
    await loadOrganizationContext();
    if (currentUser.value) await fetchSessions();
  };

  const loadOrganizationContext = async () => {
    if (!currentUser.value) {
      activeOwner.value = null;
      organizations.value = [];
      organizationInvitations.value = [];
      clearActiveOwnerPreference();
      return;
    }
    const context = await AuthService.organizationContext();
    activeOwner.value = context.activeOwner;
    organizations.value = context.organizations;
    organizationInvitations.value = context.pendingInvitations;
    rememberCurrentOwner(context.activeOwner);
  };

  const createOrganization = async (payload: { orgName: string; displayName: string; bio?: string; socialLinks?: Array<{ label: string; url: string }> }) => {
    await AuthService.createOrganization(payload);
    await loadOrganizationContext();
  };

  const updateOrganization = async (orgId: string, payload: { orgName?: string; displayName?: string; bio?: string | null; socialLinks?: Array<{ label: string; url: string }> }) => {
    await AuthService.updateOrganization(orgId, payload);
    await loadOrganizationContext();
  };

  const uploadOrganizationAvatar = async (orgId: string, file: File) => {
    await AuthService.uploadOrganizationAvatar(orgId, file);
    await loadOrganizationContext();
  };

  const inviteOrganizationMember = async (orgId: string, payload: { username?: string; userId?: string; role?: "OWNER" | "CONTRIBUTOR" }) => {
    await AuthService.inviteOrganizationMember(orgId, payload);
    await loadOrganizationContext();
  };

  const respondOrganizationInvitation = async (invitationId: string, accept: boolean) => {
    if (accept) await AuthService.acceptOrganizationInvitation(invitationId);
    else await AuthService.declineOrganizationInvitation(invitationId);
    await loadOrganizationContext();
  };

  const switchOwner = async (ownerType: OwnerType, ownerId: string) => {
    activeOwner.value = await AuthService.switchOwner(ownerType, ownerId);
    rememberCurrentOwner(activeOwner.value);
    await loadOrganizationContext();
  };

  const login = async (identifier: string, password: string) => {
    isLoading.value = true;
    error.value = null;
    try {
      currentUser.value = await AuthService.login({ identifier, password });
      applyUserLocale();
      syncAccounts();
      await loadOrganizationContext();
      await fetchSessions();
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };

  const consumeQrLogin = async (payload: { scanToken?: string; manualCode?: string }) => {
    isLoading.value = true;
    error.value = null;
    try {
      currentUser.value = await AuthService.consumeQrLogin(payload);
      applyUserLocale();
      syncAccounts();
      await loadOrganizationContext();
      await fetchSessions();
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };

  const register = async (payload: {
    email: string;
    username: string;
    password: string;
  }): Promise<RegistrationStartedResponse> => {
    isLoading.value = true;
    error.value = null;
    try {
      return await AuthService.register(payload);
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };

  const confirmRegistration = async (email: string, code: string) => {
    isLoading.value = true;
    error.value = null;
    try {
      currentUser.value = await AuthService.confirmRegistration(email, code);
      applyUserLocale();
      isCompletingRegistrationProfile.value = true;
      syncAccounts();
      await loadOrganizationContext();
      return currentUser.value;
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };

  const resendRegistrationCode = async (email: string) => {
    isLoading.value = true;
    error.value = null;
    try {
      return await AuthService.resendRegistrationCode(email);
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };

  const updateProfile = async (payload: {
    username?: string;
    firstName?: string;
    lastName?: string;
    bio?: string;
    birthDate?: string | null;
    socialLinks?: Array<{ label: string; url: string }>;
  }) => {
    isLoading.value = true;
    error.value = null;
    try {
      currentUser.value = await AuthService.updateProfile(payload);
      syncAccounts();
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };

  const updatePreferredLocale = async (locale: SupportedLocale) => {
    currentUser.value = await AuthService.updatePreferredLocale(locale);
    applyUserLocale();
    syncAccounts();
  };

  const requestEmailChange = (currentPassword: string, newEmail: string) => AuthService.requestEmailChange(currentPassword, newEmail);
  const confirmEmailChange = async (code: string) => {
    isLoading.value = true;
    error.value = null;
    try {
      currentUser.value = await AuthService.confirmEmailChange(code);
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };
  const cancelEmailChange = () => AuthService.cancelEmailChange();

  const completeRegistrationProfile = async (payload: { firstName?: string; lastName?: string }) => {
    isLoading.value = true;
    error.value = null;
    try {
      currentUser.value = await AuthService.updateProfile(payload);
      isCompletingRegistrationProfile.value = false;
      syncAccounts();
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };

  const uploadAvatar = async (file: File) => {
    isLoading.value = true;
    error.value = null;
    try {
      currentUser.value = await AuthService.uploadAvatar(file);
      syncAccounts();
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };

  const verifyEmail = async (code: string) => {
    await AuthService.verifyEmail(code);
    currentUser.value = await AuthService.getMe();
    syncAccounts();
  };

  const resendVerification = async () => {
    await AuthService.resendVerification();
  };

  const forgotPassword = async (identifier: string) => {
    await AuthService.forgotPassword(identifier);
  };

  const resetPassword = async (identifier: string, code: string, newPassword: string) => {
    await AuthService.resetPassword(identifier, code, newPassword);
  };

  const changePassword = async (currentPassword: string, newPassword: string) => {
    isLoading.value = true;
    error.value = null;
    try {
      currentUser.value = await AuthService.changePassword(currentPassword, newPassword);
      sessions.value = [];
      syncAccounts();
      if (currentUser.value) await fetchSessions();
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };

  const resetPasswordAndEndSession = async (identifier: string, code: string, newPassword: string) => {
    isLoading.value = true;
    error.value = null;
    try {
      await AuthService.resetPasswordAndEndSession(identifier, code, newPassword);
      isCompletingRegistrationProfile.value = false;
      currentUser.value = null;
      sessions.value = [];
      await loadOrganizationContext();
      syncAccounts();
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };

  const deleteAccount = async (password: string) => {
    isLoading.value = true;
    error.value = null;
    try {
      isCompletingRegistrationProfile.value = false;
      currentUser.value = await AuthService.deleteAccount(password);
      sessions.value = [];
      syncAccounts();
      if (currentUser.value) await resetActiveOwnerToCurrentUser();
      if (currentUser.value) await fetchSessions();
    } catch (cause) {
      error.value = apiErrorMessage(cause);
      throw cause;
    } finally {
      isLoading.value = false;
    }
  };

  const fetchSessions = async () => {
    sessions.value = await AuthService.getSessions();
  };

  const revokeSession = async (id: string) => {
    await AuthService.revokeSession(id);
    sessions.value = sessions.value.filter((session) => session.id !== id);
  };

  const logout = async () => {
    isCompletingRegistrationProfile.value = false;
    currentUser.value = await AuthService.logout();
    sessions.value = [];
    syncAccounts();
    if (currentUser.value) await resetActiveOwnerToCurrentUser();
    await loadOrganizationContext();
    if (currentUser.value) await fetchSessions();
  };

  const logoutAll = async () => {
    isCompletingRegistrationProfile.value = false;
    currentUser.value = await AuthService.logoutAll();
    sessions.value = [];
    syncAccounts();
    if (currentUser.value) await resetActiveOwnerToCurrentUser();
    await loadOrganizationContext();
    if (currentUser.value) await fetchSessions();
  };

  const refreshMe = async () => {
    try {
      currentUser.value = await AuthService.getMe();
      applyUserLocale();
      syncAccounts();
      await loadOrganizationContext();
    } catch (cause) {
      error.value = apiErrorMessage(cause);
    } finally {
      isLoading.value = false;
    }
  };

  const promptAddAccount = () => {
    AuthService.promptAddAccount();
    isCompletingRegistrationProfile.value = false;
    currentUser.value = null;
    clearActiveOwnerPreference();
  };

  return {
    currentUser,
    storedAccounts,
    sessions,
    activeOwner,
    organizations,
    organizationInvitations,
    isAuthenticated,
    isCompletingRegistrationProfile,
    displayName,
    isLoading,
    error,
    initAuth,
    switchAccount,
    promptAddAccount,
    login,
    consumeQrLogin,
    register,
    confirmRegistration,
    resendRegistrationCode,
    updateProfile,
    updatePreferredLocale,
    requestEmailChange,
    confirmEmailChange,
    cancelEmailChange,
    completeRegistrationProfile,
    uploadAvatar,
    verifyEmail,
    resendVerification,
    forgotPassword,
    resetPassword,
    changePassword,
    resetPasswordAndEndSession,
    deleteAccount,
    fetchSessions,
    loadOrganizationContext,
    createOrganization,
    updateOrganization,
    uploadOrganizationAvatar,
    inviteOrganizationMember,
    respondOrganizationInvitation,
    switchOwner,
    revokeSession,
    logout,
    logoutAll,
    refreshMe,
  };
});
