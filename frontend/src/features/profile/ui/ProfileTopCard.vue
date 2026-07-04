<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import type { User } from "@/domain";
import type { ProfileSummary } from "@/api/services/ProfileSocialService";
import { userDisplayName, userInitials } from "@/shared/lib/user";

const props = defineProps<{
  user: User | null;
  summary: ProfileSummary | null;
  avatarPreview: string;
  isUploadingAvatar: boolean;
}>();

const emit = defineEmits<{
  avatar: [];
  switchAccount: [];
  openView: [view: "followers" | "following" | "notifications"];
}>();

const { t } = useI18n();
const displayName = computed(() => userDisplayName(props.user));
const displayInitials = computed(() => userInitials(props.user));
const unreadCount = computed(() => props.summary?.unreadNotificationCount ?? 0);
const unreadBadge = computed(() => unreadCount.value > 99 ? "99+" : String(unreadCount.value));
</script>

<template>
  <header class="w-full max-w-[800px] mx-auto grid gap-4 bg-[var(--surface)] rounded-2xl p-5 shadow-sm border-0">
    <div class="flex items-center justify-between gap-4 min-w-0">
      <div class="flex items-center gap-4 min-w-0">
        <button
          class="relative w-18 h-18 rounded-full overflow-hidden shrink-0 group focus:outline-none focus:ring-3 focus:ring-[var(--focus-ring)] focus:ring-offset-2 border-0"
          type="button"
          :disabled="isUploadingAvatar"
          :aria-label="t('profile.changePhoto')"
          @click="emit('avatar')"
        >
          <span class="w-full h-full bg-[var(--surface-muted)] flex items-center justify-center text-2xl font-bold text-[var(--text)] border-0">
            <img v-if="avatarPreview" :src="avatarPreview" alt="" class="w-full h-full object-cover border-0" />
            <span v-else>{{ displayInitials }}</span>
          </span>
          <span class="absolute inset-0 bg-black/60 flex items-center justify-center text-white text-xl opacity-0 group-hover:opacity-100 transition-opacity border-0" aria-hidden="true">
            <i :class="isUploadingAvatar ? 'pi pi-spinner pi-spin' : 'pi pi-camera'"></i>
          </span>
        </button>
        <div class="min-w-0">
          <h1 class="text-2xl font-bold m-0 text-[var(--text)] truncate">{{ displayName }}</h1>
          <p class="m-0 mt-1 text-sm text-[var(--muted)] truncate">{{ user?.email }}</p>
        </div>
      </div>
      <PButton
        icon="pi pi-users"
        variant="text"
        severity="secondary"
        class="w-10 h-10 border-0"
        :aria-label="t('profile.switchAccount')"
        :title="t('profile.switchAccount')"
        @click="emit('switchAccount')"
      />
    </div>

    <div class="profile-actions">
      <button class="profile-notification-button" type="button" :aria-label="t('social.notifications')" @click="emit('openView', 'notifications')">
        <i class="pi pi-bell text-lg"></i>
        <span v-if="unreadCount > 0" class="notification-badge">{{ unreadBadge }}</span>
      </button>
      <button class="profile-stat-button" type="button" @click="emit('openView', 'followers')">
        <span>{{ summary?.followersCount ?? 0 }}</span>
        <small>{{ t("social.followers") }}</small>
      </button>
      <button class="profile-stat-button" type="button" @click="emit('openView', 'following')">
        <span>{{ summary?.followingCount ?? 0 }}</span>
        <small>{{ t("social.following") }}</small>
      </button>
    </div>
  </header>
</template>

<style scoped>
.profile-actions {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) minmax(0, 1fr);
  gap: 8px;
  align-items: stretch;
}

.profile-notification-button {
  width: 52px;
  min-height: 52px;
  position: relative;
  border: 0;
  border-radius: 12px;
  background: var(--surface-muted);
  color: var(--text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.notification-badge {
  position: absolute;
  top: 5px;
  right: 5px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--danger);
  color: #fff;
  font-size: 10px;
  line-height: 18px;
  font-weight: 800;
  text-align: center;
}

.profile-stat-button {
  min-width: 0;
  border: 0;
  border-radius: 12px;
  background: var(--surface-muted);
  color: var(--text);
  padding: 10px 8px;
  display: grid;
  gap: 1px;
  text-align: center;
  cursor: pointer;
}

.profile-stat-button span {
  font-size: 18px;
  line-height: 1.1;
  font-weight: 800;
}

.profile-stat-button small {
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
