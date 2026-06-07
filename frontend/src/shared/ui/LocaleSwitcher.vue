<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { setLocale, type SupportedLocale } from "@/shared/i18n";

const { locale, t } = useI18n();
const currentLocale = computed(() => locale.value as SupportedLocale);

const chooseLocale = (value: SupportedLocale) => {
  setLocale(value);
};
</script>

<template>
  <div class="locale-switcher" role="group" :aria-label="t('auth.language')">
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
</template>
