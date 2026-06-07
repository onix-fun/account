<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { setLocale, type SupportedLocale } from "@/shared/i18n";

const { locale, t } = useI18n();
withDefaults(defineProps<{ variant?: "segmented" | "dropdown" }>(), { variant: "segmented" });
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
      :class="{ active: currentLocale === 'ru' }"
      :aria-pressed="currentLocale === 'ru'"
      @click="chooseLocale('ru')"
    >
      RU
    </button>
    <button
      type="button"
      :class="{ active: currentLocale === 'en' }"
      :aria-pressed="currentLocale === 'en'"
      @click="chooseLocale('en')"
    >
      EN
    </button>
  </div>
  <div v-else ref="root" class="locale-dropdown" @keydown.esc="isOpen = false">
    <button
      class="locale-dropdown-trigger"
      type="button"
      :aria-label="t('auth.language')"
      :aria-expanded="isOpen"
      aria-haspopup="listbox"
      @click="isOpen = !isOpen"
    >
      <span>{{ currentLocale.toUpperCase() }}</span>
      <i class="pi pi-chevron-down" aria-hidden="true"></i>
    </button>
    <div v-if="isOpen" class="locale-dropdown-menu" role="listbox" :aria-label="t('auth.language')">
      <button
        v-for="value in (['ru', 'en'] as SupportedLocale[])"
        :key="value"
        type="button"
        role="option"
        :aria-selected="currentLocale === value"
        :class="{ active: currentLocale === value }"
        @click="chooseLocale(value)"
      >
        {{ value.toUpperCase() }}
        <i v-if="currentLocale === value" class="pi pi-check" aria-hidden="true"></i>
      </button>
    </div>
  </div>
</template>
