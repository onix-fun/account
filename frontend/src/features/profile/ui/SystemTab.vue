<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage, parseApiError } from "@/api/client";
import { useAuthStore } from "@/infra/store";
import VerificationCodeInput from "@/shared/ui/VerificationCodeInput.vue";
import PasswordInput from "@/shared/ui/PasswordInput.vue";
import { isEmail, isVerificationCode } from "@/shared/lib/validation";

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
  <section class="tab-panel system-panel">
    <div class="section-toolbar">
      <h2>{{ t("system.title") }}</h2>
    </div>

    <section class="system-section">
      <div class="system-section-head">
        <i class="pi pi-at row-icon"></i>
        <div>
          <h3>{{ t("profile.changeEmail") }}</h3>
          <p>{{ t("system.changeEmailHint", { email: accountEmail }) }}</p>
        </div>
      </div>

      <form v-if="emailChangeStep === 'request'" class="system-form" @submit.prevent="requestEmailChange">
        <label class="field">
          <span>{{ t("profile.newEmail") }}</span>
          <input v-model="emailChangeForm.newEmail" class="input" type="email" autocomplete="email" required />
        </label>
        <label class="field">
          <span>{{ t("system.currentPassword") }}</span>
          <PasswordInput v-model="emailChangeForm.currentPassword" class="input" autocomplete="current-password" required />
        </label>
        <button class="btn btn-primary system-submit" type="submit" :disabled="isChangingEmail || !canRequestEmailChange">
          <i class="pi pi-send"></i>
          {{ t("common.continue") }}
        </button>
      </form>

      <form v-else class="system-form" @submit.prevent="confirmEmailChange">
        <label class="field">
          <span>{{ t("auth.verificationCode") }}</span>
          <VerificationCodeInput v-model="emailChangeForm.code" autofocus />
        </label>
        <div class="system-actions email-change-actions">
          <button class="btn btn-primary" type="submit" :disabled="isChangingEmail || !isVerificationCode(emailChangeForm.code)">
            <i class="pi pi-check"></i>
            {{ t("common.apply") }}
          </button>
          <button class="btn btn-ghost" type="button" :disabled="isChangingEmail" @click="cancelEmailChange">
            {{ t("common.cancel") }}
          </button>
        </div>
      </form>
    </section>

    <section class="system-section">
      <div class="system-section-head">
        <i class="pi pi-key row-icon"></i>
        <div>
          <h3>{{ t("system.changePassword") }}</h3>
          <p>{{ t("system.changePasswordHint") }}</p>
        </div>
      </div>

      <form class="system-form" @submit.prevent="changePassword">
        <label class="field">
          <span>{{ t("system.currentPassword") }}</span>
          <PasswordInput
            v-model="directForm.currentPassword"
            class="input"
            autocomplete="current-password"
            required
          />
        </label>
        <div class="field-grid cols-2">
          <label class="field">
            <span>{{ t("auth.newPassword") }}</span>
            <PasswordInput
              v-model="directForm.newPassword"
              class="input"
              autocomplete="new-password"
              minlength="8"
              required
            />
          </label>
          <label class="field">
            <span>{{ t("auth.confirmPassword") }}</span>
            <PasswordInput
              v-model="directForm.confirmPassword"
              class="input"
              autocomplete="new-password"
              minlength="8"
              required
              :aria-invalid="directPasswordMismatch"
            />
            <span v-if="directPasswordMismatch" class="validation-message text-danger">
              {{ t("auth.passwordMismatch") }}
            </span>
          </label>
        </div>
        <button
          class="btn btn-primary system-submit"
          type="submit"
          :disabled="authStore.isLoading || directPasswordMismatch"
        >
          <i class="pi pi-check"></i>
          {{ t("system.changePasswordAction") }}
        </button>
      </form>
    </section>

    <section class="system-section">
      <div class="system-section-head">
        <i class="pi pi-envelope row-icon"></i>
        <div>
          <h3>{{ t("system.emailReset") }}</h3>
          <p>{{ t("system.emailResetHint", { email: accountEmail }) }}</p>
        </div>
      </div>

      <div v-if="!isResetCodeSent" class="system-actions">
        <button class="btn btn-ghost" type="button" :disabled="authStore.isLoading" @click="requestResetCode">
          <i class="pi pi-send"></i>
          {{ t("system.sendResetCode") }}
        </button>
      </div>

      <form v-else class="system-form" @submit.prevent="resetPassword">
        <label class="field">
          <span>{{ t("auth.verificationCode") }}</span>
          <VerificationCodeInput
            v-model="resetForm.code"
            :error="resetCodeError"
          />
        </label>
        <div class="field-grid cols-2">
          <label class="field">
            <span>{{ t("auth.newPassword") }}</span>
            <PasswordInput
              v-model="resetForm.newPassword"
              class="input"
              autocomplete="new-password"
              minlength="8"
              required
            />
          </label>
          <label class="field">
            <span>{{ t("auth.confirmPassword") }}</span>
            <PasswordInput
              v-model="resetForm.confirmPassword"
              class="input"
              autocomplete="new-password"
              minlength="8"
              required
              :aria-invalid="resetPasswordMismatch"
            />
            <span v-if="resetPasswordMismatch" class="validation-message text-danger">
              {{ t("auth.passwordMismatch") }}
            </span>
          </label>
        </div>
        <div class="system-actions">
          <button class="btn btn-primary" type="submit" :disabled="authStore.isLoading || resetPasswordMismatch || !/^\d{6}$/.test(resetForm.code)">
            <i class="pi pi-check"></i>
            {{ t("system.resetPasswordAction") }}
          </button>
          <button class="btn btn-ghost" type="button" :disabled="authStore.isLoading" @click="requestResetCode">
            <i class="pi pi-refresh"></i>
            {{ t("system.resendResetCode") }}
          </button>
        </div>
      </form>
    </section>

    <section class="system-section danger-system-section">
      <div class="system-section-head">
        <i class="pi pi-trash row-icon danger-icon"></i>
        <div>
          <h3>{{ t("system.deleteAccount") }}</h3>
          <p>{{ t("system.deleteAccountHint") }}</p>
        </div>
      </div>

      <form class="system-form" @submit.prevent="deleteAccount">
        <label class="field">
          <span>{{ t("system.deleteConfirmation", { email: accountEmail }) }}</span>
          <input v-model="deleteForm.confirmation" class="input" autocomplete="off" required />
        </label>
        <label class="field">
          <span>{{ t("system.currentPassword") }}</span>
          <PasswordInput
            v-model="deleteForm.password"
            class="input"
            autocomplete="current-password"
            required
          />
        </label>
        <button class="btn btn-ghost danger system-submit" type="submit" :disabled="authStore.isLoading || !canDeleteAccount">
          <i class="pi pi-trash"></i>
          {{ t("system.deleteAccountAction") }}
        </button>
      </form>
    </section>
  </section>
</template>
