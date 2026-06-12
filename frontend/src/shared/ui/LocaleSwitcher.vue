<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { setLocale, type SupportedLocale } from "@/shared/i18n";

const { locale, t } = useI18n();
withDefaults(defineProps<{ variant?: "segmented" | "dropdown" }>(), { variant: "segmented" });

const localeLabels: Record<SupportedLocale, string> = {
  en: "English",
  ru: "Русский",
};

const currentLocale = computed(() => locale.value as SupportedLocale);
const isOpen = ref(false);
const root = ref<HTMLElement | null>(null);

const chooseLocale = (value: SupportedLocale) => {
  setLocale(value);
  isOpen.value = false;
};

const closeOnOutsideClick = (event: MouseEvent) => {
  if (!root.value?.contains(event.target as Node)) isOpen.value = false;
};

onMounted(() => document.addEventListener("click", closeOnOutsideClick));
onBeforeUnmount(() => document.removeEventListener("click", closeOnOutsideClick));
</script>

<template>
  <div v-if="variant === 'segmented'" class="locale-switcher" role="group" :aria-label="t('auth.language')">
    <button
      type="button"
      v-for="value in (['ru', 'en'] as SupportedLocale[])"
      :key="value"
      :class="{ active: currentLocale === value }"
      :aria-pressed="currentLocale === value"
      @click="chooseLocale(value)"
    >
      {{ value.toUpperCase() }}
    </button>
  </div>
  <div v-else ref="root" class="relative inline-block text-left" @keydown.esc="isOpen = false">
    <button
      class="min-w-[120px] min-h-[40px] border-0 rounded-[10px] bg-[var(--surface-muted)] text-[var(--text)] px-3 flex items-center justify-between gap-4 text-xs font-bold hover:bg-[var(--surface-active)] transition-colors cursor-pointer"
      type="button"
      :aria-label="t('auth.language')"
      :aria-expanded="isOpen"
      aria-haspopup="listbox"
      @click="isOpen = !isOpen"
    >
      <span>{{ localeLabels[currentLocale] }}</span>
      <i class="pi pi-chevron-down text-[10px]" aria-hidden="true"></i>
    </button>
    <div 
      v-if="isOpen" 
      class="absolute z-20 top-full mt-2 left-0 min-w-full p-1 rounded-[10px] bg-[var(--surface-raised)] shadow-[var(--shadow)] grid gap-1" 
      role="listbox" 
      :aria-label="t('auth.language')"
    >
      <button
        v-for="value in (['ru', 'en'] as SupportedLocale[])"
        :key="value"
        type="button"
        role="option"
        :aria-selected="currentLocale === value"
        class="min-h-[34px] border-0 rounded-[7px] px-2.5 flex flex-row items-center justify-between gap-3 text-[11px] font-bold transition-colors cursor-pointer w-full"
        :class="currentLocale === value ? 'bg-[var(--text)] text-white' : 'bg-[var(--surface-muted)] text-[var(--muted)] hover:bg-[var(--surface-active)] hover:text-[var(--text)]'"
        @click="chooseLocale(value)"
      >
        <span>{{ localeLabels[value] }}</span>
        <span v-if="currentLocale === value" class="w-4 h-4 flex items-center justify-center shrink-0">
          <i class="pi pi-check text-[9px]" aria-hidden="true"></i>
        </span>
      </button>
    </div>
  </div>
</template>
