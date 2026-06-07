import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type { AuthSession, User } from "@/domain";
import { AuthService, type RegistrationStartedResponse } from "@/api/services/AuthService";
import { apiErrorMessage } from "@/api/client";

export const useAuthStore = defineStore("auth", () => {
  const currentUser = ref<User | null>(AuthService.getStoredSession());
  const storedAccounts = ref<User[]>(AuthService.getStoredAccounts());
  const sessions = ref<AuthSession[]>([]);
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

  const initAuth = async () => {
    error.value = null;

    isLoading.value = true;
    try {
      currentUser.value = await AuthService.refresh();
      syncAccounts();
    } finally {
      isLoading.value = false;
    }
  };

  const switchAccount = async (userId: string) => {
    sessions.value = [];
    currentUser.value = await AuthService.switchAccount(userId);
    syncAccounts();
    if (currentUser.value) await fetchSessions();
  };

  const login = async (identifier: string, password: string) => {
    isLoading.value = true;
    error.value = null;
    try {
      currentUser.value = await AuthService.login({ identifier, password });
      syncAccounts();
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
      isCompletingRegistrationProfile.value = true;
      syncAccounts();
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

  const updateProfile = async (payload: { username?: string; firstName?: string; lastName?: string; bio?: string }) => {
    currentUser.value = await AuthService.updateProfile(payload);
    syncAccounts();
  };

  const requestEmailChange = (currentPassword: string, newEmail: string) => AuthService.requestEmailChange(currentPassword, newEmail);
  const confirmEmailChange = async (code: string) => {
    currentUser.value = await AuthService.confirmEmailChange(code);
  };
  const cancelEmailChange = () => AuthService.cancelEmailChange();

  const completeRegistrationProfile = async (payload: { firstName?: string; lastName?: string }) => {
    currentUser.value = await AuthService.updateProfile(payload);
    isCompletingRegistrationProfile.value = false;
    syncAccounts();
  };

  const uploadAvatar = async (file: File) => {
    currentUser.value = await AuthService.uploadAvatar(file);
    syncAccounts();
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
    if (currentUser.value) await fetchSessions();
  };

  const logoutAll = async () => {
    isCompletingRegistrationProfile.value = false;
    currentUser.value = await AuthService.logoutAll();
    sessions.value = [];
    syncAccounts();
    if (currentUser.value) await fetchSessions();
  };

  const refreshMe = async () => {
    try {
      currentUser.value = await AuthService.getMe();
      syncAccounts();
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
  };

  return {
    currentUser,
    storedAccounts,
    sessions,
    isAuthenticated,
    isCompletingRegistrationProfile,
    displayName,
    isLoading,
    error,
    initAuth,
    switchAccount,
    promptAddAccount,
    login,
    register,
    confirmRegistration,
    resendRegistrationCode,
    updateProfile,
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
    revokeSession,
    logout,
    logoutAll,
    refreshMe,
  };
});
