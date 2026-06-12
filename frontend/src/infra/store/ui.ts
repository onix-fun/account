import { defineStore } from "pinia";
import { ref, watch, onMounted, onUnmounted } from "vue";

export type Theme = "light" | "dark" | "system";

export const useUIStore = defineStore("ui", () => {
  const theme = ref<Theme>((localStorage.getItem("theme") as Theme) || "system");

  const setTheme = (newTheme: Theme) => {
    theme.value = newTheme;
    localStorage.setItem("theme", newTheme);
    applyTheme();
  };

  const applyTheme = () => {
    const isDark =
      theme.value === "dark" || (theme.value === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches);

    if (isDark) {
      document.documentElement.classList.add("dark");
    } else {
      document.documentElement.classList.remove("dark");
    }
  };

  const init = () => {
    applyTheme();
    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
      if (theme.value === "system") {
        applyTheme();
      }
    });
  };

  // Also watch for manual theme changes
  watch(theme, () => {
    applyTheme();
  });

  return {
    theme,
    setTheme,
    init,
  };
});
