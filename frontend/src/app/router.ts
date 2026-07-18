import { createRouter, createWebHistory } from "vue-router";
import { runtimeConfig } from "@/shared/config/runtime";

export const router = createRouter({
  history: createWebHistory(runtimeConfig.frontendBasePath),
  routes: [
    {
      path: "/",
      name: "Profile",
      component: () => import("@/pages/profile/ProfilePage.vue"),
    },
  ],
});
