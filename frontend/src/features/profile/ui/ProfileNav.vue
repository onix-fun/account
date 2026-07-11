<script setup lang="ts">
import { useI18n } from "vue-i18n";

export type ProfileTab = "profile" | "close" | "blocked" | "settings" | "sessions" | "system";

defineProps<{
  activeTab: ProfileTab;
}>();

const emit = defineEmits<{
  "update:activeTab": [tab: ProfileTab];
}>();

const { t } = useI18n();

const tabs: Array<{ key: ProfileTab; icon: string }> = [
  { key: "profile", icon: "pi pi-user" },
  { key: "close", icon: "pi pi-star" },
  { key: "blocked", icon: "pi pi-ban" },
  { key: "settings", icon: "pi pi-sliders-h" },
  { key: "sessions", icon: "pi pi-desktop" },
  { key: "system", icon: "pi pi-cog" },
];
</script>

<template>
  <nav class="lg:sticky lg:top-6 flex lg:flex-col items-center justify-center gap-2" aria-label="Profile sections">
    <div class="flex lg:flex-col items-center gap-2">
      <PButton
        v-for="tab in tabs"
        :key="tab.key"
        v-tooltip.right="t(`profile.${tab.key}`)"
        :icon="tab.icon"
        :variant="activeTab === tab.key ? 'primary' : 'text'"
        :severity="activeTab === tab.key ? undefined : 'secondary'"
        class="w-11 h-11 border-0"
        :aria-label="t(`profile.${tab.key}`)"
        @click="emit('update:activeTab', tab.key)"
      />
    </div>
  </nav>
</template>
