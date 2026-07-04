<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import type { NotificationPrefs } from "@/api/services/ProfileSocialService";
import { useProfileSocialStore } from "@/infra/store";

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "warning"];
}>();

const { t } = useI18n();
const socialStore = useProfileSocialStore();
const savingKey = ref<string | null>(null);

const prefRows: Array<{ key: keyof NotificationPrefs; icon: string }> = [
  { key: "inAppSubscriptions", icon: "pi pi-user-plus" },
  { key: "inAppPublications", icon: "pi pi-send" },
  { key: "inAppAuthorMentions", icon: "pi pi-at" },
  { key: "inAppPostComments", icon: "pi pi-comments" },
  { key: "inAppNewStories", icon: "pi pi-bolt" },
];

onMounted(() => {
  socialStore.loadSettings().catch((cause) => emit("message", apiErrorMessage(cause), "error"));
});

async function setPrivacy(value: boolean) {
  savingKey.value = "privacy";
  try {
    await socialStore.setPrivacy(value);
    emit("message", t("social.settingsSaved"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    savingKey.value = null;
  }
}

async function setPref(key: keyof NotificationPrefs, value: boolean) {
  savingKey.value = key;
  try {
    await socialStore.setNotificationPref(key, value);
    emit("message", t("social.settingsSaved"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    savingKey.value = null;
  }
}
</script>

<template>
  <section class="grid gap-2">
    <div class="flex items-center justify-between gap-3 min-h-[40px] px-1">
      <h2 class="text-base font-bold m-0 text-[var(--text)]">{{ t("profile.settings") }}</h2>
    </div>

    <section class="bg-[var(--surface)] p-4 rounded-2xl flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div class="flex items-center gap-3.5 min-w-0">
        <div class="w-10 h-10 rounded-lg bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)] shrink-0">
          <i class="pi pi-lock text-lg"></i>
        </div>
        <div class="min-w-0">
          <h3 class="text-[15px] font-bold m-0 text-[var(--text)] leading-tight">{{ t("social.privateProfile") }}</h3>
          <p class="m-0 mt-1 text-xs text-[var(--muted)] leading-relaxed">{{ t("social.privateProfileHint") }}</p>
        </div>
      </div>
      <button class="profile-toggle" type="button" :aria-pressed="socialStore.privacy.isPrivate" :disabled="savingKey === 'privacy'" @click="setPrivacy(!socialStore.privacy.isPrivate)">
        <span>{{ socialStore.privacy.isPrivate ? t("social.private") : t("social.public") }}</span>
        <i v-if="savingKey === 'privacy'" class="pi pi-spinner pi-spin"></i>
        <i v-else :class="socialStore.privacy.isPrivate ? 'pi pi-lock' : 'pi pi-globe'"></i>
      </button>
    </section>

    <section class="bg-[var(--surface)] p-4 rounded-2xl grid gap-3">
      <div class="flex items-start gap-3.5">
        <div class="w-10 h-10 rounded-lg bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)] shrink-0">
          <i class="pi pi-bell text-lg"></i>
        </div>
        <div class="min-w-0">
          <h3 class="text-[15px] font-bold m-0 text-[var(--text)] leading-tight">{{ t("social.notificationPrefs") }}</h3>
          <p class="m-0 mt-1 text-xs text-[var(--muted)] leading-relaxed">{{ t("social.notificationPrefsHint") }}</p>
        </div>
      </div>

      <div class="grid gap-1.5">
        <article v-for="row in prefRows" :key="row.key" class="flex flex-row items-center justify-between gap-3 p-3 rounded-xl bg-[var(--surface-muted)]">
          <div class="flex items-center gap-3 min-w-0">
            <span class="w-9 h-9 rounded-lg bg-[var(--surface)] flex items-center justify-center text-[var(--muted)] shrink-0">
              <i :class="row.icon"></i>
            </span>
            <span class="min-w-0">
              <strong class="block text-sm text-[var(--text)] truncate">{{ t(`social.prefs.${row.key}`) }}</strong>
              <small class="block text-xs text-[var(--muted)] truncate">{{ t(`social.prefs.${row.key}Hint`) }}</small>
            </span>
          </div>
          <button
            class="profile-switch"
            type="button"
            role="switch"
            :aria-checked="socialStore.notificationPrefs[row.key]"
            :aria-label="t(`social.prefs.${row.key}`)"
            :disabled="savingKey === row.key"
            @click="setPref(row.key, !socialStore.notificationPrefs[row.key])"
          >
            <span class="profile-switch-track">
              <span class="profile-switch-thumb">
                <i v-if="savingKey === row.key" class="pi pi-spinner pi-spin"></i>
              </span>
            </span>
          </button>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.profile-toggle {
  min-width: 132px;
  border: 0;
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--surface-muted);
  color: var(--text);
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
}

.profile-toggle[aria-pressed="true"] {
  background: var(--text);
  color: var(--btn-primary-text);
}

.profile-toggle:disabled {
  opacity: 0.7;
  cursor: wait;
}

.profile-switch {
  width: 44px;
  height: 26px;
  border: 0;
  padding: 0;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  flex: 0 0 auto;
}

.profile-switch-track {
  width: 44px;
  height: 26px;
  border-radius: 999px;
  padding: 3px;
  display: block;
  background: var(--surface-active);
  transition: background 0.16s ease;
}

.profile-switch-thumb {
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: var(--surface);
  color: var(--muted);
  box-shadow: 0 2px 8px rgba(22, 34, 51, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  transform: translateX(0);
  transition: transform 0.16s ease;
}

.profile-switch[aria-checked="true"] .profile-switch-track {
  background: var(--text);
}

.profile-switch[aria-checked="true"] .profile-switch-thumb {
  transform: translateX(18px);
}

.profile-switch:disabled {
  opacity: 0.75;
  cursor: wait;
}
</style>
