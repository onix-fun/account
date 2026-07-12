<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import type { Organization } from "@/domain";

export type OrganizationAdminTab = "profile" | "social" | "blocked" | "members";

const props = defineProps<{
  organization: Organization;
  activeTab: OrganizationAdminTab;
}>();

const emit = defineEmits<{
  "update:activeTab": [tab: OrganizationAdminTab];
  choose: [];
}>();

const { t } = useI18n();
const mobileContentOpen = ref(false);
const tabs = computed<Array<{ key: OrganizationAdminTab; icon: string; label: string; description: string; tone: "info" | "success" | "danger" | "cyan" }>>(() => [
  { key: "profile", icon: "pi pi-id-card", label: t("profile.profile"), description: t("organizations.menu.profile"), tone: "info" },
  { key: "social", icon: "pi pi-users", label: t("organizations.social"), description: t("organizations.menu.social"), tone: "success" },
  { key: "blocked", icon: "pi pi-ban", label: t("profile.blocked"), description: t("organizations.menu.blocked"), tone: "danger" },
  { key: "members", icon: "pi pi-sitemap", label: t("organizations.members"), description: t("organizations.menu.members"), tone: "cyan" },
]);

watch(
  () => props.organization.id,
  () => {
    mobileContentOpen.value = false;
  },
);

function initials(): string {
  return props.organization.displayName.slice(0, 1).toUpperCase();
}

function openMobileTab(tab: OrganizationAdminTab) {
  mobileContentOpen.value = true;
  emit("update:activeTab", tab);
}

function closeMobileTab() {
  mobileContentOpen.value = false;
}
</script>

<template>
  <section class="org-admin" :class="{ 'org-admin--mobile-content': mobileContentOpen }">
    <header class="org-admin-header">
      <div class="org-admin-title">
        <span class="org-admin-avatar">
          <img v-if="organization.avatarUrl" :src="organization.avatarUrl" alt="" />
          <span v-else>{{ initials() }}</span>
        </span>
        <span class="min-w-0">
          <h1>{{ organization.displayName }}</h1>
          <small>{{ organization.orgName }} · {{ organization.role }}</small>
        </span>
      </div>
      <PButton icon="pi pi-building" variant="text" severity="secondary" class="w-10 h-10 border-0" :aria-label="t('organizations.choose')" @click="emit('choose')" />
    </header>

    <nav class="org-admin-tabs" aria-label="Organization sections">
      <PButton
        v-for="tab in tabs"
        :key="tab.key"
        v-tooltip.right="tab.label"
        :icon="tab.icon"
        :variant="activeTab === tab.key ? 'primary' : 'text'"
        :severity="activeTab === tab.key ? undefined : 'secondary'"
        class="org-admin-tab-button w-11 h-11 border-0"
        :class="`org-admin-tab-button--${tab.tone}`"
        :aria-label="tab.label"
        @click="emit('update:activeTab', tab.key)"
      />
    </nav>

    <nav class="org-mobile-menu" aria-label="Organization menu">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="org-mobile-row"
        :class="{ active: activeTab === tab.key }"
        @click="openMobileTab(tab.key)"
      >
        <UiIconTile :tone="tab.tone" class="org-mobile-icon"><i :class="tab.icon"></i></UiIconTile>
        <span class="min-w-0">
          <strong>{{ tab.label }}</strong>
          <small>{{ tab.description }}</small>
        </span>
        <i class="pi pi-chevron-right text-[var(--subtle)]"></i>
      </button>
    </nav>

    <main class="org-admin-content">
      <PButton class="org-mobile-back" icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" @click="closeMobileTab" />
      <slot />
    </main>
  </section>
</template>

<style scoped>
.org-admin {
  width: min(800px, 100%);
  margin: 0 auto;
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.org-admin-header {
  grid-column: 1 / -1;
  border-radius: 20px;
  background: var(--surface);
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.org-admin-title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.org-admin-avatar {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: var(--surface-muted);
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  flex-shrink: 0;
}

.org-admin-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.org-admin-title h1 {
  margin: 0;
  color: var(--text);
  font-size: 22px;
  line-height: 1.1;
  font-weight: 900;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-admin-title small {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-admin-tabs {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.org-admin-tab-button {
  position: relative;
}

.org-admin-tab-button::before {
  content: "";
  position: absolute;
  left: 6px;
  width: 3px;
  height: 15px;
  border-radius: 999px;
  opacity: 0;
  transform: scaleY(0.65);
  transition: opacity var(--motion), transform var(--motion);
}

.org-admin-tab-button.p-button:not(.p-button-text)::before {
  opacity: 1;
  transform: scaleY(1);
}

.org-admin-tab-button--info::before {
  background: var(--info);
}

.org-admin-tab-button--success::before {
  background: var(--success);
}

.org-admin-tab-button--danger::before {
  background: var(--danger);
}

.org-admin-tab-button--cyan::before {
  background: var(--cyan);
}

.org-admin-content {
  min-width: 0;
}

.org-mobile-menu {
  display: none;
}

.org-mobile-back {
  display: none;
  justify-self: start;
  margin: 0 0 10px -8px;
}

.org-mobile-row {
  width: 100%;
  min-height: 62px;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--surface);
  color: var(--text);
  padding: 11px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 12px;
  text-align: left;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: background var(--motion), transform var(--motion-fast), box-shadow var(--motion);
}

.org-mobile-row:hover,
.org-mobile-row.active {
  background: var(--surface-active);
  transform: translateY(-1px);
}

.org-mobile-icon {
  font-size: 16px;
}

.org-mobile-row strong,
.org-mobile-row small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-mobile-row small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}

@media (max-width: 900px) {
  .org-admin {
    grid-template-columns: 1fr;
  }

  .org-admin-tabs {
    display: none;
  }

  .org-mobile-menu {
    display: grid;
    gap: 8px;
  }

  .org-admin-content {
    display: none;
  }

  .org-admin--mobile-content .org-mobile-menu {
    display: none;
  }

  .org-admin--mobile-content .org-admin-content {
    display: block;
  }

  .org-admin--mobile-content .org-mobile-back {
    display: inline-flex;
  }
}
</style>
