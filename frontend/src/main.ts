import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "@/app/App.vue";
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
import { AccountTheme } from "@/app/providers/primevueTheme";

import "primeicons/primeicons.css";
import "@fontsource/inter/400.css";
import "@fontsource/inter/500.css";
import "@fontsource/inter/600.css";
import "@fontsource/inter/700.css";
import "@onix/design-system/css";
import "@/app/styles/index.css";

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
