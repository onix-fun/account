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
import Dialog from "primevue/dialog";
import Message from "primevue/message";
import Toast from "primevue/toast";

import { definePreset } from "@primeuix/themes";
import Aura from "@primeuix/themes/aura";
import "primeicons/primeicons.css";
import "@fontsource/roboto/400.css";
import "@fontsource/roboto/500.css";
import "@fontsource/roboto/700.css";
import "./style.css";
import { installFrontendTelemetry } from "@/infra/telemetry";

const AccountTheme = definePreset(Aura, {
  semantic: {
    primary: {
      50: "#f8fafc",
      100: "#f1f5f9",
      200: "#e2e8f0",
      300: "#cbd5e1",
      400: "#94a3b8",
      500: "#475569",
      600: "#334155",
      700: "#1e293b",
      800: "#0f172a",
      900: "#020617",
      950: "#010409",
    },
    colorScheme: {
      light: {
        surface: {
          0: "#ffffff",
          50: "#f4f6f8",
          100: "#edf0f2",
          200: "#e1e5e8",
          300: "#c8d0d5",
          400: "#a1adb5",
          500: "#7e8d98",
          600: "#606e7a",
          700: "#4b5660",
          800: "#384047",
          900: "#212121",
          950: "#1a1a1a",
        },
      },
      dark: {
        surface: {
          0: "#ffffff",
          50: "#0d1117",
          100: "#161b22",
          200: "#21262d",
          300: "#30363d",
          400: "#484f58",
          500: "#6e7681",
          600: "#8b949e",
          700: "#b1bac4",
          800: "#c9d1d9",
          900: "#f0f6fc",
          950: "#ffffff",
        },
      },
    },
  },
  components: {
    inputtext: {
      background: "{surface.100}",
      borderColor: "transparent",
      hoverBorderColor: "transparent",
      focusBorderColor: "transparent",
      borderRadius: "10px",
    },
    password: {
      input: {
        background: "{surface.100}",
        borderColor: "transparent",
        hoverBorderColor: "transparent",
        focusBorderColor: "transparent",
        borderRadius: "10px",
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
app.component("PDialog", Dialog);
app.component("PMessage", Message);
app.component("PToast", Toast);

app.mount("#app");
installFrontendTelemetry();
