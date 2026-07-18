import { createI18n } from "vue-i18n";
import en from "@/assets/locales/en.json";
import ru from "@/assets/locales/ru.json";

export const supportedLocales = ["ru", "en"] as const;
export type SupportedLocale = (typeof supportedLocales)[number];

const STORAGE_KEY = "account.locale";

function isSupportedLocale(value: string | null): value is SupportedLocale {
  return value === "ru" || value === "en";
}

function detectLocale(): SupportedLocale {
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (isSupportedLocale(stored)) return stored;
  return window.navigator.language.toLowerCase().startsWith("ru") ? "ru" : "en";
}

export const i18n = createI18n({
  legacy: false,
  locale: detectLocale(),
  fallbackLocale: "en",
  messages: { en, ru },
});

export function setLocale(locale: SupportedLocale): void {
  i18n.global.locale.value = locale;
  window.localStorage.setItem(STORAGE_KEY, locale);
  document.documentElement.lang = locale;
}

document.documentElement.lang = i18n.global.locale.value;
