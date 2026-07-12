<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import { ProfileSocialService, type PublicUser, type RelatedUser } from "@/api/services/ProfileSocialService";
import { useAuthStore, useProfileSocialStore } from "@/infra/store";
import ProfileUserRow from "@/features/profile/ui/ProfileUserRow.vue";

const props = withDefaults(defineProps<{
  visible: boolean;
  mode?: "default" | "close-friends" | "blocked";
  followingCandidates?: RelatedUser[];
  page?: boolean;
}>(), {
  mode: "default",
  followingCandidates: () => [],
  page: false,
});

const emit = defineEmits<{
  close: [];
  message: [message: string, tone?: "success" | "error" | "warning" | "info"];
}>();

const { t } = useI18n();
const authStore = useAuthStore();
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
const isOrganizationMode = computed(() => authStore.activeOwner?.ownerType === "ORGANIZATION");
const activeOrganizationId = computed(() => isOrganizationMode.value ? authStore.activeOwner?.ownerId || "" : "");

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return;
    query.value = "";
    results.value = props.mode === "close-friends" ? props.followingCandidates : [];
    await nextTick();
    searchInput.value?.focus();
  },
  { immediate: true },
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

async function inviteUser(user: PublicUser, role: "OWNER" | "CONTRIBUTOR") {
  if (!activeOrganizationId.value || (user.ownerType || "USER") !== "USER") return;
  busyUserId.value = user.id;
  try {
    await authStore.inviteOrganizationMember(activeOrganizationId.value, { userId: user.id, role });
    emit("message", t("organizations.invited"));
    await search(query.value);
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyUserId.value = null;
  }
}
</script>

<template>
  <section v-if="visible" class="search-surface" :class="{ 'search-surface--overlay': !page }">
    <div class="search-inner">
      <header class="search-header">
        <PButton icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="-ml-2 justify-self-start" @click="emit('close')" />
        <div class="search-title">
          <span><i class="pi pi-search"></i></span>
          <div class="min-w-0">
            <h2>{{ title }}</h2>
            <p>{{ isOrganizationMode ? t("organizations.inviteHint") : t("social.searchShortcutStyle") }}</p>
          </div>
        </div>
      </header>

      <PInputText
        ref="searchInput"
        v-model="query"
        class="search-input"
        :placeholder="t('social.searchPlaceholder')"
        autocomplete="off"
        @keydown.esc="emit('close')"
      />

      <div class="search-results">
        <div v-if="isSearching" class="search-state">
          <i class="pi pi-spinner pi-spin mr-2"></i>{{ t("common.loading") }}
        </div>
        <div v-else-if="!results.length" class="search-state">
          {{ emptyText }}
        </div>
        <ProfileUserRow
          v-for="user in results"
          v-else
          :key="`${user.ownerType || 'USER'}:${user.id}`"
          :user="user"
          :relationship="user.relationship"
          :mode="mode === 'close-friends' ? 'close-add' : 'default'"
          :show-invite="isOrganizationMode && (user.ownerType || 'USER') === 'USER'"
          :membership-state="user.organizationMembershipState"
          :busy="busyUserId === user.id"
          @follow="(target) => runAction(target, () => socialStore.follow(target).then(() => undefined), 'social.followActionDone')"
          @unfollow="(target) => runAction(target, () => socialStore.unfollow(target), 'social.unfollowActionDone')"
          @block="(target) => runAction(target, () => socialStore.block(target), 'social.blockActionDone')"
          @unblock="(target) => runAction(target, () => socialStore.unblock(target), 'social.unblockActionDone')"
          @add-close="(target) => runAction(target, () => socialStore.addCloseFriend(target), 'social.closeFriendAdded')"
          @invite="inviteUser"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.search-surface {
  min-height: 100dvh;
  background: var(--bg);
}

.search-surface--overlay {
  position: fixed;
  z-index: 1200;
  inset: 0;
}

.search-inner {
  width: min(920px, calc(100% - 28px));
  min-height: 100dvh;
  margin: 0 auto;
  padding: max(18px, env(safe-area-inset-top)) 0 24px;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 14px;
}

.search-header {
  display: grid;
  justify-items: start;
  gap: 10px;
}

.search-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-title > span {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--surface-muted);
  color: var(--muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.search-title h2 {
  margin: 0;
  color: var(--text);
  font-size: 18px;
  line-height: 1.15;
  font-weight: 900;
}

.search-title p {
  margin: 3px 0 0;
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.search-input {
  width: 100%;
}

.search-results {
  min-height: 0;
  display: grid;
  align-content: start;
  gap: 8px;
  overflow-y: auto;
  padding-right: 2px;
}

.search-state {
  min-height: 180px;
  border-radius: 14px;
  background: var(--surface);
  color: var(--muted);
  display: grid;
  place-items: center;
  text-align: center;
  padding: 24px;
  font-size: 14px;
  font-weight: 700;
}
</style>
