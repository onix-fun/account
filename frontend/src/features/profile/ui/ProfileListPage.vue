<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import type { PublicUser, RelatedUser } from "@/api/services/ProfileSocialService";
import { useProfileSocialStore } from "@/infra/store";
import ProfileUserRow from "@/features/profile/ui/ProfileUserRow.vue";

const props = defineProps<{
  title: string;
  items: RelatedUser[];
  totalCount: number;
  page: number;
  limit: number;
  isLoading: boolean;
  emptyText: string;
  showFriendFilter?: boolean;
}>();

const emit = defineEmits<{
  back: [];
  page: [page: number];
  message: [message: string, tone?: "success" | "error" | "warning"];
}>();

const { t } = useI18n();
const socialStore = useProfileSocialStore();
const busyUserId = ref<string | null>(null);
const friendsOnly = ref(false);
const visibleItems = computed(() => {
  if (!props.showFriendFilter || !friendsOnly.value) return props.items;
  return props.items.filter((user) => user.relationship?.isFriend);
});

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
      <PButton icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="-ml-2 justify-self-start" @click="emit('back')" />
      <div class="flex items-center justify-between gap-3 min-h-[40px]">
        <h2 class="text-base font-bold m-0 text-[var(--text)] truncate">{{ title }}</h2>
        <span class="text-sm font-bold text-[var(--muted)]">{{ totalCount }}</span>
      </div>
    </div>

    <label v-if="showFriendFilter" class="friend-filter">
      <input v-model="friendsOnly" type="checkbox" />
      <span>{{ t("social.friendsOnly") }}</span>
    </label>

    <div class="grid gap-1.5">
      <div v-if="isLoading" class="p-9 text-center text-sm text-[var(--muted)] bg-[var(--surface)] rounded-xl">
        <i class="pi pi-spinner pi-spin mr-2"></i>{{ t("common.loading") }}
      </div>
      <div v-else-if="!visibleItems.length" class="p-9 text-center text-sm text-[var(--muted)] bg-[var(--surface)] rounded-xl">
        {{ emptyText }}
      </div>
      <ProfileUserRow
        v-for="user in visibleItems"
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

    <div v-if="totalCount > limit" class="flex items-center justify-between gap-2">
      <PButton :label="t('social.previous')" icon="pi pi-angle-left" variant="text" severity="secondary" size="small" :disabled="page <= 1 || isLoading" @click="emit('page', page - 1)" />
      <span class="text-xs font-bold text-[var(--muted)]">{{ page }}</span>
      <PButton :label="t('social.next')" icon="pi pi-angle-right" icon-pos="right" variant="text" severity="secondary" size="small" :disabled="page * limit >= totalCount || isLoading" @click="emit('page', page + 1)" />
    </div>
  </section>
</template>

<style scoped>
.friend-filter {
  justify-self: start;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 10px;
  background: var(--surface);
  color: var(--muted);
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.friend-filter input {
  width: 16px;
  height: 16px;
  accent-color: var(--text);
}
</style>
