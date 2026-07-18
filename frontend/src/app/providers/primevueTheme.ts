import { definePreset } from "@primeuix/themes";
import Aura from "@primeuix/themes/aura";
import { themes } from "@onix/design-system";

const light = themes.light;
const dark = themes.dark;

export const AccountTheme = definePreset(Aura, {
  semantic: {
    primary: {
      50: light.surfaceSoft,
      100: light.surfaceMuted,
      200: light.surfaceActive,
      300: light.surfaceStrong,
      400: light.surfaceStrong,
      500: light.muted,
      600: light.muted,
      700: light.accent,
      800: light.text,
      900: light.text,
      950: light.text,
    },
    colorScheme: {
      light: {
        surface: {
          0: light.surface,
          50: light.surfaceSoft,
          100: light.surfaceMuted,
          200: light.surfaceActive,
          300: light.surfaceStrong,
          400: light.surfaceStrong,
          500: light.muted,
          600: light.muted,
          700: light.accent,
          800: light.text,
          900: light.text,
          950: light.text,
        },
      },
      dark: {
        surface: {
          0: light.surface,
          50: dark.bg,
          100: dark.surfaceMuted,
          200: dark.surface,
          300: dark.surfaceActive,
          400: dark.surfaceStrong,
          500: dark.subtle,
          600: dark.muted,
          700: dark.text,
          800: dark.text,
          900: dark.text,
          950: light.surface,
        },
      },
    },
  },
});
