<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import { useProfileSocialStore } from "@/infra/store";
import type { NotificationAction } from "@/api/services/ProfileSocialService";

const emit = defineEmits<{
  back: [];
  message: [message: string, tone?: "success" | "error" | "warning" | "info"];
}>();

const { t } = useI18n();
const socialStore = useProfileSocialStore();
const busyKey = ref<string | null>(null);

onMounted(() => {
  socialStore.loadNotifications().catch((cause) => emit("message", apiErrorMessage(cause), "error"));
});

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

function actionLabel(action: NotificationAction) {
  if (action.kind === "accept_follow") return t("social.accept");
  if (action.kind === "reject_follow") return t("social.reject");
  return t("social.open");
}

function actionIcon(action: NotificationAction) {
  if (action.kind === "accept_follow") return "pi pi-check";
  if (action.kind === "reject_follow") return "pi pi-times";
  return "pi pi-external-link";
}

async function runAction(notificationId: string, action: NotificationAction) {
  busyKey.value = `${notificationId}:${action.kind}`;
  try {
    if (action.kind === "accept_follow") {
      await socialStore.acceptRequest(action.targetUserId);
      emit("message", t("social.requestAccepted"));
    } else if (action.kind === "reject_follow") {
      await socialStore.rejectRequest(action.targetUserId);
      emit("message", t("social.requestRejected"));
    } else {
      window.open(action.href, "_blank", "noopener,noreferrer");
    }
    await socialStore.markNotificationRead(notificationId);
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyKey.value = null;
  }
}

async function markRead(id: string) {
  busyKey.value = `${id}:read`;
  try {
    await socialStore.markNotificationRead(id);
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyKey.value = null;
  }
}

async function markAllRead() {
  busyKey.value = "read-all";
  try {
    await socialStore.markAllNotificationsRead();
    emit("message", t("social.notificationsRead"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyKey.value = null;
  }
}
</script>

<template>
  <section class="grid gap-4">
    <div class="grid gap-2">
      <PButton icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="-ml-2 justify-self-start" @click="emit('back')" />
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 min-h-[40px]">
        <h2 class="text-base font-bold m-0 text-[var(--text)] truncate">{{ t("social.notifications") }}</h2>
        <PButton
          icon="pi pi-check-circle"
          :label="t('social.markAllRead')"
          variant="text"
          severity="secondary"
          size="small"
          :loading="busyKey === 'read-all'"
          :disabled="!socialStore.notifications.items.length"
          @click="markAllRead"
        />
      </div>
    </div>

    <div class="grid gap-1.5">
      <div v-if="socialStore.notifications.isLoading" class="p-9 text-center text-sm text-[var(--muted)] bg-[var(--surface)] rounded-xl">
        <i class="pi pi-spinner pi-spin mr-2"></i>{{ t("common.loading") }}
      </div>
      <div v-else-if="!socialStore.notifications.items.length" class="p-9 text-center text-sm text-[var(--muted)] bg-[var(--surface)] rounded-xl">
        {{ t("social.noNotifications") }}
      </div>
      <article
        v-for="item in socialStore.notifications.items"
        v-else
        :key="item.id"
        class="grid gap-3 p-4 rounded-xl bg-[var(--surface)] border-0"
        :class="{ 'bg-[var(--surface-active)]': !item.isRead }"
      >
        <div class="flex items-start gap-3 min-w-0">
          <span class="w-10 h-10 rounded-lg bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)] shrink-0">
            <i :class="item.isRead ? 'pi pi-bell' : 'pi pi-bell-fill'"></i>
          </span>
          <div class="min-w-0 flex-1">
            <h3 class="m-0 text-[15px] font-bold text-[var(--text)] leading-tight">{{ item.title }}</h3>
            <p class="m-0 mt-1 text-sm text-[var(--muted)] leading-relaxed">{{ item.body }}</p>
            <small class="block mt-2 text-xs text-[var(--subtle)]">{{ formatDate(item.createdAt) }}</small>
          </div>
        </div>

        <div class="flex flex-wrap items-center justify-end gap-2">
          <PButton
            v-for="action in item.metadata.actions"
            :key="action.kind + ('targetUserId' in action ? action.targetUserId : 'href' in action ? action.href : '')"
            :icon="actionIcon(action)"
            :label="actionLabel(action)"
            size="small"
            :severity="action.kind === 'reject_follow' ? 'secondary' : undefined"
            :variant="action.kind === 'reject_follow' ? 'text' : undefined"
            :loading="busyKey === `${item.id}:${action.kind}`"
            @click="runAction(item.id, action)"
          />
          <PButton
            v-if="!item.isRead"
            icon="pi pi-check"
            :label="t('social.markRead')"
            variant="text"
            severity="secondary"
            size="small"
            :loading="busyKey === `${item.id}:read`"
            @click="markRead(item.id)"
          />
        </div>
      </article>
    </div>

    <div v-if="socialStore.notifications.totalCount > socialStore.notifications.limit" class="flex items-center justify-between gap-2">
      <PButton
        :label="t('social.previous')"
        icon="pi pi-angle-left"
        variant="text"
        severity="secondary"
        size="small"
        :disabled="socialStore.notifications.page <= 1 || socialStore.notifications.isLoading"
        @click="socialStore.loadNotifications(socialStore.notifications.page - 1)"
      />
      <span class="text-xs font-bold text-[var(--muted)]">{{ socialStore.notifications.page }}</span>
      <PButton
        :label="t('social.next')"
        icon="pi pi-angle-right"
        icon-pos="right"
        variant="text"
        severity="secondary"
        size="small"
        :disabled="socialStore.notifications.page * socialStore.notifications.limit >= socialStore.notifications.totalCount || socialStore.notifications.isLoading"
        @click="socialStore.loadNotifications(socialStore.notifications.page + 1)"
      />
    </div>
  </section>
</template>
