<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { ProfileTab } from "@/features/profile/ui/ProfileNav.vue";

defineEmits<{
  openView: [view: ProfileTab];
}>();

const { t } = useI18n();

const menuItems: Array<{ key: ProfileTab; icon: string; description: string; tone: "info" | "success" | "danger" | "warning" | "pink" | "cyan" }> = [
  { key: "profile", icon: "pi pi-user", description: "profile.menu.profile", tone: "info" },
  { key: "close", icon: "pi pi-star", description: "profile.menu.close", tone: "pink" },
  { key: "blocked", icon: "pi pi-ban", description: "profile.menu.blocked", tone: "danger" },
  { key: "settings", icon: "pi pi-sliders-h", description: "profile.menu.settings", tone: "warning" },
  { key: "sessions", icon: "pi pi-desktop", description: "profile.menu.sessions", tone: "cyan" },
  { key: "system", icon: "pi pi-cog", description: "profile.menu.system", tone: "success" },
];
</script>

<template>
  <nav class="grid gap-2 w-full max-w-[800px] mx-auto lg:hidden" aria-label="Profile menu">
    <button
      v-for="item in menuItems"
      :key="item.key"
      class="profile-menu-row"
      type="button"
      @click="$emit('openView', item.key)"
    >
      <UiIconTile :tone="item.tone" class="profile-menu-icon"><i :class="item.icon"></i></UiIconTile>
      <span class="min-w-0">
        <strong>{{ t(`profile.${item.key}`) }}</strong>
        <small>{{ t(item.description) }}</small>
      </span>
      <i class="pi pi-chevron-right text-[var(--subtle)]" aria-hidden="true"></i>
    </button>
  </nav>
</template>

<style scoped>
.profile-menu-row {
  width: 100%;
  min-height: 64px;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--surface);
  color: var(--text);
  padding: 12px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 12px;
  text-align: left;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: background var(--motion), transform var(--motion-fast), box-shadow var(--motion);
}

.profile-menu-row:hover {
  background: var(--surface-muted);
  transform: translateY(-1px);
}

.profile-menu-icon {
  font-size: 16px;
}

.profile-menu-row strong {
  display: block;
  font-size: 15px;
  line-height: 1.2;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-menu-row small {
  display: block;
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.25;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
