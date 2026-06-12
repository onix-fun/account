import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { AuthService, type AccountLookupResponse } from "@/api/services/AuthService";
import { apiErrorMessage, parseApiError } from "@/api/client";
import { trustedRedirectUrl } from "@/infra/navigation/trustedRedirect";
import { useAuthStore } from "@/infra/store";
import { isEmail, isPassword, isUsername, isVerificationCode } from "@/shared/lib/validation";

export type AuthMode = "identifier" | "password" | "register" | "confirm" | "verify" | "name" | "forgot" | "reset";

export function useAuthFlow() {
  const route = useRoute();
  const router = useRouter();
  const authStore = useAuthStore();
  const { t } = useI18n();

  const mode = ref<AuthMode>(authStore.isCompletingRegistrationProfile ? "name" : "identifier");
  const authMessage = ref("");
  const fieldErrors = ref<Record<string, string>>({});
  const loginIdentifier = ref("");
  const loginPassword = ref("");
  const registerForm = ref({
    email: "",
    username: "",
    password: "",
    confirmPassword: "",
  });
  const nameForm = ref({
    firstName: "",
    lastName: "",
  });
  const pendingRegistrationEmail = ref("");
  const registrationCode = ref("");
  const publicVerificationCode = ref("");
  const accountLookup = ref<AccountLookupResponse | null>(null);
  const isLookupLoading = ref(false);
  const lookupPurpose = ref<"login" | "forgot">("login");
  const forgotIdentifier = ref("");
  const resetIdentifier = ref("");
  const resetCode = ref("");
  const resetPassword = ref("");
  const isCheckingUsername = ref(false);
  const isUsernameTaken = ref(false);
  const usernameCheckTouched = ref(false);
  let usernameTimeout: ReturnType<typeof setTimeout> | null = null;

  const activeStep = computed(() => {
    if (mode.value === "password" || mode.value === "forgot" || mode.value === "reset") return "password";
    if (mode.value === "confirm" || mode.value === "verify") return "code";
    if (mode.value === "name") return "done";
    return "account";
  });

  const showRegistrationSteps = computed(() => {
    return mode.value === "register" || mode.value === "confirm" || mode.value === "name";
  });

  const registerPasswordMismatch = computed(() => {
    return Boolean(registerForm.value.confirmPassword) && registerForm.value.password !== registerForm.value.confirmPassword;
  });
  const registerPasswordValid = computed(() => isPassword(registerForm.value.password));
  const registerPasswordsMatch = computed(() => Boolean(registerForm.value.confirmPassword) && !registerPasswordMismatch.value);
  const accountDisplayName = computed(() => {
    return authStore.currentUser?.username || registerForm.value.username || loginIdentifier.value.trim();
  });
  const accountAvatarUrl = computed(() => accountLookup.value?.avatarUrl || authStore.currentUser?.avatarUrl || "");
  const validateIdentifier = (rawValue: string) => {
    const value = rawValue.trim();
    if (!value) return t("errors.VALIDATION_REQUIRED_FIELD");
    if (value.includes("@") && !isEmail(value)) return t("errors.VALIDATION_INVALID_EMAIL");
    if (!value.includes("@") && !isUsername(value)) return t("errors.VALIDATION_USERNAME_TOO_SHORT");
    return "";
  };
  const identifierError = computed(() => validateIdentifier(loginIdentifier.value));
  const forgotIdentifierError = computed(() => validateIdentifier(forgotIdentifier.value));
  const registrationErrors = computed(() => ({
    username: isUsername(registerForm.value.username) ? "" : t("errors.VALIDATION_USERNAME_TOO_SHORT"),
    email: isEmail(registerForm.value.email) ? "" : t("errors.VALIDATION_INVALID_EMAIL"),
    password: registerPasswordValid.value ? "" : t("errors.VALIDATION_PASSWORD_TOO_SHORT"),
    confirmPassword: registerPasswordsMatch.value ? "" : t("auth.passwordMismatch"),
  }));
  const canRegister = computed(() => Object.values(registrationErrors.value).every((value) => !value));

  const title = computed(() => {
    if (mode.value === "register") return t("auth.registerTitle");
    if (mode.value === "confirm") return t("auth.confirmTitle");
    if (mode.value === "verify") return t("auth.confirmTitle");
    if (mode.value === "name") return t("auth.nameTitle");
    if (mode.value === "forgot") return t("auth.forgotTitle");
    if (mode.value === "reset") return t("auth.resetTitle");
    return t("auth.signIn");
  });

  watch(
    () => registerForm.value.username,
    (newVal) => {
      usernameCheckTouched.value = true;
      isCheckingUsername.value = false;
      isUsernameTaken.value = false;

      if (usernameTimeout) clearTimeout(usernameTimeout);

      const username = newVal.trim();
      if (!username) {
        usernameCheckTouched.value = false;
        return;
      }

      isCheckingUsername.value = true;
      usernameTimeout = setTimeout(async () => {
        try {
          isUsernameTaken.value = !(await AuthService.isUsernameAvailable(username));
        } catch {
          isUsernameTaken.value = false;
        } finally {
          isCheckingUsername.value = false;
        }
      }, 450);
    },
  );

  const handleAuthSuccess = async () => {
    const target = trustedRedirectUrl(route.query.redirect);
    if (target) {
      window.location.href = target;
      return;
    }
    await router.push("/");
  };

  const showIdentifierStep = () => {
    authMessage.value = "";
    fieldErrors.value = {};
    accountLookup.value = null;
    lookupPurpose.value = "login";
    mode.value = "identifier";
  };

  const showForgotStep = () => {
    authMessage.value = "";
    fieldErrors.value = {};
    forgotIdentifier.value = loginIdentifier.value.trim();
    lookupPurpose.value = "forgot";
    mode.value = "forgot";
  };

  const openRegistrationForIdentifier = (identifier: string) => {
    const normalized = identifier.trim();
    registerForm.value.email = normalized.includes("@") ? normalized : "";
    registerForm.value.username = normalized.includes("@") ? "" : normalized;
    mode.value = "register";
  };

  const startPasswordReset = async (identifier: string) => {
    await authStore.forgotPassword(identifier);
    resetIdentifier.value = identifier.trim();
    resetCode.value = "";
    resetPassword.value = "";
    authMessage.value = t("auth.resetSent");
    mode.value = "reset";
  };

  const handleUnavailableAccount = async (identifier: string, lookup: AccountLookupResponse) => {
    if (lookup.state === "NOT_FOUND") {
      openRegistrationForIdentifier(identifier);
    } else if (lookup.state === "PENDING_REGISTRATION") {
      pendingRegistrationEmail.value = lookup.identifier;
      mode.value = "confirm";
      authMessage.value = "";
    } else if (lookup.state === "EMAIL_UNVERIFIED") {
      await AuthService.requestPublicVerification(identifier);
      publicVerificationCode.value = "";
      mode.value = "verify";
      authMessage.value = t("auth.verificationSent");
    } else {
      authMessage.value = t("errors.AUTH_ACCOUNT_BLOCKED");
    }
  };

  const continueToPassword = async () => {
    authMessage.value = "";
    fieldErrors.value = {};
    if (identifierError.value) return;
    lookupPurpose.value = "login";
    isLookupLoading.value = true;
    try {
      const lookup = await AuthService.lookupAccount(loginIdentifier.value);
      accountLookup.value = lookup;
      if (lookup.state === "ACTIVE" || lookup.state === "EMAIL_LOGIN") {
        mode.value = "password";
      } else {
        await handleUnavailableAccount(loginIdentifier.value, lookup);
      }
    } catch (cause) {
      captureError(cause);
    } finally {
      isLookupLoading.value = false;
    }
  };

  const login = async () => {
    authMessage.value = "";
    fieldErrors.value = {};
    try {
      await authStore.login(loginIdentifier.value, loginPassword.value);
      await handleAuthSuccess();
    } catch (cause) {
      captureError(cause);
    }
  };

  const register = async () => {
    authMessage.value = "";
    fieldErrors.value = {};
    if (!canRegister.value) return;
    try {
      await authStore.register({
        email: registerForm.value.email,
        username: registerForm.value.username,
        password: registerForm.value.password,
      });
      pendingRegistrationEmail.value = registerForm.value.email;
      registrationCode.value = "";
      mode.value = "confirm";
      authMessage.value = t("auth.verificationSent");
    } catch (cause) {
      captureError(cause);
    }
  };

  const confirmPublicVerification = async () => {
    authMessage.value = "";
    fieldErrors.value = {};
    if (!isVerificationCode(publicVerificationCode.value)) return;
    try {
      await AuthService.confirmPublicVerification(loginIdentifier.value, publicVerificationCode.value);
      if (lookupPurpose.value === "forgot") {
        await startPasswordReset(loginIdentifier.value);
      } else {
        mode.value = "password";
        authMessage.value = t("auth.emailConfirmed");
      }
    } catch (cause) {
      captureError(cause);
    }
  };

  const confirmRegistration = async () => {
    authMessage.value = "";
    fieldErrors.value = {};
    if (!isVerificationCode(registrationCode.value)) {
      fieldErrors.value = { code: t("errors.VALIDATION_INVALID_CODE") };
      return;
    }
    try {
      await authStore.confirmRegistration(pendingRegistrationEmail.value, registrationCode.value);
      nameForm.value = { firstName: "", lastName: "" };
      mode.value = "name";
      authMessage.value = t("auth.emailConfirmed");
    } catch (cause) {
      captureError(cause);
    }
  };

  const completeNameStep = async () => {
    authMessage.value = "";
    try {
      await authStore.completeRegistrationProfile(nameForm.value);
      await handleAuthSuccess();
    } catch {
      authMessage.value = authStore.error || t("auth.profileFailed");
    }
  };

  const resendRegistrationCode = async () => {
    authMessage.value = "";
    try {
      await authStore.resendRegistrationCode(
        pendingRegistrationEmail.value || registerForm.value.email,
      );
      authMessage.value = t("auth.verificationResent");
    } catch {
      authMessage.value = authStore.error || t("auth.confirmationFailed");
    }
  };

  const forgotPassword = async () => {
    authMessage.value = "";
    fieldErrors.value = {};
    if (forgotIdentifierError.value) return;
    loginIdentifier.value = forgotIdentifier.value.trim();
    lookupPurpose.value = "forgot";
    isLookupLoading.value = true;
    try {
      const lookup = await AuthService.lookupAccount(loginIdentifier.value);
      accountLookup.value = lookup;
      if (lookup.state === "ACTIVE" || lookup.state === "EMAIL_LOGIN") {
        await startPasswordReset(loginIdentifier.value);
      } else {
        await handleUnavailableAccount(loginIdentifier.value, lookup);
      }
    } catch (cause) {
      captureError(cause);
    } finally {
      isLookupLoading.value = false;
    }
  };

  const submitResetPassword = async () => {
    authMessage.value = "";
    fieldErrors.value = {};
    if (!isVerificationCode(resetCode.value)) {
      fieldErrors.value = { code: t("errors.VALIDATION_INVALID_CODE") };
      return;
    }
    try {
      await authStore.resetPassword(resetIdentifier.value, resetCode.value, resetPassword.value);
      mode.value = "identifier";
      authMessage.value = t("auth.passwordUpdated");
    } catch (cause) {
      captureError(cause);
    }
  };

  const captureError = (cause: unknown) => {
    const parsed = parseApiError(cause);
    fieldErrors.value = Object.fromEntries(
      parsed.fieldErrors.map((error) => [error.field, t(`errors.${error.code}`)]),
    );
    if (!parsed.fieldErrors.length) authMessage.value = apiErrorMessage(cause);
  };

  return {
    activeStep,
    accountAvatarUrl,
    accountDisplayName,
    authMessage,
    canRegister,
    completeNameStep,
    confirmRegistration,
    confirmPublicVerification,
    continueToPassword,
    forgotIdentifier,
    forgotIdentifierError,
    forgotPassword,
    fieldErrors,
    isCheckingUsername,
    isLookupLoading,
    isUsernameTaken,
    login,
    loginIdentifier,
    loginPassword,
    mode,
    nameForm,
    pendingRegistrationEmail,
    publicVerificationCode,
    register,
    registerForm,
    registerPasswordMismatch,
    registerPasswordsMatch,
    registerPasswordValid,
    registrationErrors,
    registrationCode,
    resendRegistrationCode,
    resetCode,
    resetIdentifier,
    resetPassword,
    showIdentifierStep,
    showForgotStep,
    showRegistrationSteps,
    submitResetPassword,
    title,
    identifierError,
    usernameCheckTouched,
  };
}
