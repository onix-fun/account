import { createRouter, createWebHistory } from "vue-router";
import { runtimeConfig } from "@/runtime-config";

export const router = createRouter({
  history: createWebHistory(runtimeConfig.frontendBasePath),
  routes: [
    {
      path: "/",
      name: "Profile",
      component: () => import("@/features/profile/ui/ProfilePage.vue"),
    },
  ],
});
