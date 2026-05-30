<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "@/infra/store";
import AppLayout from "@/infra/navigation/layouts/AppLayout.vue";
import { AuthService } from "@/api/services/AuthService";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const authMode = ref<"login" | "register" | "confirm" | "name" | "forgot" | "reset">("login");
const authMessage = ref("");
const loginIdentifier = ref("");
const loginPassword = ref("");
const registerForm = ref({
    email: "",
    username: "",
    password: "",
});

const isCheckingUsername = ref(false);
const isUsernameTaken = ref(false);
const usernameCheckTouched = ref(false);
let usernameTimeout: ReturnType<typeof setTimeout> | null = null;

watch(
    () => registerForm.value.username,
    (newVal) => {
        usernameCheckTouched.value = true;
        isCheckingUsername.value = false;
        isUsernameTaken.value = false;

        if (usernameTimeout) clearTimeout(usernameTimeout);

        const val = newVal.trim();
        if (!val) {
            usernameCheckTouched.value = false;
            return;
        }

        isCheckingUsername.value = true;
        usernameTimeout = setTimeout(async () => {
            try {
                const results = await AuthService.searchUsers(val);
                isUsernameTaken.value = results.some((u) => u.username.toLowerCase() === val.toLowerCase());
            } catch {
                // Ignore errors
            } finally {
                isCheckingUsername.value = false;
            }
        }, 500);
    },
);
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
    // We can add profile-specific bootstrapping here if needed
};

const handleAuthSuccess = async () => {
    const redirect = route.query.redirect;
    const redirectUrl = Array.isArray(redirect) ? redirect[0] : redirect;
    if (redirectUrl) {
        window.location.href = decodeURIComponent(String(redirectUrl));
    } else {
        await router.push("/");
    }
};

onMounted(async () => {
    await authStore.initAuth();
    if (authStore.isAuthenticated) {
        // We are already authenticated. Do not auto-redirect back on mount unless they just logged in.
        // Just bootstrap the app so the Settings view can render.
        await bootstrap();
    }
});

const login = async () => {
    authMessage.value = "";
    try {
        await authStore.login(loginIdentifier.value, loginPassword.value);
        await bootstrap();
        await handleAuthSuccess();
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
        await handleAuthSuccess();
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
                    <div class="input-wrapper">
                        <input v-model="registerForm.username" autocomplete="username" required />
                        <span v-if="isCheckingUsername" class="input-suffix">
                            <i class="pi pi-spinner pi-spin"></i>
                        </span>
                        <span
                            v-else-if="usernameCheckTouched && registerForm.username"
                            class="input-suffix"
                            :class="isUsernameTaken ? 'text-danger' : 'text-success'"
                        >
                            <i :class="isUsernameTaken ? 'pi pi-times' : 'pi pi-check'"></i>
                        </span>
                    </div>
                    <span
                        v-if="usernameCheckTouched && registerForm.username && isUsernameTaken"
                        class="validation-message text-danger"
                        >Username is already taken.</span
                    >
                    <span
                        v-else-if="
                            usernameCheckTouched && registerForm.username && !isCheckingUsername && !isUsernameTaken
                        "
                        class="validation-message text-success"
                        >Username is available.</span
                    >
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
                <button
                    class="btn btn-primary"
                    type="submit"
                    :disabled="authStore.isLoading || isCheckingUsername || isUsernameTaken || !registerForm.username"
                >
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
        <span>Loading...</span>
    </div>

    <AppLayout v-else>
        <router-view />
    </AppLayout>
</template>
