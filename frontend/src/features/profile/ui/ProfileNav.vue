<script setup lang="ts">
import { useI18n } from "vue-i18n";

export type ProfileTab = "profile" | "connections" | "close" | "blocked" | "settings" | "sessions" | "system";

defineProps<{
  activeTab: ProfileTab;
}>();

const emit = defineEmits<{
  "update:activeTab": [tab: ProfileTab];
}>();

const { t } = useI18n();

const tabs: Array<{ key: ProfileTab; icon: string; tone: string }> = [
  { key: "profile", icon: "pi pi-user", tone: "var(--info)" },
  { key: "connections", icon: "pi pi-users", tone: "var(--cyan)" },
  { key: "close", icon: "pi pi-star", tone: "var(--success)" },
  { key: "blocked", icon: "pi pi-ban", tone: "var(--danger)" },
  { key: "settings", icon: "pi pi-sliders-h", tone: "var(--warning)" },
  { key: "sessions", icon: "pi pi-desktop", tone: "var(--info)" },
  { key: "system", icon: "pi pi-cog", tone: "var(--muted)" },
];
</script>

<template>
  <nav class="profile-nav" aria-label="Profile sections">
    <div class="profile-nav-rail">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        v-tooltip.right="t(`profile.${tab.key}`)"
        type="button"
        class="profile-nav-button"
        :class="{ active: activeTab === tab.key }"
        :style="{ '--tab-tone': tab.tone }"
        :aria-label="t(`profile.${tab.key}`)"
        @click="emit('update:activeTab', tab.key)"
      >
        <i :class="tab.icon" aria-hidden="true"></i>
      </button>
    </div>
  </nav>
</template>

<style scoped>
.profile-nav {
  display: flex;
  justify-content: center;
}

.profile-nav-rail {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 6px;
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.profile-nav-button {
  position: relative;
  width: 42px;
  height: 42px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--tab-tone);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background var(--motion), color var(--motion), transform var(--motion-fast);
}

.profile-nav-button::before {
  content: "";
  position: absolute;
  left: 5px;
  width: 3px;
  height: 16px;
  border-radius: 999px;
  background: var(--tab-tone);
  opacity: 0;
  transform: scaleY(0.6);
  transition: opacity var(--motion), transform var(--motion);
}

.profile-nav-button:hover,
.profile-nav-button.active {
  background: var(--surface-muted);
  color: var(--text);
  transform: translateY(-1px);
}

.profile-nav-button.active::before {
  opacity: 1;
  transform: scaleY(1);
}

@media (max-width: 1023.98px) {
  .profile-nav-rail {
    flex-direction: row;
  }
}
</style>
