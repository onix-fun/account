<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { useUIStore, type Theme } from "@/infra/store/ui";

const uiStore = useUIStore();
const { t } = useI18n();

const options: { value: Theme; icon: string; label: string }[] = [
  { value: "light", icon: "pi pi-sun", label: "system.themes.light" },
  { value: "dark", icon: "pi pi-moon", label: "system.themes.dark" },
  { value: "system", icon: "pi pi-desktop", label: "system.themes.system" },
];

const currentTheme = computed(() => uiStore.theme);
</script>

<template>
  <div class="flex p-1 bg-[var(--surface-muted)] rounded-[10px] w-full sm:w-auto overflow-hidden">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="flex-1 sm:flex-none flex items-center justify-center gap-2 px-3.5 py-2 rounded-[7px] border-0 text-[11px] font-bold transition-all cursor-pointer"
      :class="currentTheme === option.value ? 'bg-[var(--btn-primary-bg)] text-[var(--btn-primary-text)]' : 'bg-transparent text-[var(--subtle)] hover:text-[var(--text)]'"
      @click="uiStore.setTheme(option.value)"
    >
      <i :class="option.icon" class="text-[12px]"></i>
      <span>{{ t(option.label) }}</span>
    </button>
  </div>
</template>
