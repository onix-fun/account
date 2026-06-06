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
  <section class="tab-panel">
    <div class="section-toolbar">
      <h2>{{ t("profile.sessions") }}</h2>
      <div class="toolbar-actions">
        <button class="btn btn-ghost" type="button" :disabled="isLoadingSessions" @click="refreshSessions">
          <i class="pi pi-refresh"></i>
          {{ t("profile.refresh") }}
        </button>
        <button class="btn btn-ghost danger" type="button" @click="logoutAll">
          <i class="pi pi-sign-out"></i>
          {{ t("profile.logoutAll") }}
        </button>
      </div>
    </div>

    <div class="row-list">
      <div v-if="!authStore.sessions.length" class="empty-row">{{ t("profile.noSessions") }}</div>
      <article
        v-for="session in authStore.sessions"
        :key="session.id"
        class="data-row session-data-row"
        :class="{ selected: session.isCurrent }"
      >
        <i class="pi pi-desktop row-icon"></i>
        <div class="row-main">
          <strong>
            {{ sessionDevice(session) }}
            <span v-if="session.isCurrent" class="inline-muted">({{ t("common.current") }})</span>
          </strong>
          <small>
            {{ session.ipAddress || t("common.unknown") }} · {{ t("profile.lastActive") }}
            {{ formatDate(session.lastUsedAt) }}
          </small>
        </div>
        <span class="row-meta">{{ t("profile.expires") }} {{ formatDate(session.expiresAt) }}</span>
        <button v-if="session.isCurrent" class="btn btn-ghost danger" type="button" @click="logoutCurrent">
          {{ t("profile.logout") }}
        </button>
        <button v-else class="btn btn-ghost danger" type="button" @click="revokeSession(session)">
          {{ t("profile.revoke") }}
        </button>
      </article>
    </div>
  </section>
</template>
