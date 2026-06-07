<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/infra/store";
import { useAuthFlow } from "@/features/auth/model/useAuthFlow";
import LocaleSwitcher from "@/shared/ui/LocaleSwitcher.vue";
import VerificationCodeInput from "@/shared/ui/VerificationCodeInput.vue";
import PasswordInput from "@/shared/ui/PasswordInput.vue";

const authStore = useAuthStore();
const { t } = useI18n();
const flow = useAuthFlow();

const steps = [
  { key: "account", label: "auth.steps.account", icon: "pi pi-user" },
  { key: "password", label: "auth.steps.password", icon: "pi pi-key" },
  { key: "code", label: "auth.steps.code", icon: "pi pi-shield" },
  { key: "done", label: "auth.steps.done", icon: "pi pi-check" },
];
</script>

<template>
  <section class="auth-screen">
    <main class="auth-flow" aria-live="polite">
      <nav v-if="flow.showRegistrationSteps.value" class="auth-stepper" aria-label="Authentication steps">
        <span
          v-for="step in steps"
          :key="step.key"
          :class="{ active: flow.activeStep.value === step.key }"
          :aria-label="t(step.label)"
          :title="t(step.label)"
        >
          <i :class="step.icon" aria-hidden="true"></i>
          <span class="visually-hidden">{{ t(step.label) }}</span>
        </span>
      </nav>

      <div class="auth-title-row">
        <button
          v-if="flow.mode.value !== 'identifier' && flow.mode.value !== 'name' && flow.mode.value !== 'register'"
          class="icon-button quiet"
          type="button"
          :aria-label="t('common.back')"
          @click="flow.showIdentifierStep"
        >
          <i class="pi pi-arrow-left"></i>
        </button>
        <h1>{{ flow.title.value }}</h1>
      </div>

      <form v-if="flow.mode.value === 'identifier'" class="account-form" @submit.prevent="flow.continueToPassword">
        <label class="field">
          <span>{{ t("auth.emailOrUsername") }}</span>
          <input v-model="flow.loginIdentifier.value" class="input xl" autocomplete="username" required autofocus />
          <span v-if="flow.loginIdentifier.value && flow.identifierError.value" class="validation-message text-danger">
            {{ flow.identifierError.value }}
          </span>
          <span v-else-if="flow.fieldErrors.value.identifier" class="validation-message text-danger">
            {{ flow.fieldErrors.value.identifier }}
          </span>
        </label>
        <div class="auth-actions split">
          <button class="link-button" type="button" @click="flow.mode.value = 'register'">
            {{ t("auth.createAccount") }}
          </button>
          <button class="btn btn-primary" type="submit" :disabled="flow.isLookupLoading.value || Boolean(flow.identifierError.value)">
            {{ t("common.continue") }}
          </button>
        </div>
        <button class="link-button subtle-link" type="button" @click="flow.showForgotStep">
          {{ t("auth.forgotPassword") }}
        </button>
      </form>

      <form v-else-if="flow.mode.value === 'password'" class="account-form" @submit.prevent="flow.login">
        <div class="account-chip">
          <img v-if="flow.accountAvatarUrl.value" class="account-chip-avatar" :src="flow.accountAvatarUrl.value" alt="" />
          <i v-else class="pi pi-user"></i>
          <span>{{ flow.accountDisplayName.value }}</span>
        </div>
        <label class="field">
          <span>{{ t("auth.password") }}</span>
          <PasswordInput
            v-model="flow.loginPassword.value"
            class="input xl"
            autocomplete="current-password"
            required
            autofocus
          />
          <span v-if="flow.fieldErrors.value.password" class="validation-message text-danger">
            {{ flow.fieldErrors.value.password }}
          </span>
        </label>
        <div class="auth-actions split">
          <button class="link-button" type="button" @click="flow.showForgotStep">
            {{ t("auth.forgotPassword") }}
          </button>
          <button class="btn btn-primary" type="submit" :disabled="authStore.isLoading">
            {{ t("auth.signIn") }}
          </button>
        </div>
      </form>

      <form v-else-if="flow.mode.value === 'register'" class="account-form" @submit.prevent="flow.register">
        <label class="field">
          <span>{{ t("auth.username") }}</span>
          <div class="input-wrapper">
            <input v-model="flow.registerForm.value.username" class="input xl" autocomplete="username" required />
            <span v-if="flow.isCheckingUsername.value" class="input-suffix">
              <i class="pi pi-spinner pi-spin"></i>
            </span>
            <span
              v-else-if="flow.usernameCheckTouched.value && flow.registerForm.value.username"
              class="input-suffix"
              :class="flow.isUsernameTaken.value ? 'text-danger' : 'text-success'"
            >
              <i :class="flow.isUsernameTaken.value ? 'pi pi-times' : 'pi pi-check'"></i>
            </span>
          </div>
          <span v-if="flow.registerForm.value.username && flow.registrationErrors.value.username" class="validation-message text-danger">
            {{ flow.registrationErrors.value.username }}
          </span>
          <span v-else-if="flow.fieldErrors.value.username" class="validation-message text-danger">
            {{ flow.fieldErrors.value.username }}
          </span>
          <span
            v-if="flow.usernameCheckTouched.value && flow.registerForm.value.username && flow.isUsernameTaken.value"
            class="validation-message text-danger"
          >
            {{ t("auth.usernameTaken") }}
          </span>
          <span
            v-else-if="
              flow.usernameCheckTouched.value &&
              flow.registerForm.value.username &&
              !flow.isCheckingUsername.value &&
              !flow.isUsernameTaken.value
            "
            class="validation-message text-success"
          >
            {{ t("auth.usernameAvailable") }}
          </span>
        </label>
        <label class="field">
          <span>{{ t("auth.email") }}</span>
          <input v-model="flow.registerForm.value.email" class="input xl" type="email" autocomplete="email" required />
          <span v-if="flow.registerForm.value.email && flow.registrationErrors.value.email" class="validation-message text-danger">
            {{ flow.registrationErrors.value.email }}
          </span>
          <span v-else-if="flow.fieldErrors.value.email" class="validation-message text-danger">
            {{ flow.fieldErrors.value.email }}
          </span>
        </label>
        <label class="field">
          <span>{{ t("auth.password") }}</span>
          <PasswordInput
            v-model="flow.registerForm.value.password"
            class="input xl"
            autocomplete="new-password"
            required
            minlength="8"
          />
          <span v-if="flow.fieldErrors.value.password" class="validation-message text-danger">
            {{ flow.fieldErrors.value.password }}
          </span>
        </label>
        <label class="field">
          <span>{{ t("auth.confirmPassword") }}</span>
          <PasswordInput
            v-model="flow.registerForm.value.confirmPassword"
            class="input xl"
            autocomplete="new-password"
            required
            minlength="8"
            :aria-invalid="flow.registerPasswordMismatch.value"
          />
        </label>
        <div class="password-requirements" aria-live="polite">
          <span class="validation-message" :class="flow.registerPasswordValid.value ? 'text-success' : 'text-danger'">
            <i :class="flow.registerPasswordValid.value ? 'pi pi-check' : 'pi pi-times'"></i>
            {{ t("errors.VALIDATION_PASSWORD_TOO_SHORT") }}
          </span>
          <span class="validation-message" :class="flow.registerPasswordsMatch.value ? 'text-success' : 'text-danger'">
            <i :class="flow.registerPasswordsMatch.value ? 'pi pi-check' : 'pi pi-times'"></i>
            {{ t("auth.passwordsMatchRequirement") }}
          </span>
        </div>
        <div class="auth-actions split">
          <button class="link-button" type="button" @click="flow.showIdentifierStep">
            {{ t("auth.backToSignIn") }}
          </button>
          <button
            class="btn btn-primary"
            type="submit"
            :disabled="
              authStore.isLoading ||
              flow.isCheckingUsername.value ||
              flow.isUsernameTaken.value ||
              !flow.canRegister.value
            "
          >
            {{ t("auth.sendCode") }}
          </button>
        </div>
      </form>

      <form v-else-if="flow.mode.value === 'verify'" class="account-form" @submit.prevent="flow.confirmPublicVerification">
        <div class="account-chip">
          <img v-if="flow.accountAvatarUrl.value" class="account-chip-avatar" :src="flow.accountAvatarUrl.value" alt="" />
          <i v-else class="pi pi-envelope"></i>
          <span>{{ flow.accountDisplayName.value }}</span>
        </div>
        <label class="field">
          <span>{{ t("auth.verificationCode") }}</span>
          <VerificationCodeInput
            v-model="flow.publicVerificationCode.value"
            :autofocus="true"
            :error="
              flow.fieldErrors.value.code ||
              (flow.publicVerificationCode.value && !/^\d{6}$/.test(flow.publicVerificationCode.value)
                ? t('errors.VALIDATION_INVALID_CODE')
                : '')
            "
          />
        </label>
        <button class="btn btn-primary full" type="submit" :disabled="authStore.isLoading || !/^\d{6}$/.test(flow.publicVerificationCode.value)">
          {{ t("auth.confirmEmail") }}
        </button>
      </form>

      <form v-else-if="flow.mode.value === 'confirm'" class="account-form" @submit.prevent="flow.confirmRegistration">
        <div class="account-chip">
          <i class="pi pi-envelope"></i>
          <span>{{ flow.accountDisplayName.value }}</span>
        </div>
        <label class="field">
          <span>{{ t("auth.verificationCode") }}</span>
          <VerificationCodeInput
            v-model="flow.registrationCode.value"
            :autofocus="true"
            :error="
              flow.fieldErrors.value.code ||
              (flow.registrationCode.value && !/^\d{6}$/.test(flow.registrationCode.value)
                ? t('errors.VALIDATION_INVALID_CODE')
                : '')
            "
          />
        </label>
        <button class="btn btn-primary full" type="submit" :disabled="authStore.isLoading || !/^\d{6}$/.test(flow.registrationCode.value)">
          {{ t("auth.confirmEmail") }}
        </button>
        <div class="auth-actions center">
          <button class="link-button" type="button" :disabled="authStore.isLoading" @click="flow.resendRegistrationCode">
            {{ t("auth.resendCode") }}
          </button>
          <button class="link-button" type="button" @click="flow.mode.value = 'register'">
            {{ t("auth.editRegistration") }}
          </button>
        </div>
      </form>

      <form v-else-if="flow.mode.value === 'name'" class="account-form" @submit.prevent="flow.completeNameStep">
        <div class="account-chip">
          <img v-if="flow.accountAvatarUrl.value" class="account-chip-avatar" :src="flow.accountAvatarUrl.value" alt="" />
          <i v-else class="pi pi-check-circle"></i>
          <span>{{ authStore.currentUser?.username }}</span>
        </div>
        <label class="field">
          <span>{{ t("auth.firstName") }}</span>
          <input v-model="flow.nameForm.value.firstName" class="input xl" autocomplete="given-name" required />
        </label>
        <label class="field">
          <span>{{ t("auth.lastName") }}</span>
          <input v-model="flow.nameForm.value.lastName" class="input xl" autocomplete="family-name" required />
        </label>
        <button class="btn btn-primary full" type="submit" :disabled="authStore.isLoading">
          {{ t("common.continue") }}
        </button>
      </form>

      <form v-else-if="flow.mode.value === 'forgot'" class="account-form" @submit.prevent="flow.forgotPassword">
        <label class="field">
          <span>{{ t("auth.emailOrUsername") }}</span>
          <input v-model="flow.forgotIdentifier.value" class="input xl" autocomplete="username" required autofocus />
          <span v-if="flow.forgotIdentifier.value && flow.forgotIdentifierError.value" class="validation-message text-danger">
            {{ flow.forgotIdentifierError.value }}
          </span>
          <span v-else-if="flow.fieldErrors.value.identifier" class="validation-message text-danger">
            {{ flow.fieldErrors.value.identifier }}
          </span>
        </label>
        <button
          class="btn btn-primary full"
          type="submit"
          :disabled="authStore.isLoading || flow.isLookupLoading.value || Boolean(flow.forgotIdentifierError.value)"
        >
          {{ t("auth.sendCode") }}
        </button>
      </form>

      <form v-else class="account-form" @submit.prevent="flow.submitResetPassword">
        <div class="account-chip">
          <img v-if="flow.accountAvatarUrl.value" class="account-chip-avatar" :src="flow.accountAvatarUrl.value" alt="" />
          <i v-else class="pi pi-user"></i>
          <span>{{ flow.accountDisplayName.value }}</span>
        </div>
        <label class="field">
          <span>{{ t("auth.verificationCode") }}</span>
          <VerificationCodeInput
            v-model="flow.resetCode.value"
            :error="
              flow.fieldErrors.value.code ||
              (flow.resetCode.value && !/^\d{6}$/.test(flow.resetCode.value)
                ? t('errors.VALIDATION_INVALID_CODE')
                : '')
            "
          />
        </label>
        <label class="field">
          <span>{{ t("auth.newPassword") }}</span>
          <PasswordInput
            v-model="flow.resetPassword.value"
            class="input xl"
            autocomplete="new-password"
            required
            minlength="8"
          />
        </label>
        <button class="btn btn-primary full" type="submit" :disabled="authStore.isLoading || !/^\d{6}$/.test(flow.resetCode.value)">
          {{ t("auth.resetPassword") }}
        </button>
      </form>

      <p v-if="flow.authMessage.value" class="form-message">{{ flow.authMessage.value }}</p>
    </main>

    <LocaleSwitcher class="auth-locale" />
  </section>
</template>
