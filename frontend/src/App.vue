<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useAuthStore, useUIStore } from "@/infra/store";
import AuthScreen from "@/features/auth/ui/AuthScreen.vue";

const authStore = useAuthStore();
const uiStore = useUIStore();
const isBooting = ref(true);

onMounted(async () => {
  uiStore.init();
  try {
    await authStore.initAuth();
    if (authStore.isAuthenticated) {
      await authStore.fetchSessions().catch(() => undefined);
    }
  } finally {
    isBooting.value = false;
  }
});
</script>

<template>
  <PToast />
  <div v-if="isBooting" class="min-h-screen bg-[var(--bg)] grid place-items-center gap-3 text-[var(--muted)]">
    <div class="w-8.5 h-8.5 rounded-full border-3 border-transparent border-t-[var(--text)] animate-spin" aria-hidden="true"></div>
    <span class="text-sm font-medium">{{ $t("common.loading") }}</span>
  </div>

  <AuthScreen v-else-if="!authStore.isAuthenticated || authStore.isCompletingRegistrationProfile" />

  <router-view v-else />
</template>
