<script setup lang="ts">
import { reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import { useAuthStore } from "@/infra/store";
import type { Organization } from "@/domain";

defineProps<{
  selectedOrganizationId?: string | null;
}>();

const emit = defineEmits<{
  select: [organization: Organization];
  message: [message: string, tone?: "success" | "error" | "info"];
}>();

const authStore = useAuthStore();
const { t } = useI18n();
const isCreateOpen = ref(false);
const isSaving = ref(false);
const form = reactive({
  orgName: "",
  displayName: "",
  bio: "",
});

function initials(organization: Organization): string {
  return organization.displayName.slice(0, 1).toUpperCase();
}

async function createOrganization() {
  if (!form.orgName.trim() || !form.displayName.trim()) return;
  isSaving.value = true;
  try {
    await authStore.createOrganization({
      orgName: form.orgName.trim(),
      displayName: form.displayName.trim(),
      bio: form.bio.trim() || undefined,
    });
    form.orgName = "";
    form.displayName = "";
    form.bio = "";
    isCreateOpen.value = false;
    emit("message", t("organizations.created"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isSaving.value = false;
  }
}
</script>

<template>
  <section class="selector-screen">
    <header class="selector-header">
      <UiIconTile tone="cyan" class="selector-icon"><i class="pi pi-building"></i></UiIconTile>
      <div class="min-w-0">
        <h1>{{ t("organizations.choose") }}</h1>
        <p>{{ t("organizations.selectorHint") }}</p>
      </div>
    </header>

    <div v-if="!authStore.organizations.length" class="selector-empty">
      <UiIconTile tone="cyan" class="selector-empty-icon"><i class="pi pi-building"></i></UiIconTile>
      <h2>{{ t("organizations.emptyTitle") }}</h2>
      <p>{{ t("organizations.emptyHint") }}</p>
      <PButton :label="t('organizations.create')" icon="pi pi-plus" @click="isCreateOpen = true" />
    </div>

    <div v-else class="selector-list">
      <button
        v-for="organization in authStore.organizations"
        :key="organization.id"
        type="button"
        class="selector-row"
        :class="{ active: selectedOrganizationId === organization.id }"
        @click="emit('select', organization)"
      >
        <span class="selector-avatar">
          <img v-if="organization.avatarUrl" :src="organization.avatarUrl" alt="" />
          <span v-else>{{ initials(organization) }}</span>
        </span>
        <span class="min-w-0">
          <strong>{{ organization.displayName }}</strong>
          <small>{{ organization.orgName }} · {{ organization.role }}</small>
        </span>
        <i v-if="selectedOrganizationId === organization.id" class="pi pi-check text-[var(--success)]"></i>
      </button>

      <button type="button" class="selector-row create" @click="isCreateOpen = true">
        <UiIconTile tone="info" class="selector-avatar"><i class="pi pi-plus"></i></UiIconTile>
        <strong>{{ t("organizations.create") }}</strong>
      </button>
    </div>

    <PDialog
      :visible="isCreateOpen"
      modal
      dismissable-mask
      class="mobile-fullscreen-dialog w-full max-w-[520px]"
      :header="t('organizations.create')"
      @update:visible="isCreateOpen = false"
    >
      <section class="grid gap-3 p-1">
        <PButton icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="mobile-dialog-back justify-self-start" @click="isCreateOpen = false" />
        <div class="grid gap-3 sm:grid-cols-2">
          <PInputText v-model="form.orgName" :placeholder="t('organizations.orgName')" />
          <PInputText v-model="form.displayName" :placeholder="t('organizations.displayName')" />
        </div>
        <PTextarea v-model="form.bio" auto-resize rows="3" :placeholder="t('organizations.bio')" />
        <PButton :label="t('organizations.create')" icon="pi pi-plus" :loading="isSaving" class="justify-self-start" @click="createOrganization" />
      </section>
    </PDialog>
  </section>
</template>

<style scoped>
.selector-screen {
  width: min(800px, 100%);
  min-height: min(720px, calc(100dvh - 150px));
  margin: 0 auto;
  display: grid;
  align-content: start;
  gap: 16px;
}

.selector-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border-radius: 18px;
  background: var(--surface);
}

.selector-header h1,
.selector-empty h2 {
  margin: 0;
  color: var(--text);
  font-weight: 900;
}

.selector-header h1 {
  font-size: 20px;
}

.selector-header p,
.selector-empty p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
  font-weight: 600;
}

.selector-icon,
.selector-empty-icon {
  width: 48px;
  height: 48px;
  font-size: 19px;
}

.selector-empty {
  min-height: 360px;
  border-radius: 22px;
  background: var(--surface);
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  padding: 32px;
  text-align: center;
}

.selector-list {
  display: grid;
  gap: 8px;
}

.selector-row {
  width: 100%;
  min-height: 72px;
  border: 0;
  border-radius: 18px;
  background: var(--surface);
  color: var(--text);
  padding: 12px;
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) 22px;
  align-items: center;
  gap: 12px;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s ease, transform 0.16s ease;
}

.selector-row:hover,
.selector-row.active {
  background: var(--surface-active);
}

.selector-row:hover {
  transform: translateY(-1px);
}

.selector-row.create {
  grid-template-columns: 48px minmax(0, 1fr);
  color: var(--muted);
}

.selector-avatar {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  background: var(--surface-muted);
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
}

.selector-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.selector-row strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selector-row small {
  display: block;
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
