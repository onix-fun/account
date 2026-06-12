<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import type { AuthSession } from "@/domain";
import { useAuthStore } from "@/infra/store";

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "warning"];
}>();

const authStore = useAuthStore();
const { t } = useI18n();
const isLoadingSessions = ref(false);

const refreshSessions = async () => {
  isLoadingSessions.value = true;
  try {
    await authStore.fetchSessions();
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isLoadingSessions.value = false;
  }
};

const revokeSession = async (session: AuthSession) => {
  try {
    await authStore.revokeSession(session.id);
    emit("message", t("profile.sessionRevoked"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  }
};

const logoutAll = async () => {
  try {
    await authStore.logoutAll();
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  }
};

const logoutCurrent = async () => {
  try {
    await authStore.logout();
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  }
};

const sessionDevice = (session: AuthSession) => session.userAgent || session.deviceId || t("profile.unknownDevice");
const formatDate = (value?: string | null) => (value ? new Date(value).toLocaleString() : t("common.unknown"));
</script>

<template>
  <section class="grid gap-4">
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 min-h-[40px]">
      <h2 class="text-base font-bold m-0 text-[var(--text)]">{{ t("profile.sessions") }}</h2>
      <div class="flex gap-2 w-full sm:w-auto">
        <PButton
          icon="pi pi-refresh"
          :label="t('profile.refresh')"
          variant="text"
          severity="secondary"
          size="small"
          class="flex-1 sm:flex-initial"
          :loading="isLoadingSessions"
          @click="refreshSessions"
        />
        <PButton
          icon="pi pi-sign-out"
          :label="t('profile.logoutAll')"
          variant="text"
          severity="danger"
          size="small"
          class="flex-1 sm:flex-initial"
          @click="logoutAll"
        />
      </div>
    </div>

    <div class="grid gap-1.5">
      <div v-if="!authStore.sessions.length" class="p-9 text-center text-sm text-[var(--muted)] bg-[var(--surface)] border border-[var(--surface-muted)] rounded-xl">
        {{ t("profile.noSessions") }}
      </div>
      <article
        v-for="session in authStore.sessions"
        :key="session.id"
        class="flex flex-col sm:flex-row sm:items-center gap-3.5 p-3.5 rounded-xl transition-colors bg-[var(--surface)] border-0"
        :class="{ '!bg-[var(--surface-active)]': session.isCurrent }"
      >
        <div class="w-10 h-10 rounded-lg bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)] shrink-0">
          <i class="pi pi-desktop text-lg"></i>
        </div>
        <div class="flex-1 min-w-0 grid gap-0.5">
          <strong class="text-sm font-bold text-[var(--text)] truncate">
            {{ sessionDevice(session) }}
            <span v-if="session.isCurrent" class="text-[var(--muted)] font-medium">({{ t("common.current") }})</span>
          </strong>
          <small class="text-[13px] text-[var(--muted)]">
            {{ session.ipAddress || t("common.unknown") }} · {{ t("profile.lastActive") }}
            {{ formatDate(session.lastUsedAt) }}
          </small>
        </div>
        <span class="text-[13px] text-[var(--muted)] whitespace-nowrap">{{ t("profile.expires") }} {{ formatDate(session.expiresAt) }}</span>
        <PButton
          v-if="session.isCurrent"
          :label="t('profile.logout')"
          variant="text"
          severity="danger"
          size="small"
          class="self-end sm:self-center"
          @click="logoutCurrent"
        />
        <PButton
          v-else
          :label="t('profile.revoke')"
          variant="text"
          severity="danger"
          size="small"
          class="self-end sm:self-center"
          @click="revokeSession(session)"
        />
      </article>
    </div>
  </section>
</template>
