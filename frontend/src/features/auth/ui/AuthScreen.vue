<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/shared/model/store";
import { useAuthFlow } from "@/features/auth/model/useAuthFlow";
import LocaleSwitcher from "@/shared/ui/LocaleSwitcher.vue";
import VerificationCodeInput from "@/shared/ui/VerificationCodeInput.vue";
import PasswordInput from "@/shared/ui/PasswordInput.vue";
import QrLoginScanner from "@/features/auth/ui/QrLoginScanner.vue";
import { apiErrorMessage } from "@/shared/api/client";
import { computed, ref } from "vue";
import { useRoute } from "vue-router";
import { trustedRedirectUrl } from "@/shared/lib/trustedRedirect";

const authStore = useAuthStore();
const { t } = useI18n();
const route = useRoute();
const flow = useAuthFlow();
const isQrScannerOpen = ref(false);
const qrError = ref("");
const externalBackUrl = computed(() => trustedRedirectUrl(route.query.redirect));
const hasFlowBack = computed(() => flow.mode.value !== "identifier" && flow.mode.value !== "name" && flow.mode.value !== "register");

async function submitQrLogin(payload: { scanToken?: string; manualCode?: string }) {
  qrError.value = "";
  try {
    await flow.qrLogin(payload);
    isQrScannerOpen.value = false;
  } catch (cause) {
    qrError.value = apiErrorMessage(cause);
  }
}

const steps = [
  { key: "account", label: "auth.steps.account", icon: "pi pi-user" },
  { key: "password", label: "auth.steps.password", icon: "pi pi-key" },
  { key: "code", label: "auth.steps.code", icon: "pi pi-shield" },
  { key: "done", label: "auth.steps.done", icon: "pi pi-check" },
];
</script>

<template>
  <section class="min-h-screen grid place-items-center p-4 sm:p-8 relative">
    <a
      v-if="externalBackUrl && !hasFlowBack"
      :href="externalBackUrl"
      class="absolute left-4 top-4 sm:left-8 sm:top-8 inline-flex items-center gap-2 min-h-10 rounded-lg px-3 text-sm font-bold text-[var(--muted)] hover:text-[var(--text)] hover:bg-[var(--surface-muted)] transition-colors"
      :aria-label="t('common.back')"
    >
      <i class="pi pi-arrow-left" aria-hidden="true"></i>
      <span>{{ t("common.back") }}</span>
    </a>

    <UiSurface as="main" class="w-full max-w-[420px] grid gap-6" aria-live="polite">
      <nav v-if="flow.showRegistrationSteps.value" class="flex items-center justify-between gap-3 relative" aria-label="Authentication steps">
        <div class="absolute left-5 right-5 top-[19px] h-px bg-[var(--surface-muted)] z-0"></div>
        <span
          v-for="step in steps"
          :key="step.key"
          class="w-10 h-10 rounded-full bg-[var(--surface)] flex items-center justify-center relative z-10 text-sm transition-colors shadow-sm"
          :class="flow.activeStep.value === step.key ? 'bg-[var(--text)] text-white' : 'text-[var(--subtle)]'"
          :aria-label="t(step.label)"
          :title="t(step.label)"
        >
          <i :class="step.icon" aria-hidden="true"></i>
          <span class="visually-hidden">{{ t(step.label) }}</span>
        </span>
      </nav>

      <div class="flex items-center gap-3 min-h-[46px]">
        <PButton
          v-if="hasFlowBack"
          icon="pi pi-arrow-left"
          variant="text"
          severity="secondary"
          class="w-9 h-9"
          :aria-label="t('common.back')"
          @click="flow.showIdentifierStep"
        />
        <h1 class="text-3xl font-bold m-0 leading-tight text-[var(--text)]">{{ flow.title.value }}</h1>
      </div>

      <form v-if="flow.mode.value === 'identifier'" class="grid gap-4" @submit.prevent="flow.continueToPassword">
        <UiField :label="t('auth.emailOrUsername')">
          <PInputText v-model="flow.loginIdentifier.value" autocomplete="username" required autofocus class="w-full" />
          <PMessage v-if="flow.loginIdentifier.value && flow.identifierError.value" severity="error" variant="simple">
            {{ flow.identifierError.value }}
          </PMessage>
          <PMessage v-else-if="flow.fieldErrors.value.identifier" severity="error" variant="simple">
            {{ flow.fieldErrors.value.identifier }}
          </PMessage>
        </UiField>
        <div class="flex items-center justify-between gap-3 pt-1">
          <PButton :label="t('auth.createAccount')" variant="text" severity="secondary" @click="flow.mode.value = 'register'" />
          <PButton type="submit" :label="t('common.continue')" :disabled="flow.isLookupLoading.value || Boolean(flow.identifierError.value)" :loading="flow.isLookupLoading.value" />
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <PButton :label="t('auth.qr.signInTitle')" icon="pi pi-qrcode" variant="text" severity="secondary" size="small" class="-ml-2" @click="isQrScannerOpen = true" />
          <PButton :label="t('auth.forgotPassword')" variant="text" severity="secondary" size="small" class="-ml-2" @click="flow.showForgotStep" />
        </div>
      </form>

      <form v-else-if="flow.mode.value === 'password'" class="grid gap-4" @submit.prevent="flow.login">
        <div class="ui-chip w-fit">
          <img v-if="flow.accountAvatarUrl.value" class="w-6 h-6 rounded-full object-cover shrink-0" :src="flow.accountAvatarUrl.value" alt="" />
          <i v-else class="pi pi-user"></i>
          <span class="truncate">{{ flow.accountDisplayName.value }}</span>
        </div>
        <UiField :label="t('auth.password')">
          <PasswordInput
            v-model="flow.loginPassword.value"
            autocomplete="current-password"
            required
            autofocus
          />
          <PMessage v-if="flow.fieldErrors.value.password" severity="error" variant="simple">
            {{ flow.fieldErrors.value.password }}
          </PMessage>
        </UiField>
        <div class="flex items-center justify-between gap-3 pt-1">
          <PButton :label="t('auth.forgotPassword')" variant="text" severity="secondary" @click="flow.showForgotStep" />
          <PButton type="submit" :label="t('auth.signIn')" :disabled="authStore.isLoading" :loading="authStore.isLoading" />
        </div>
      </form>

      <form v-else-if="flow.mode.value === 'register'" class="grid gap-4" @submit.prevent="flow.register">
        <UiField :label="t('auth.username')">
          <div class="relative w-full">
            <PInputText v-model="flow.registerForm.value.username" autocomplete="username" required class="w-full" />
            <div class="absolute right-3 top-1/2 -translate-y-1/2 flex items-center">
              <i v-if="flow.isCheckingUsername.value" class="pi pi-spinner pi-spin text-[var(--muted)]"></i>
              <i v-else-if="flow.usernameCheckTouched.value && flow.registerForm.value.username" 
                 :class="[flow.isUsernameTaken.value ? 'pi pi-times text-[var(--danger)]' : 'pi pi-check text-[var(--success)]']"></i>
            </div>
          </div>
          <PMessage v-if="flow.registerForm.value.username && flow.registrationErrors.value.username" severity="error" variant="simple">
            {{ flow.registrationErrors.value.username }}
          </PMessage>
          <PMessage v-else-if="flow.fieldErrors.value.username" severity="error" variant="simple">
            {{ flow.fieldErrors.value.username }}
          </PMessage>
          <PMessage v-if="flow.usernameCheckTouched.value && flow.registerForm.value.username && flow.isUsernameTaken.value" severity="error" variant="simple">
            {{ t("auth.usernameTaken") }}
          </PMessage>
          <PMessage v-else-if="flow.usernameCheckTouched.value && flow.registerForm.value.username && !flow.isCheckingUsername.value && !flow.isUsernameTaken.value" severity="success" variant="simple">
            {{ t("auth.usernameAvailable") }}
          </PMessage>
        </UiField>

        <UiField :label="t('auth.email')">
          <PInputText v-model="flow.registerForm.value.email" type="email" autocomplete="email" required class="w-full" />
          <PMessage v-if="flow.registerForm.value.email && flow.registrationErrors.value.email" severity="error" variant="simple">
            {{ flow.registrationErrors.value.email }}
          </PMessage>
          <PMessage v-else-if="flow.fieldErrors.value.email" severity="error" variant="simple">
            {{ flow.fieldErrors.value.email }}
          </PMessage>
        </UiField>

        <UiField :label="t('auth.password')">
          <PasswordInput
            v-model="flow.registerForm.value.password"
            autocomplete="new-password"
            required
            minlength="8"
          />
          <PMessage v-if="flow.fieldErrors.value.password" severity="error" variant="simple">
            {{ flow.fieldErrors.value.password }}
          </PMessage>
        </UiField>

        <UiField :label="t('auth.confirmPassword')">
          <PasswordInput
            v-model="flow.registerForm.value.confirmPassword"
            autocomplete="new-password"
            required
            minlength="8"
            :aria-invalid="flow.registerPasswordMismatch.value"
          />
        </UiField>

        <div class="grid gap-1.5 pt-1" aria-live="polite">
          <div class="flex items-center gap-2 text-xs font-semibold transition-colors" :class="flow.registerPasswordValid.value ? 'text-[var(--success)]' : 'text-[var(--danger)]'">
            <i :class="flow.registerPasswordValid.value ? 'pi pi-check' : 'pi pi-times'"></i>
            {{ t("errors.VALIDATION_PASSWORD_TOO_SHORT") }}
          </div>
          <div class="flex items-center gap-2 text-xs font-semibold transition-colors" :class="flow.registerPasswordsMatch.value ? 'text-[var(--success)]' : 'text-[var(--danger)]'">
            <i :class="flow.registerPasswordsMatch.value ? 'pi pi-check' : 'pi pi-times'"></i>
            {{ t("auth.passwordsMatchRequirement") }}
          </div>
        </div>

        <div class="flex items-center justify-between gap-3 pt-2">
          <PButton :label="t('auth.backToSignIn')" variant="text" severity="secondary" @click="flow.showIdentifierStep" />
          <PButton
            type="submit"
            :label="t('auth.sendCode')"
            :disabled="authStore.isLoading || flow.isCheckingUsername.value || flow.isUsernameTaken.value || !flow.canRegister.value"
            :loading="authStore.isLoading"
          />
        </div>
      </form>

      <form v-else-if="flow.mode.value === 'verify' || flow.mode.value === 'confirm'" class="grid gap-4" @submit.prevent="flow.mode.value === 'verify' ? flow.confirmPublicVerification() : flow.confirmRegistration()">
        <div class="ui-chip w-fit">
          <img v-if="flow.accountAvatarUrl.value" class="w-6 h-6 rounded-full object-cover shrink-0" :src="flow.accountAvatarUrl.value" alt="" />
          <i v-else-if="flow.mode.value === 'verify'" class="pi pi-envelope"></i>
          <i v-else class="pi pi-user"></i>
          <span class="truncate">{{ flow.mode.value === 'confirm' ? flow.pendingRegistrationEmail.value : flow.accountDisplayName.value }}</span>
        </div>
        <UiField :label="t('auth.verificationCode')">
          <VerificationCodeInput
            v-if="flow.mode.value === 'verify'"
            v-model="flow.publicVerificationCode.value"
            :autofocus="true"
            :error="flow.fieldErrors.value.code"
          />
          <VerificationCodeInput
            v-else
            v-model="flow.registrationCode.value"
            :autofocus="true"
            :error="flow.fieldErrors.value.code"
          />
        </UiField>
        <PButton 
          type="submit" 
          class="w-full"
          :label="t('auth.confirmEmail')"
          :disabled="authStore.isLoading || !(flow.mode.value === 'verify' ? /^\d{6}$/.test(flow.publicVerificationCode.value) : /^\d{6}$/.test(flow.registrationCode.value))"
          :loading="authStore.isLoading"
        />
        <div v-if="flow.mode.value === 'confirm'" class="flex justify-center gap-3 pt-2">
          <PButton :label="t('auth.resendCode')" variant="text" severity="secondary" size="small" :disabled="authStore.isLoading" @click="flow.resendRegistrationCode" />
          <PButton :label="t('auth.editRegistration')" variant="text" severity="secondary" size="small" @click="flow.mode.value = 'register'" />
        </div>
      </form>

      <form v-else-if="flow.mode.value === 'name'" class="grid gap-4" @submit.prevent="flow.completeNameStep">
        <div class="ui-chip w-fit">
          <img v-if="flow.accountAvatarUrl.value" class="w-6 h-6 rounded-full object-cover shrink-0" :src="flow.accountAvatarUrl.value" alt="" />
          <i v-else class="pi pi-check-circle"></i>
          <span class="truncate">{{ authStore.currentUser?.username }}</span>
        </div>
        <UiField :label="t('auth.firstName')">
          <PInputText v-model="flow.nameForm.value.firstName" autocomplete="given-name" required class="w-full" />
        </UiField>
        <UiField :label="t('auth.lastName')">
          <PInputText v-model="flow.nameForm.value.lastName" autocomplete="family-name" required class="w-full" />
        </UiField>
        <PButton type="submit" class="w-full" :label="t('common.continue')" :loading="authStore.isLoading" />
      </form>

      <form v-else-if="flow.mode.value === 'forgot'" class="grid gap-4" @submit.prevent="flow.forgotPassword">
        <UiField :label="t('auth.emailOrUsername')">
          <PInputText v-model="flow.forgotIdentifier.value" autocomplete="username" required autofocus class="w-full" />
          <PMessage v-if="flow.forgotIdentifier.value && flow.forgotIdentifierError.value" severity="error" variant="simple">
            {{ flow.forgotIdentifierError.value }}
          </PMessage>
          <PMessage v-else-if="flow.fieldErrors.value.identifier" severity="error" variant="simple">
            {{ flow.fieldErrors.value.identifier }}
          </PMessage>
        </UiField>
        <PButton type="submit" class="w-full" :label="t('auth.sendCode')" :disabled="authStore.isLoading || flow.isLookupLoading.value || Boolean(flow.forgotIdentifierError.value)" :loading="authStore.isLoading" />
      </form>

      <form v-else class="grid gap-4" @submit.prevent="flow.submitResetPassword">
        <div class="ui-chip w-fit">
          <img v-if="flow.accountAvatarUrl.value" class="w-6 h-6 rounded-full object-cover shrink-0" :src="flow.accountAvatarUrl.value" alt="" />
          <i v-else class="pi pi-user"></i>
          <span class="truncate">{{ flow.accountDisplayName.value }}</span>
        </div>
        <UiField :label="t('auth.verificationCode')">
          <VerificationCodeInput v-model="flow.resetCode.value" :error="flow.fieldErrors.value.code" />
        </UiField>
        <UiField :label="t('auth.newPassword')">
          <PasswordInput v-model="flow.resetPassword.value" autocomplete="new-password" required minlength="8" />
        </UiField>
        <PButton type="submit" class="w-full" :label="t('auth.resetPassword')" :disabled="authStore.isLoading || !/^\d{6}$/.test(flow.resetCode.value)" :loading="authStore.isLoading" />
      </form>

      <p v-if="flow.authMessage.value" class="m-0 text-sm text-[var(--muted)] text-center">{{ flow.authMessage.value }}</p>
    </UiSurface>

    <LocaleSwitcher class="fixed left-1/2 bottom-[max(18px,env(safe-area-inset-bottom))] -translate-x-1/2" />
    <QrLoginScanner
      :visible="isQrScannerOpen"
      :loading="authStore.isLoading"
      :message="qrError"
      @close="isQrScannerOpen = false"
      @submit="submitQrLogin"
    />
  </section>
</template>
