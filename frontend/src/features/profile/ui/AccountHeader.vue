<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import type { User } from "@/shared/model/domain";
import type { ProfileSummary } from "@/shared/api/services/ProfileSocialService";
import { userInitials } from "@/shared/model/user";
import OwnerModeSwitch from "@/features/profile/ui/OwnerModeSwitch.vue";

const props = defineProps<{
  mode: "user" | "organization";
  user: User | null;
  summary: ProfileSummary | null;
  backUrl?: string | null;
}>();

const emit = defineEmits<{
  userMode: [];
  organizationMode: [];
  account: [];
  notifications: [];
  search: [];
}>();

const { t } = useI18n();
const unreadCount = computed(() => props.summary?.unreadNotificationCount ?? 0);
const unreadBadge = computed(() => unreadCount.value > 99 ? "99+" : String(unreadCount.value));
const initials = computed(() => userInitials(props.user));
</script>

<template>
  <UiSurface as="header" class="account-header" :padded="false">
    <div class="header-side">
      <PButton
        v-if="backUrl"
        :as="'a'"
        :href="backUrl"
        icon="pi pi-arrow-left"
        variant="text"
        severity="secondary"
        class="header-icon"
        :aria-label="t('common.back')"
      />
      <span v-else class="header-spacer" aria-hidden="true"></span>
    </div>

    <OwnerModeSwitch :mode="mode" @user="emit('userMode')" @organization="emit('organizationMode')" />

    <div class="header-actions">
      <PButton
        v-tooltip.bottom="t('social.search')"
        icon="pi pi-search"
        variant="text"
        severity="secondary"
        class="header-icon"
        :aria-label="t('social.search')"
        @click="emit('search')"
      />
      <button
        v-tooltip.bottom="t('social.notifications')"
        class="header-icon-button"
        type="button"
        :aria-label="t('social.notifications')"
        @click="emit('notifications')"
      >
        <i class="pi pi-bell"></i>
        <span v-if="unreadCount > 0" class="header-badge">{{ unreadBadge }}</span>
      </button>
      <button
        v-tooltip.bottom="t('profile.switchAccount')"
        class="account-button"
        type="button"
        :aria-label="t('profile.switchAccount')"
        @click="emit('account')"
      >
        <img v-if="user?.avatarUrl" :src="user.avatarUrl" alt="" />
        <span v-else>{{ initials }}</span>
      </button>
    </div>
  </UiSurface>
</template>

<style scoped>
.account-header {
  width: min(800px, 100%);
  min-height: 48px;
  margin: 0 auto;
  padding: 8px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 10px;
}

.header-side,
.header-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.header-actions {
  justify-content: flex-end;
}

.header-icon,
.header-icon-button,
.account-button,
.header-spacer {
  width: 40px;
  height: 40px;
  border-radius: 999px;
}

.header-icon-button,
.account-button {
  position: relative;
  border: 0;
  background: var(--surface-muted);
  color: var(--text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: none;
  transition: background var(--motion), transform var(--motion-fast);
}

.header-icon-button:hover,
.account-button:hover {
  background: var(--surface-active);
  transform: translateY(-1px);
}

.account-button {
  overflow: hidden;
  font-size: 12px;
  font-weight: 900;
}

.account-button img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.header-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--danger);
  color: var(--btn-primary-text);
  font-size: 10px;
  line-height: 17px;
  font-weight: 900;
}

@media (max-width: 520px) {
  .account-header {
    gap: 6px;
  }

  .header-actions {
    gap: 4px;
  }
}
</style>
