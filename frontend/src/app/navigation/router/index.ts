import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { runtimeConfig } from '@/shared/config/runtime';

export function setupRouter(routes: RouteRecordRaw[]) {
  return createRouter({
    history: createWebHistory(runtimeConfig.frontendBasePath),
    routes
  });
}
