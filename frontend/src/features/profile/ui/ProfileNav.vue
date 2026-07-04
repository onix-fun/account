<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";

export type ProfileTab = "profile" | "requests" | "close" | "blocked" | "settings" | "sessions" | "system";

const props = withDefaults(defineProps<{
  activeTab: ProfileTab;
  showRequests?: boolean;
}>(), {
  showRequests: true,
});

const emit = defineEmits<{
  "update:activeTab": [tab: ProfileTab];
  search: [];
}>();

const { t } = useI18n();

const tabs: Array<{ key: ProfileTab; icon: string }> = [
  { key: "profile", icon: "pi pi-user" },
  { key: "requests", icon: "pi pi-inbox" },
  { key: "close", icon: "pi pi-star" },
  { key: "blocked", icon: "pi pi-ban" },
  { key: "settings", icon: "pi pi-sliders-h" },
  { key: "sessions", icon: "pi pi-desktop" },
  { key: "system", icon: "pi pi-cog" },
];
const visibleTabs = computed(() => tabs.filter((tab) => props.showRequests || tab.key !== "requests"));
</script>

<template>
  <nav class="lg:sticky lg:top-6 flex lg:flex-col items-center justify-center gap-2" aria-label="Profile sections">
    <PButton
      v-tooltip.right="t('social.search')"
      icon="pi pi-search"
      variant="text"
      severity="secondary"
      class="w-11 h-11 border-0"
      :aria-label="t('social.search')"
      @click="emit('search')"
    />
    <div class="w-px h-7 lg:w-7 lg:h-px bg-[var(--surface-active)] mx-1 lg:mx-0" aria-hidden="true"></div>
    <div class="flex lg:flex-col items-center gap-2">
      <PButton
        v-for="tab in visibleTabs"
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
