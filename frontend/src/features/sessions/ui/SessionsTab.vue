<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/shared/api/client";
import type { AuthSession } from "@/shared/model/domain";
import { useAuthStore } from "@/shared/model/store";
import QrLoginDialog from "@/features/sessions/ui/QrLoginDialog.vue";

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "warning"];
}>();

const authStore = useAuthStore();
const { t } = useI18n();
const isLoadingSessions = ref(false);
const isQrDialogOpen = ref(false);

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

const onQrConsumed = async () => {
  isQrDialogOpen.value = false;
  await refreshSessions();
};

const forwardQrMessage = (message: string, tone?: "success" | "error" | "warning" | "info") => {
  emit("message", message, tone === "info" ? "success" : tone);
};

const sessionDevice = (session: AuthSession) => {
  const browser = sessionBrowser(session.userAgent);
  const platform = sessionPlatform(session.userAgent);
  if (browser && platform) return `${browser} · ${platform}`;
  return session.userAgent || session.deviceId || t("profile.unknownDevice");
};
const sessionBrowser = (userAgent?: string | null) => {
  if (!userAgent) return "";
  if (/Edg\//.test(userAgent)) return "Edge";
  if (/Firefox\//.test(userAgent)) return "Firefox";
  if (/Chrome\//.test(userAgent) && !/Chromium\//.test(userAgent)) return "Chrome";
  if (/Safari\//.test(userAgent) && /Version\//.test(userAgent)) return "Safari";
  return "";
};
const sessionPlatform = (userAgent?: string | null) => {
  if (!userAgent) return "";
  if (/iPad/.test(userAgent)) return "iPadOS";
  if (/iPhone/.test(userAgent)) return "iOS";
  if (/Android/.test(userAgent)) return "Android";
  if (/Mac OS X|Macintosh/.test(userAgent)) return "macOS";
  if (/Windows/.test(userAgent)) return "Windows";
  if (/Linux/.test(userAgent)) return "Linux";
  return "";
};
const sessionIcon = (session: AuthSession) => {
  const userAgent = session.userAgent || "";
  if (/iPad|Tablet/.test(userAgent)) return "pi pi-tablet";
  if (/Mobile|Android|iPhone/.test(userAgent)) return "pi pi-mobile";
  return "pi pi-desktop";
};
const formatDate = (value?: string | null) => (value ? new Date(value).toLocaleString() : t("common.unknown"));
</script>

<template>
  <section class="grid gap-4">
    <UiSectionHeader :title="t('profile.sessions')">
      <template #actions>
      <div class="ui-action-group w-full sm:w-auto">
        <PButton
          icon="pi pi-qrcode"
          :label="t('auth.qr.otherDeviceAction')"
          variant="text"
          severity="secondary"
          size="small"
          class="flex-1 sm:flex-initial"
          @click="isQrDialogOpen = true"
        />
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
      </template>
    </UiSectionHeader>

    <div class="ui-list">
      <div v-if="!authStore.sessions.length" class="ui-empty">
        {{ t("profile.noSessions") }}
      </div>
      <UiFlatRow
        v-for="session in authStore.sessions"
        :key="session.id"
        as="article"
        class="session-card"
        :active="session.isCurrent"
      >
        <div class="session-heading">
          <UiIconTile :tone="session.isCurrent ? 'success' : 'info'">
            <i :class="sessionIcon(session)"></i>
          </UiIconTile>
          <span class="session-title-wrap">
            <strong class="session-title">{{ sessionDevice(session) }}</strong>
            <span v-if="session.isCurrent" class="session-badge">{{ t("common.current") }}</span>
          </span>
        </div>

        <div class="session-meta">
          <div class="session-meta-item">
            <span>{{ t("profile.ipAddress") }}</span>
            <strong>{{ session.ipAddress || t("common.unknown") }}</strong>
          </div>
          <div class="session-meta-item">
            <span>{{ t("profile.lastActive") }}</span>
            <strong>{{ formatDate(session.lastUsedAt) }}</strong>
          </div>
          <div class="session-meta-item">
            <span>{{ t("profile.createdAt") }}</span>
            <strong>{{ formatDate(session.createdAt) }}</strong>
          </div>
          <div class="session-meta-item">
            <span>{{ t("profile.expires") }}</span>
            <strong>{{ formatDate(session.expiresAt) }}</strong>
          </div>
        </div>

        <div class="session-actions">
          <PButton
            v-if="session.isCurrent"
            :label="t('profile.logout')"
            icon="pi pi-sign-out"
            variant="text"
            severity="danger"
            size="small"
            @click="logoutCurrent"
          />
          <PButton
            v-else
            :label="t('profile.revoke')"
            icon="pi pi-times"
            variant="text"
            severity="danger"
            size="small"
            @click="revokeSession(session)"
          />
        </div>
      </UiFlatRow>
    </div>

    <QrLoginDialog
      :visible="isQrDialogOpen"
      @close="isQrDialogOpen = false"
      @consumed="onQrConsumed"
      @message="forwardQrMessage"
    />
  </section>
</template>

<style scoped>
.session-card {
  display: grid;
  gap: 11px;
  padding: 14px;
  border: 0;
  transition: background 0.16s ease, transform 0.16s ease;
}

.session-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.session-title-wrap {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-title {
  min-width: 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-badge {
  flex: 0 0 auto;
  padding: 3px 7px;
  border-radius: 999px;
  background: var(--text);
  color: var(--btn-primary-text);
  font-size: 11px;
  line-height: 1.2;
  font-weight: 800;
}

.session-meta {
  display: grid;
  gap: 6px;
}

.session-meta-item {
  min-width: 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.session-meta-item span {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.2;
  font-weight: 800;
}

.session-meta-item strong {
  min-width: 0;
  color: var(--text);
  font-size: 12px;
  line-height: 1.25;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-actions {
  justify-self: end;
}

@media (min-width: 640px) {
  .session-card {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 12px 16px;
  }

  .session-heading,
  .session-meta {
    grid-column: 1;
  }

  .session-actions {
    grid-column: 2;
    grid-row: 1 / span 2;
    align-self: center;
  }
}
</style>
