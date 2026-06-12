<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage, parseApiError } from "@/api/client";
import { useAuthStore } from "@/infra/store";
import VerificationCodeInput from "@/shared/ui/VerificationCodeInput.vue";
import PasswordInput from "@/shared/ui/PasswordInput.vue";
import { isEmail, isVerificationCode } from "@/shared/lib/validation";
import LocaleSwitcher from "@/shared/ui/LocaleSwitcher.vue";
import ThemeSwitcher from "@/shared/ui/ThemeSwitcher.vue";

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "warning"];
}>();

const authStore = useAuthStore();
const { t } = useI18n();

const isResetCodeSent = ref(false);
const resetCodeError = ref("");
const emailChangeStep = ref<"request" | "confirm">("request");
const isChangingEmail = ref(false);
const emailChangeForm = reactive({ currentPassword: "", newEmail: "", code: "" });
const directForm = reactive({
  currentPassword: "",
  newPassword: "",
  confirmPassword: "",
});
const resetForm = reactive({
  code: "",
  newPassword: "",
  confirmPassword: "",
});
const deleteForm = reactive({
  password: "",
  confirmation: "",
});

const accountEmail = computed(() => authStore.currentUser?.email || "");
const directPasswordMismatch = computed(() => {
  return Boolean(directForm.confirmPassword) && directForm.newPassword !== directForm.confirmPassword;
});
const resetPasswordMismatch = computed(() => {
  return Boolean(resetForm.confirmPassword) && resetForm.newPassword !== resetForm.confirmPassword;
});
const canDeleteAccount = computed(() => {
  return Boolean(deleteForm.password) && deleteForm.confirmation.trim() === accountEmail.value;
});
const canRequestEmailChange = computed(() => {
  return Boolean(emailChangeForm.currentPassword) && isEmail(emailChangeForm.newEmail);
});

const requestEmailChange = async () => {
  if (!canRequestEmailChange.value) return;
  isChangingEmail.value = true;
  try {
    await authStore.requestEmailChange(emailChangeForm.currentPassword, emailChangeForm.newEmail);
    emailChangeStep.value = "confirm";
    emit("message", t("profile.emailChangeSent"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isChangingEmail.value = false;
  }
};

const confirmEmailChange = async () => {
  if (!isVerificationCode(emailChangeForm.code)) return;
  isChangingEmail.value = true;
  try {
    await authStore.confirmEmailChange(emailChangeForm.code);
    emailChangeStep.value = "request";
    emailChangeForm.currentPassword = "";
    emailChangeForm.newEmail = "";
    emailChangeForm.code = "";
    emit("message", t("profile.emailChanged"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isChangingEmail.value = false;
  }
};

const cancelEmailChange = async () => {
  if (emailChangeStep.value === "confirm") await authStore.cancelEmailChange().catch(() => undefined);
  emailChangeStep.value = "request";
  emailChangeForm.code = "";
};

const changePassword = async () => {
  if (directForm.newPassword !== directForm.confirmPassword) {
    emit("message", t("auth.passwordMismatch"), "error");
    return;
  }

  try {
    await authStore.changePassword(directForm.currentPassword, directForm.newPassword);
    emit("message", t("system.passwordChanged"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  }
};

const requestResetCode = async () => {
  resetCodeError.value = "";
  try {
    await authStore.forgotPassword(accountEmail.value);
    isResetCodeSent.value = true;
    emit("message", t("system.resetCodeSent"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  }
};

const resetPassword = async () => {
  resetCodeError.value = "";
  if (resetForm.newPassword !== resetForm.confirmPassword) {
    emit("message", t("auth.passwordMismatch"), "error");
    return;
  }

  try {
    await authStore.resetPasswordAndEndSession(accountEmail.value, resetForm.code, resetForm.newPassword);
    emit("message", t("system.passwordChanged"));
  } catch (cause) {
    const parsed = parseApiError(cause);
    const codeError = parsed.fieldErrors.find((error) => error.field === "code");
    if (codeError) {
      resetCodeError.value = t(`errors.${codeError.code}`);
    } else {
      emit("message", apiErrorMessage(cause), "error");
    }
  }
};

const deleteAccount = async () => {
  if (!canDeleteAccount.value) {
    emit("message", t("system.deleteConfirmationError"), "error");
    return;
  }

  try {
    await authStore.deleteAccount(deleteForm.password);
    emit("message", t("system.accountDeleted"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  }
};
</script>

<template>
  <section class="grid gap-2">
    <div class="flex items-center justify-between gap-3 min-h-[40px] px-1">
      <h2 class="text-base font-bold m-0 text-[var(--text)]">{{ t("system.title") }}</h2>
    </div>

    <section class="bg-[var(--surface)] p-4 rounded-2xl flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div class="flex items-center gap-3.5 min-w-0">
        <div class="w-10 h-10 rounded-lg bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)] shrink-0">
          <i class="pi pi-language text-lg"></i>
        </div>
        <div class="min-w-0">
          <h3 class="text-[15px] font-bold m-0 text-[var(--text)] leading-tight">{{ t("system.interface") }}</h3>
          <p class="m-0 mt-1 text-xs text-[var(--muted)] leading-relaxed truncate">{{ t("system.languageHint") }}</p>
        </div>
      </div>
      <LocaleSwitcher variant="dropdown" class="shrink-0 w-full sm:w-auto" />
    </section>

    <section class="bg-[var(--surface)] p-4 rounded-2xl flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div class="flex items-center gap-3.5 min-w-0">
        <div class="w-10 h-10 rounded-lg bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)] shrink-0">
          <i class="pi pi-palette text-lg"></i>
        </div>
        <div class="min-w-0">
          <h3 class="text-[15px] font-bold m-0 text-[var(--text)] leading-tight">{{ t("system.theme") }}</h3>
          <p class="m-0 mt-1 text-xs text-[var(--muted)] leading-relaxed truncate">{{ t("system.themeHint") }}</p>
        </div>
      </div>
      <ThemeSwitcher class="shrink-0" />
    </section>

    <section class="bg-[var(--surface)] p-4 rounded-2xl grid gap-4">
      <div class="flex items-start gap-3.5">
        <div class="w-10 h-10 rounded-lg bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)] shrink-0">
          <i class="pi pi-at text-lg"></i>
        </div>
        <div class="min-w-0">
          <h3 class="text-[15px] font-bold m-0 text-[var(--text)] leading-tight">{{ t("profile.changeEmail") }}</h3>
          <p class="m-0 mt-1 text-xs text-[var(--muted)] leading-relaxed">{{ t("system.changeEmailHint", { email: accountEmail }) }}</p>
        </div>
      </div>

      <form v-if="emailChangeStep === 'request'" class="grid gap-4 sm:px-1" @submit.prevent="requestEmailChange">
        <div class="grid gap-1.5">
          <span class="text-[13px] font-bold text-[var(--muted)]">{{ t("profile.newEmail") }}</span>
          <PInputText v-model="emailChangeForm.newEmail" type="email" autocomplete="email" required class="w-full" />
        </div>
        <div class="grid gap-1.5">
          <span class="text-[13px] font-bold text-[var(--muted)]">{{ t("system.currentPassword") }}</span>
          <PasswordInput v-model="emailChangeForm.currentPassword" autocomplete="current-password" required />
        </div>
        <PButton type="submit" :label="t('common.continue')" icon="pi pi-send" class="w-full sm:w-auto self-start" :disabled="isChangingEmail || !canRequestEmailChange" :loading="isChangingEmail" />
      </form>

      <form v-else class="grid gap-4 sm:px-1" @submit.prevent="confirmEmailChange">
        <div class="grid gap-1.5">
          <span class="text-[13px] font-bold text-[var(--muted)]">{{ t("auth.verificationCode") }}</span>
          <VerificationCodeInput v-model="emailChangeForm.code" autofocus />
        </div>
        <div class="flex flex-col sm:flex-row gap-2">
          <PButton type="submit" :label="t('common.apply')" icon="pi pi-check" class="w-full sm:w-auto" :disabled="isChangingEmail || !isVerificationCode(emailChangeForm.code)" :loading="isChangingEmail" />
          <PButton type="button" :label="t('common.cancel')" variant="text" severity="secondary" class="w-full sm:w-auto" :disabled="isChangingEmail" @click="cancelEmailChange" />
        </div>
      </form>
    </section>

    <section class="bg-[var(--surface)] p-4 rounded-2xl grid gap-4">
      <div class="flex items-start gap-3.5">
        <div class="w-10 h-10 rounded-lg bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)] shrink-0">
          <i class="pi pi-key text-lg"></i>
        </div>
        <div class="min-w-0">
          <h3 class="text-[15px] font-bold m-0 text-[var(--text)] leading-tight">{{ t("system.changePassword") }}</h3>
          <p class="m-0 mt-1 text-xs text-[var(--muted)] leading-relaxed">{{ t("system.changePasswordHint") }}</p>
        </div>
      </div>

      <form class="grid gap-4 sm:px-1" @submit.prevent="changePassword">
        <div class="grid gap-1.5">
          <span class="text-[13px] font-bold text-[var(--muted)]">{{ t("system.currentPassword") }}</span>
          <PasswordInput v-model="directForm.currentPassword" autocomplete="current-password" required />
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div class="grid gap-1.5">
            <span class="text-[13px] font-bold text-[var(--muted)]">{{ t("auth.newPassword") }}</span>
            <PasswordInput v-model="directForm.newPassword" autocomplete="new-password" minlength="8" required />
          </div>
          <div class="grid gap-1.5">
            <span class="text-[13px] font-bold text-[var(--muted)]">{{ t("auth.confirmPassword") }}</span>
            <PasswordInput v-model="directForm.confirmPassword" autocomplete="new-password" minlength="8" required :aria-invalid="directPasswordMismatch" />
            <span v-if="directPasswordMismatch" class="text-xs font-semibold text-[var(--danger)]">{{ t("auth.passwordMismatch") }}</span>
          </div>
        </div>
        <PButton type="submit" :label="t('system.changePasswordAction')" icon="pi pi-check" class="w-full sm:w-auto self-start" :disabled="authStore.isLoading || directPasswordMismatch" :loading="authStore.isLoading" />
      </form>
    </section>

    <section class="bg-[var(--surface)] p-4 rounded-2xl grid gap-4">
      <div class="flex items-start gap-3.5">
        <div class="w-10 h-10 rounded-lg bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)] shrink-0">
          <i class="pi pi-envelope text-lg"></i>
        </div>
        <div class="min-w-0">
          <h3 class="text-[15px] font-bold m-0 text-[var(--text)] leading-tight">{{ t("system.emailReset") }}</h3>
          <p class="m-0 mt-1 text-xs text-[var(--muted)] leading-relaxed">{{ t("system.emailResetHint", { email: accountEmail }) }}</p>
        </div>
      </div>

      <div v-if="!isResetCodeSent" class="sm:px-1">
        <PButton :label="t('system.sendResetCode')" icon="pi pi-send" variant="text" severity="secondary" class="w-full sm:w-auto" :disabled="authStore.isLoading" @click="requestResetCode" />
      </div>

      <form v-else class="grid gap-4 sm:px-1" @submit.prevent="resetPassword">
        <div class="grid gap-1.5">
          <span class="text-[13px] font-bold text-[var(--muted)]">{{ t("auth.verificationCode") }}</span>
          <VerificationCodeInput v-model="resetForm.code" :error="resetCodeError" />
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div class="grid gap-1.5">
            <span class="text-[13px] font-bold text-[var(--muted)]">{{ t("auth.newPassword") }}</span>
            <PasswordInput v-model="resetForm.newPassword" autocomplete="new-password" minlength="8" required />
          </div>
          <div class="grid gap-1.5">
            <span class="text-[13px] font-bold text-[var(--muted)]">{{ t("auth.confirmPassword") }}</span>
            <PasswordInput v-model="resetForm.confirmPassword" autocomplete="new-password" minlength="8" required :aria-invalid="resetPasswordMismatch" />
            <span v-if="resetPasswordMismatch" class="text-xs font-semibold text-[var(--danger)]">{{ t("auth.passwordMismatch") }}</span>
          </div>
        </div>
        <div class="flex flex-col sm:flex-row gap-2">
          <PButton type="submit" :label="t('system.resetPasswordAction')" icon="pi pi-check" class="w-full sm:w-auto" :disabled="authStore.isLoading || resetPasswordMismatch || !/^\d{6}$/.test(resetForm.code)" :loading="authStore.isLoading" />
          <PButton type="button" :label="t('system.resendResetCode')" icon="pi pi-refresh" variant="text" severity="secondary" class="w-full sm:w-auto" :disabled="authStore.isLoading" @click="requestResetCode" />
        </div>
      </form>
    </section>

    <section class="bg-[var(--danger-section-bg)] p-4 rounded-2xl grid gap-4">
      <div class="flex items-start gap-3.5">
        <div class="w-10 h-10 rounded-lg bg-[var(--toast-error-bg)] flex items-center justify-center text-[var(--danger)] shrink-0">
          <i class="pi pi-trash text-lg"></i>
        </div>
        <div class="min-w-0">
          <h3 class="text-[15px] font-bold m-0 text-[var(--text)] leading-tight">{{ t("system.deleteAccount") }}</h3>
          <p class="m-0 mt-1 text-xs text-[var(--muted)] leading-relaxed">{{ t("system.deleteAccountHint") }}</p>
        </div>
      </div>

      <form class="grid gap-4 sm:px-1" @submit.prevent="deleteAccount">
        <div class="grid gap-1.5">
          <span class="text-[13px] font-bold text-[var(--muted)]">{{ t("system.deleteConfirmation", { email: accountEmail }) }}</span>
          <PInputText v-model="deleteForm.confirmation" autocomplete="off" required class="w-full" />
        </div>
        <div class="grid gap-1.5">
          <span class="text-[13px] font-bold text-[var(--muted)]">{{ t("system.currentPassword") }}</span>
          <PasswordInput v-model="deleteForm.password" autocomplete="current-password" required />
        </div>
        <PButton type="submit" :label="t('system.deleteAccountAction')" icon="pi pi-trash" severity="danger" variant="text" class="w-full sm:w-auto self-start" :disabled="authStore.isLoading || !canDeleteAccount" :loading="authStore.isLoading" />
      </form>
    </section>
  </section>
</template>
