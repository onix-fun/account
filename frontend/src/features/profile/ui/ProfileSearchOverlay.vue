<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import { ProfileSocialService, type PublicUser, type RelatedUser } from "@/api/services/ProfileSocialService";
import { useProfileSocialStore } from "@/infra/store";
import ProfileUserRow from "@/features/profile/ui/ProfileUserRow.vue";

const props = withDefaults(defineProps<{
  visible: boolean;
  mode?: "default" | "close-friends" | "blocked";
  followingCandidates?: RelatedUser[];
}>(), {
  mode: "default",
  followingCandidates: () => [],
});

const emit = defineEmits<{
  close: [];
  message: [message: string, tone?: "success" | "error" | "warning" | "info"];
}>();

const { t } = useI18n();
const socialStore = useProfileSocialStore();
const query = ref("");
const results = ref<RelatedUser[]>([]);
const isSearching = ref(false);
const searchInput = ref<HTMLInputElement | null>(null);
const busyUserId = ref<string | null>(null);
let searchTimer: ReturnType<typeof setTimeout> | null = null;

const title = computed(() => props.mode === "close-friends"
  ? t("social.addCloseFriend")
  : props.mode === "blocked"
    ? t("social.blockUser")
    : t("social.searchUsers"));

const emptyText = computed(() => query.value.trim().length < 2 ? t("social.searchHint") : t("social.noUsersFound"));

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return;
    query.value = "";
    results.value = props.mode === "close-friends" ? props.followingCandidates : [];
    await nextTick();
    searchInput.value?.focus();
  },
);

watch(
  () => query.value,
  (value) => {
    if (!props.visible) return;
    if (searchTimer) clearTimeout(searchTimer);
    searchTimer = setTimeout(() => search(value), 250);
  },
);

async function search(value: string) {
  const normalized = value.trim().toLowerCase();
  if (props.mode === "close-friends") {
    results.value = props.followingCandidates.filter((user) => {
      const name = [user.firstName, user.lastName, user.username].filter(Boolean).join(" ").toLowerCase();
      return !normalized || name.includes(normalized);
    });
    return;
  }
  if (normalized.length < 2) {
    results.value = [];
    return;
  }
  isSearching.value = true;
  try {
    results.value = await ProfileSocialService.searchUsers(normalized, 12);
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isSearching.value = false;
  }
}

async function runAction(user: PublicUser, action: () => Promise<void>, successKey: string) {
  busyUserId.value = user.id;
  try {
    await action();
    emit("message", t(successKey));
    if (props.mode === "close-friends") emit("close");
    else await search(query.value);
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyUserId.value = null;
  }
}
</script>

<template>
  <PDialog
    :visible="visible"
    modal
    dismissable-mask
    :show-header="false"
    class="w-[min(720px,calc(100vw-24px))]"
    @update:visible="emit('close')"
  >
    <section class="grid gap-3 p-2 sm:p-3">
      <header class="flex items-center gap-3 px-1">
        <div class="w-10 h-10 rounded-lg bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)] shrink-0">
          <i class="pi pi-search"></i>
        </div>
        <div class="min-w-0 flex-1">
          <h2 class="m-0 text-base font-bold text-[var(--text)]">{{ title }}</h2>
          <p class="m-0 text-xs text-[var(--muted)]">{{ t("social.searchShortcutStyle") }}</p>
        </div>
        <PButton icon="pi pi-times" variant="text" severity="secondary" class="w-9 h-9" :aria-label="t('common.close')" @click="emit('close')" />
      </header>

      <div class="relative">
        <PInputText
          ref="searchInput"
          v-model="query"
          class="w-full !pl-11"
          :placeholder="t('social.searchPlaceholder')"
          autocomplete="off"
          @keydown.esc="emit('close')"
        />
        <i class="pi pi-search absolute left-4 top-1/2 -translate-y-1/2 text-[var(--muted)]"></i>
      </div>

      <div class="grid gap-1.5 max-h-[55vh] overflow-y-auto pr-1">
        <div v-if="isSearching" class="p-8 text-center text-sm text-[var(--muted)] bg-[var(--surface-muted)] rounded-xl">
          <i class="pi pi-spinner pi-spin mr-2"></i>{{ t("common.loading") }}
        </div>
        <div v-else-if="!results.length" class="p-8 text-center text-sm text-[var(--muted)] bg-[var(--surface-muted)] rounded-xl">
          {{ emptyText }}
        </div>
        <ProfileUserRow
          v-for="user in results"
          v-else
          :key="user.id"
          :user="user"
          :relationship="user.relationship"
          :mode="mode === 'close-friends' ? 'close-add' : 'default'"
          :busy="busyUserId === user.id"
          @follow="(target) => runAction(target, () => socialStore.follow(target).then(() => undefined), 'social.followActionDone')"
          @unfollow="(target) => runAction(target, () => socialStore.unfollow(target), 'social.unfollowActionDone')"
          @block="(target) => runAction(target, () => socialStore.block(target), 'social.blockActionDone')"
          @unblock="(target) => runAction(target, () => socialStore.unblock(target), 'social.unblockActionDone')"
          @add-close="(target) => runAction(target, () => socialStore.addCloseFriend(target), 'social.closeFriendAdded')"
        />
      </div>
    </section>
  </PDialog>
</template>
