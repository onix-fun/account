<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import type { PrivacySettings, VisibilityAudience } from "@/api/services/ProfileSocialService";
import { useAuthStore, useProfileSocialStore } from "@/infra/store";

const props = defineProps<{
  notificationOwner?: { ownerType: "USER" | "ORGANIZATION"; ownerId: string } | null;
}>();

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "warning"];
}>();

const { t } = useI18n();
const socialStore = useProfileSocialStore();
const authStore = useAuthStore();
const savingKey = ref<string | null>(null);

const allVisibilityRows: Array<{ key: keyof PrivacySettings["fieldVisibility"]; icon: string }> = [
  { key: "bio", icon: "pi pi-align-left" },
  { key: "birthday", icon: "pi pi-gift" },
  { key: "socialLinks", icon: "pi pi-link" },
];
const visibilityRows = computed(() => authStore.activeOwner?.ownerType === "ORGANIZATION"
  ? allVisibilityRows.filter((row) => row.key !== "birthday")
  : allVisibilityRows
);

const visibilityOptions: VisibilityAudience[] = ["public", "followers", "friends", "private"];

onMounted(() => {
  socialStore.loadSettings(props.notificationOwner).catch((cause) => emit("message", apiErrorMessage(cause), "error"));
});

watch(
  () => props.notificationOwner,
  (owner) => {
    socialStore.loadSettings(owner).catch((cause) => emit("message", apiErrorMessage(cause), "error"));
  },
);

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

async function setNotificationSetting(serviceKey: string, typeKey: string, value: boolean) {
  savingKey.value = `notification:${serviceKey}:${typeKey}`;
  try {
    await socialStore.setNotificationSetting(serviceKey, typeKey, value, props.notificationOwner);
    emit("message", t("social.settingsSaved"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    savingKey.value = null;
  }
}

async function setVisibility(key: keyof PrivacySettings["fieldVisibility"], value: VisibilityAudience) {
  savingKey.value = `visibility:${key}`;
  try {
    await socialStore.setFieldVisibility(key, value);
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
    <UiSectionHeader :title="t('profile.settings')" />

    <UiSurface as="section" class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div class="flex items-center gap-3.5 min-w-0">
        <UiIconTile tone="warning">
          <i class="pi pi-lock text-lg"></i>
        </UiIconTile>
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
    </UiSurface>

    <UiSurface as="section" class="grid gap-3">
      <div class="flex items-start gap-3.5">
        <UiIconTile tone="info">
          <i class="pi pi-eye text-lg"></i>
        </UiIconTile>
        <div class="min-w-0">
          <h3 class="text-[15px] font-bold m-0 text-[var(--text)] leading-tight">{{ t("social.visibility") }}</h3>
          <p class="m-0 mt-1 text-xs text-[var(--muted)] leading-relaxed">{{ t("social.visibilityHint") }}</p>
        </div>
      </div>

      <div class="grid gap-1.5">
        <UiFlatRow v-for="row in visibilityRows" :key="row.key" muted class="grid gap-3 p-3">
          <div class="flex items-center gap-3 min-w-0">
            <UiIconTile size="sm">
              <i :class="row.icon"></i>
            </UiIconTile>
            <span class="min-w-0">
              <strong class="block text-sm text-[var(--text)] truncate">{{ t(`social.visibilityFields.${row.key}`) }}</strong>
              <small class="block text-xs text-[var(--muted)] truncate">{{ t(`social.visibilityFields.${row.key}Hint`) }}</small>
            </span>
          </div>
          <div class="profile-segmented" role="radiogroup" :aria-label="t(`social.visibilityFields.${row.key}`)">
            <button
              v-for="option in visibilityOptions"
              :key="option"
              type="button"
              role="radio"
              :aria-checked="socialStore.privacy.fieldVisibility[row.key] === option"
              :disabled="savingKey === `visibility:${row.key}`"
              @click="setVisibility(row.key, option)"
            >
              {{ t(`social.visibilityOptions.${option}`) }}
            </button>
          </div>
        </UiFlatRow>
      </div>
    </UiSurface>

    <UiSurface as="section" class="grid gap-3">
      <div class="flex items-start gap-3.5">
        <UiIconTile tone="pink">
          <i class="pi pi-bell text-lg"></i>
        </UiIconTile>
        <div class="min-w-0">
          <h3 class="text-[15px] font-bold m-0 text-[var(--text)] leading-tight">{{ t("social.notificationPrefs") }}</h3>
          <p class="m-0 mt-1 text-xs text-[var(--muted)] leading-relaxed">{{ t("social.notificationPrefsHint") }}</p>
        </div>
      </div>

      <div class="grid gap-1.5">
        <section v-for="service in socialStore.notificationSettings.services" :key="service.serviceKey" class="grid gap-1.5">
          <div class="flex items-center gap-3 min-w-0 px-1 pt-1">
            <UiIconTile size="sm" tone="info">
              <i :class="service.icon"></i>
            </UiIconTile>
            <span class="min-w-0">
              <strong class="block text-sm text-[var(--text)] truncate">{{ service.name }}</strong>
              <small class="block text-xs text-[var(--muted)] truncate">{{ service.description }}</small>
            </span>
          </div>
          <UiFlatRow v-for="item in service.items" :key="`${item.serviceKey}:${item.typeKey}`" muted class="flex flex-row items-center justify-between gap-3 p-3">
            <div class="flex items-center gap-3 min-w-0">
              <UiIconTile size="sm">
                <i :class="item.icon"></i>
              </UiIconTile>
              <span class="min-w-0">
                <strong class="block text-sm text-[var(--text)] truncate">{{ item.name }}</strong>
                <small class="block text-xs text-[var(--muted)] truncate">{{ item.description }}</small>
              </span>
            </div>
            <button
              class="profile-switch"
              type="button"
              role="switch"
              :aria-checked="item.enabled"
              :aria-label="item.name"
              :disabled="savingKey === `notification:${item.serviceKey}:${item.typeKey}`"
              @click="setNotificationSetting(item.serviceKey, item.typeKey, !item.enabled)"
            >
              <span class="profile-switch-track">
                <span class="profile-switch-thumb">
                  <i v-if="savingKey === `notification:${item.serviceKey}:${item.typeKey}`" class="pi pi-spinner pi-spin"></i>
                </span>
              </span>
            </button>
          </UiFlatRow>
        </section>
      </div>
    </UiSurface>
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
  background: var(--danger-soft);
  color: var(--danger);
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
  background: var(--danger);
  transition: background 0.16s ease;
}

.profile-switch-thumb {
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: var(--surface);
  color: var(--muted);
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  transform: translateX(0);
  transition: transform 0.16s ease;
}

.profile-switch[aria-checked="true"] .profile-switch-track {
  background: var(--success);
}

.profile-switch[aria-checked="true"] .profile-switch-thumb {
  transform: translateX(18px);
}

.profile-switch:disabled {
  opacity: 0.75;
  cursor: wait;
}

.profile-segmented {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 3px;
  padding: 3px;
  border-radius: 10px;
  background: var(--surface);
}

.profile-segmented button {
  min-width: 0;
  border: 0;
  border-radius: 8px;
  padding: 7px 6px;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.profile-segmented button[aria-checked="true"] {
  background: var(--text);
  color: var(--btn-primary-text);
}

.profile-segmented button:disabled {
  opacity: 0.7;
  cursor: wait;
}
</style>
