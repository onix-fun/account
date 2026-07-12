<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import type { PublicUser, RelatedUser } from "@/api/services/ProfileSocialService";
import { useProfileSocialStore } from "@/infra/store";
import ProfileUserRow from "@/features/profile/ui/ProfileUserRow.vue";

type ConnectionsFilter = "followers" | "following" | "friends";

const props = withDefaults(defineProps<{
  initialFilter?: ConnectionsFilter;
  showBack?: boolean;
}>(), {
  initialFilter: "followers",
  showBack: false,
});

const emit = defineEmits<{
  back: [];
  message: [message: string, tone?: "success" | "error" | "warning"];
}>();

const { t } = useI18n();
const socialStore = useProfileSocialStore();
const activeFilter = ref<ConnectionsFilter>(props.initialFilter);
const busyUserId = ref<string | null>(null);

const filters = computed(() => [
  { key: "followers" as const, label: t("social.followers"), icon: "pi pi-users", count: socialStore.followers.totalCount },
  { key: "following" as const, label: t("social.following"), icon: "pi pi-user-plus", count: socialStore.following.totalCount },
  { key: "friends" as const, label: t("social.friends"), icon: "pi pi-user", count: friends.value.length },
]);

const friends = computed(() => {
  const users = new Map<string, RelatedUser>();
  for (const user of [...socialStore.followers.items, ...socialStore.following.items]) {
    if (user.relationship?.isFriend) users.set(user.id, user);
  }
  return [...users.values()];
});

const activeState = computed(() => {
  if (activeFilter.value === "following") return socialStore.following;
  if (activeFilter.value === "friends") {
    return {
      items: friends.value,
      totalCount: friends.value.length,
      page: 1,
      limit: Math.max(friends.value.length, 1),
      isLoading: socialStore.followers.isLoading || socialStore.following.isLoading,
    };
  }
  return socialStore.followers;
});

const emptyText = computed(() => {
  if (activeFilter.value === "following") return t("social.noFollowing");
  if (activeFilter.value === "friends") return t("social.noFriends");
  return t("social.noFollowers");
});

watch(() => props.initialFilter, (filter) => {
  activeFilter.value = filter;
});

onMounted(() => {
  loadInitial();
});

async function loadInitial() {
  try {
    await Promise.all([socialStore.loadFollowers(), socialStore.loadFollowing()]);
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  }
}

async function loadPage(page: number) {
  if (activeFilter.value === "friends") return;
  try {
    if (activeFilter.value === "following") await socialStore.loadFollowing(page);
    else await socialStore.loadFollowers(page);
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  }
}

async function runAction(user: PublicUser, action: () => Promise<void>, successKey: string) {
  busyUserId.value = user.id;
  try {
    await action();
    emit("message", t(successKey));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyUserId.value = null;
  }
}
</script>

<template>
  <section class="grid gap-4">
    <div class="grid gap-2">
      <PButton
        v-if="showBack"
        icon="pi pi-arrow-left"
        :label="t('common.back')"
        variant="text"
        severity="secondary"
        class="-ml-2 justify-self-start"
        @click="emit('back')"
      />
      <UiSectionHeader :title="t('social.connections')" />
    </div>

    <UiActionGroup class="connections-filter" role="tablist" :aria-label="t('social.connections')">
      <button
        v-for="filter in filters"
        :key="filter.key"
        type="button"
        class="connections-filter-button"
        :class="{ active: activeFilter === filter.key }"
        role="tab"
        :aria-selected="activeFilter === filter.key"
        @click="activeFilter = filter.key"
      >
        <i :class="filter.icon" aria-hidden="true"></i>
        <span>{{ filter.label }}</span>
        <small>{{ filter.count }}</small>
      </button>
    </UiActionGroup>

    <div class="ui-list">
      <div v-if="activeState.isLoading" class="ui-empty">
        <i class="pi pi-spinner pi-spin mr-2"></i>{{ t("common.loading") }}
      </div>
      <div v-else-if="!activeState.items.length" class="ui-empty">
        {{ emptyText }}
      </div>
      <ProfileUserRow
        v-for="user in activeState.items"
        v-else
        :key="user.id"
        :user="user"
        :relationship="user.relationship"
        :busy="busyUserId === user.id"
        @follow="(target) => runAction(target, () => socialStore.follow(target).then(() => undefined), 'social.followActionDone')"
        @unfollow="(target) => runAction(target, () => socialStore.unfollow(target), 'social.unfollowActionDone')"
        @block="(target) => runAction(target, () => socialStore.block(target), 'social.blockActionDone')"
        @unblock="(target) => runAction(target, () => socialStore.unblock(target), 'social.unblockActionDone')"
      />
    </div>

    <div v-if="activeFilter !== 'friends' && activeState.totalCount > activeState.limit" class="flex items-center justify-between gap-2">
      <PButton :label="t('social.previous')" icon="pi pi-angle-left" variant="text" severity="secondary" size="small" :disabled="activeState.page <= 1 || activeState.isLoading" @click="loadPage(activeState.page - 1)" />
      <span class="text-xs font-bold text-[var(--muted)]">{{ activeState.page }}</span>
      <PButton :label="t('social.next')" icon="pi pi-angle-right" icon-pos="right" variant="text" severity="secondary" size="small" :disabled="activeState.page * activeState.limit >= activeState.totalCount || activeState.isLoading" @click="loadPage(activeState.page + 1)" />
    </div>
  </section>
</template>

<style scoped>
.connections-filter {
  width: 100%;
  padding: 4px;
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.connections-filter-button {
  min-width: 0;
  flex: 1 1 148px;
  min-height: 42px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--muted);
  display: inline-grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.15;
  cursor: pointer;
  transition: background var(--motion), color var(--motion), transform var(--motion-fast);
}

.connections-filter-button:hover,
.connections-filter-button.active {
  background: var(--surface-muted);
  color: var(--text);
}

.connections-filter-button.active {
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--text) 7%, transparent);
}

.connections-filter-button span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.connections-filter-button small {
  color: var(--subtle);
  font-size: 12px;
  font-weight: 800;
}
</style>
