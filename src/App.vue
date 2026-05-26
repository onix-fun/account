<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore, useDeviceStore, useTemplateStore, useAnalyticsStore } from "@/infra/store";
import AppLayout from "@/infra/navigation/layouts/AppLayout.vue";

const router = useRouter();
const authStore = useAuthStore();
const deviceStore = useDeviceStore();
const templateStore = useTemplateStore();
const analyticsStore = useAnalyticsStore();

const authMode = ref<"login" | "register" | "confirm" | "name" | "forgot" | "reset">("login");
const authMessage = ref("");
const loginIdentifier = ref("");
const loginPassword = ref("");
const registerForm = ref({
    email: "",
    username: "",
    password: "",
});
const nameForm = ref({
    firstName: "",
    lastName: "",
});
const pendingRegistrationEmail = ref("");
const registrationCode = ref("");
const forgotIdentifier = ref("");
const resetIdentifier = ref("");
const resetCode = ref("");
const resetPassword = ref("");
const isBooting = ref(false);

const bootstrap = async () => {
    if (!authStore.isAuthenticated) return;
    isBooting.value = true;
    try {
        await Promise.all([templateStore.fetchTemplates(), deviceStore.fetchConsumers(), analyticsStore.checkHealth()]);
        await Promise.all([
            deviceStore.hydrateLastEvents(),
            ...deviceStore.consumers.map((consumer) => templateStore.ensureTemplateDetail(consumer.templateId)),
        ]);
    } finally {
        isBooting.value = false;
    }
};

onMounted(async () => {
    await authStore.initAuth();
    await bootstrap();
});

const login = async () => {
    authMessage.value = "";
    try {
        await authStore.login(loginIdentifier.value, loginPassword.value);
        await bootstrap();
        await router.push("/");
    } catch {
        authMessage.value = authStore.error || "Login failed";
    }
};

const register = async () => {
    authMessage.value = "";
    try {
        const response = await authStore.register(registerForm.value);
        pendingRegistrationEmail.value = response.email;
        registrationCode.value = "";
        authMode.value = "confirm";
        authMessage.value = "Verification code sent.";
    } catch {
        authMessage.value = authStore.error || "Registration failed";
    }
};

const confirmRegistration = async () => {
    authMessage.value = "";
    try {
        await authStore.confirmRegistration(pendingRegistrationEmail.value, registrationCode.value);
        nameForm.value = { firstName: "", lastName: "" };
        authMode.value = "name";
        authMessage.value = "Email confirmed.";
    } catch {
        authMessage.value = authStore.error || "Confirmation failed";
    }
};

const completeNameStep = async () => {
    authMessage.value = "";
    try {
        await authStore.updateProfile(nameForm.value);
        authMode.value = "login";
        await bootstrap();
        await router.push("/");
    } catch {
        authMessage.value = authStore.error || "Profile update failed";
    }
};

const resendRegistrationCode = async () => {
    authMessage.value = "";
    try {
        const response = await authStore.resendRegistrationCode(
            pendingRegistrationEmail.value || registerForm.value.email,
        );
        pendingRegistrationEmail.value = response.email;
        authMessage.value = "Verification code resent.";
    } catch {
        authMessage.value = authStore.error || "Could not resend verification code";
    }
};

const forgotPassword = async () => {
    authMessage.value = "";
    try {
        await authStore.forgotPassword(forgotIdentifier.value);
        resetIdentifier.value = forgotIdentifier.value;
        resetCode.value = "";
        resetPassword.value = "";
        authMessage.value = "If the account exists, a reset code was sent.";
        authMode.value = "reset";
    } catch {
        authMessage.value = authStore.error || "Password reset request failed";
    }
};

const submitResetPassword = async () => {
    authMessage.value = "";
    try {
        await authStore.resetPassword(resetIdentifier.value, resetCode.value, resetPassword.value);
        authMode.value = "login";
        authMessage.value = "Password updated. Sign in with the new password.";
    } catch {
        authMessage.value = authStore.error || "Password reset failed";
    }
};
</script>

<template>
    <section v-if="!authStore.isAuthenticated || authMode === 'name'" class="login-screen">
        <div class="login-panel">
            <div class="brand-mark">S</div>
            <h1>
                {{
                    authMode === "register" || authMode === "confirm" || authMode === "name"
                        ? "Create your Sparrow Account"
                        : "Sparrow"
                }}
            </h1>
            <p>{{ authMode === "login" ? "IoT operations console" : "One account for Sparrow services" }}</p>
            <div v-if="authMode === 'register' || authMode === 'confirm' || authMode === 'name'" class="auth-steps">
                <span :class="{ active: authMode === 'register', done: authMode === 'confirm' || authMode === 'name' }"
                    >Account</span
                >
                <span :class="{ active: authMode === 'confirm', done: authMode === 'name' }">Verify</span>
                <span :class="{ active: authMode === 'name' }">Profile</span>
            </div>
            <div v-if="authMode !== 'name'" class="auth-tabs">
                <button type="button" :class="{ active: authMode === 'login' }" @click="authMode = 'login'">
                    Sign in
                </button>
                <button type="button" :class="{ active: authMode === 'register' }" @click="authMode = 'register'">
                    Register
                </button>
            </div>

            <form v-if="authMode === 'login'" class="login-form" @submit.prevent="login">
                <label>
                    Email or username
                    <input v-model="loginIdentifier" autocomplete="username" required />
                </label>
                <label>
                    Password
                    <input v-model="loginPassword" type="password" autocomplete="current-password" required />
                </label>
                <button class="btn btn-primary" type="submit" :disabled="authStore.isLoading">Sign in</button>
                <button class="link-button" type="button" @click="authMode = 'forgot'">Forgot password?</button>
            </form>

            <form v-else-if="authMode === 'register'" class="login-form" @submit.prevent="register">
                <label>
                    Username
                    <input v-model="registerForm.username" autocomplete="username" required />
                </label>
                <label>
                    Email
                    <input v-model="registerForm.email" type="email" autocomplete="email" required />
                </label>
                <label>
                    Password
                    <input
                        v-model="registerForm.password"
                        type="password"
                        autocomplete="new-password"
                        required
                        minlength="8"
                    />
                </label>
                <button class="btn btn-primary" type="submit" :disabled="authStore.isLoading">
                    <i class="pi pi-envelope"></i>
                    Send verification code
                </button>
            </form>

            <form v-else-if="authMode === 'confirm'" class="login-form" @submit.prevent="confirmRegistration">
                <div class="auth-account-chip">
                    <i class="pi pi-envelope"></i>
                    <span>{{ pendingRegistrationEmail }}</span>
                </div>
                <label>
                    Verification code
                    <input
                        v-model="registrationCode"
                        inputmode="numeric"
                        pattern="[0-9]{6}"
                        autocomplete="one-time-code"
                        required
                    />
                </label>
                <button class="btn btn-primary" type="submit" :disabled="authStore.isLoading">
                    <i class="pi pi-check"></i>
                    Confirm email
                </button>
                <div class="auth-inline-actions">
                    <button
                        class="link-button"
                        type="button"
                        :disabled="authStore.isLoading"
                        @click="resendRegistrationCode"
                    >
                        Resend code
                    </button>
                    <button class="link-button" type="button" @click="authMode = 'register'">Edit registration</button>
                </div>
            </form>

            <form v-else-if="authMode === 'name'" class="login-form" @submit.prevent="completeNameStep">
                <div class="auth-account-chip">
                    <i class="pi pi-check-circle"></i>
                    <span>{{ authStore.currentUser?.email }}</span>
                </div>
                <label>
                    First name
                    <input v-model="nameForm.firstName" autocomplete="given-name" required />
                </label>
                <label>
                    Last name
                    <input v-model="nameForm.lastName" autocomplete="family-name" required />
                </label>
                <button class="btn btn-primary" type="submit" :disabled="authStore.isLoading">
                    <i class="pi pi-arrow-right"></i>
                    Continue
                </button>
            </form>

            <form v-else-if="authMode === 'forgot'" class="login-form" @submit.prevent="forgotPassword">
                <label>
                    Email or username
                    <input v-model="forgotIdentifier" autocomplete="username" required />
                </label>
                <button class="btn btn-primary" type="submit" :disabled="authStore.isLoading">
                    <i class="pi pi-envelope"></i>
                    Send reset code
                </button>
                <button class="link-button" type="button" @click="authMode = 'login'">Back to sign in</button>
            </form>

            <form v-else class="login-form" @submit.prevent="submitResetPassword">
                <label>
                    Email or username
                    <input v-model="resetIdentifier" autocomplete="username" required />
                </label>
                <label>
                    Reset code
                    <input
                        v-model="resetCode"
                        inputmode="numeric"
                        pattern="[0-9]{6}"
                        autocomplete="one-time-code"
                        required
                    />
                </label>
                <label>
                    New password
                    <input v-model="resetPassword" type="password" autocomplete="new-password" required minlength="8" />
                </label>
                <button class="btn btn-primary" type="submit" :disabled="authStore.isLoading">
                    <i class="pi pi-key"></i>
                    Reset password
                </button>
                <button class="link-button" type="button" @click="authMode = 'login'">Back to sign in</button>
            </form>
            <span v-if="authMessage" class="login-note">{{ authMessage }}</span>
        </div>
    </section>

    <div v-else-if="isBooting" class="boot-screen">
        <div class="spinner"></div>
        <span>Connecting to Sparrow services</span>
    </div>

    <AppLayout v-else>
        <router-view />
    </AppLayout>
</template>
