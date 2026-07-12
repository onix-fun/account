import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import { router } from "@/app/router";
import { i18n } from "@/shared/i18n";

import PrimeVue from "primevue/config";
import ToastService from "primevue/toastservice";
import ConfirmationService from "primevue/confirmationservice";
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Password from "primevue/password";
import Textarea from "primevue/textarea";
import Dialog from "primevue/dialog";
import Message from "primevue/message";
import Toast from "primevue/toast";
import Tooltip from "primevue/tooltip";
import UiActionGroup from "@/shared/ui/UiActionGroup.vue";
import UiField from "@/shared/ui/UiField.vue";
import UiFlatRow from "@/shared/ui/UiFlatRow.vue";
import UiIconTile from "@/shared/ui/UiIconTile.vue";
import UiSectionHeader from "@/shared/ui/UiSectionHeader.vue";
import UiSurface from "@/shared/ui/UiSurface.vue";

import { definePreset } from "@primeuix/themes";
import Aura from "@primeuix/themes/aura";
import "primeicons/primeicons.css";
import "@fontsource/inter/400.css";
import "@fontsource/inter/500.css";
import "@fontsource/inter/600.css";
import "@fontsource/inter/700.css";
import "./style.css";

const AccountTheme = definePreset(Aura, {
  semantic: {
    primary: {
      50: "#f8f9fa",
      100: "#f3f5f7",
      200: "#e9edf1",
      300: "#dfe4e9",
      400: "#b7c0ca",
      500: "#66717f",
      600: "#434b55",
      700: "#252a31",
      800: "#17191c",
      900: "#111316",
      950: "#090b0d",
    },
    colorScheme: {
      light: {
        surface: {
          0: "#ffffff",
          50: "#f8f9fa",
          100: "#f3f5f7",
          200: "#e9edf1",
          300: "#dfe4e9",
          400: "#b7c0ca",
          500: "#66717f",
          600: "#434b55",
          700: "#252a31",
          800: "#17191c",
          900: "#111316",
          950: "#090b0d",
        },
      },
      dark: {
        surface: {
          0: "#ffffff",
          50: "#0f141a",
          100: "#111820",
          200: "#19212a",
          300: "#26313d",
          400: "#313d49",
          500: "#6f7b89",
          600: "#a5afbc",
          700: "#c8d0d9",
          800: "#e8edf2",
          900: "#f5f7fa",
          950: "#ffffff",
        },
      },
    },
  },
});

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.use(i18n);
app.use(PrimeVue, {
  theme: {
    preset: AccountTheme,
    options: {
      darkModeSelector: ".dark",
    },
  },
});
app.use(ToastService);
app.use(ConfirmationService);

app.component("PButton", Button);
app.component("PInputText", InputText);
app.component("PPassword", Password);
app.component("PTextarea", Textarea);
app.component("PDialog", Dialog);
app.component("PMessage", Message);
app.component("PToast", Toast);
app.component("UiActionGroup", UiActionGroup);
app.component("UiField", UiField);
app.component("UiFlatRow", UiFlatRow);
app.component("UiIconTile", UiIconTile);
app.component("UiSectionHeader", UiSectionHeader);
app.component("UiSurface", UiSurface);
app.directive("tooltip", Tooltip);

app.mount("#app");
