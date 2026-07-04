<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import { ProfileSocialService, type PublicUser, type RelatedUser } from "@/api/services/ProfileSocialService";
import { useProfileSocialStore } from "@/infra/store";
import { userDisplayName, userInitials } from "@/shared/lib/user";
import ProfileUserRow from "@/features/profile/ui/ProfileUserRow.vue";

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "warning" | "info"];
}>();

const { t } = useI18n();
const socialStore = useProfileSocialStore();
const isAddOpen = ref(false);
const busyUserId = ref<string | null>(null);
const addSearch = ref("");
const followingCandidates = ref<RelatedUser[]>([]);
const isLoadingCandidates = ref(false);

const closeFriendIds = computed(() => new Set(socialStore.closeFriends.map((user) => user.id)));
const filteredCandidates = computed(() => {
  const query = addSearch.value.trim().toLowerCase();
  if (!query) return followingCandidates.value;
  return followingCandidates.value.filter((user) => {
    return [user.firstName, user.lastName, user.username].filter(Boolean).join(" ").toLowerCase().includes(query);
  });
});

onMounted(() => {
  Promise.all([socialStore.loadCloseFriends(), socialStore.refreshSummary()])
    .catch((cause) => emit("message", apiErrorMessage(cause), "error"));
});

async function openAddDialog() {
  isAddOpen.value = true;
  addSearch.value = "";
  await loadFollowingCandidates();
}

async function loadFollowingCandidates() {
  if (!socialStore.summary) await socialStore.refreshSummary();
  const userId = socialStore.summary?.id;
  if (!userId) return;
  isLoadingCandidates.value = true;
  try {
    const page = await ProfileSocialService.getFollowing(userId, 1, 1000);
    followingCandidates.value = page.items;
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isLoadingCandidates.value = false;
  }
}

async function add(user: PublicUser) {
  busyUserId.value = user.id;
  try {
    await socialStore.addCloseFriend(user);
    emit("message", t("social.closeFriendAdded"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyUserId.value = null;
  }
}

async function remove(user: PublicUser) {
  busyUserId.value = user.id;
  try {
    await socialStore.removeCloseFriend(user);
    emit("message", t("social.closeFriendRemoved"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyUserId.value = null;
  }
}
</script>

<template>
  <section class="grid gap-4">
    <div class="flex items-center justify-between gap-3 min-h-[40px]">
      <h2 class="text-base font-bold m-0 text-[var(--text)]">{{ t("profile.close") }}</h2>
      <PButton icon="pi pi-plus" :label="t('social.add')" size="small" @click="openAddDialog" />
    </div>

    <div class="grid gap-1.5">
      <div v-if="!socialStore.closeFriends.length" class="p-9 text-center text-sm text-[var(--muted)] bg-[var(--surface)] rounded-xl">
        {{ t("social.noCloseFriends") }}
      </div>
      <ProfileUserRow
        v-for="user in socialStore.closeFriends"
        v-else
        :key="user.id"
        :user="user"
        mode="close-remove"
        :busy="busyUserId === user.id"
        @remove-close="remove"
      />
    </div>

    <PDialog
      :visible="isAddOpen"
      modal
      dismissable-mask
      :header="t('social.addCloseFriend')"
      class="mobile-fullscreen-dialog w-[min(680px,calc(100vw-24px))]"
      @update:visible="isAddOpen = false"
    >
      <section class="grid gap-3">
        <PButton icon="pi pi-arrow-left" :label="t('common.back')" variant="text" severity="secondary" class="mobile-dialog-back" @click="isAddOpen = false" />
        <h2 class="m-0 text-base font-bold text-[var(--text)] lg:hidden">{{ t("social.addCloseFriend") }}</h2>

        <div class="relative">
          <PInputText
            v-model="addSearch"
            class="w-full"
            :placeholder="t('social.searchFollowingPlaceholder')"
            autocomplete="off"
          />
        </div>

        <div class="grid gap-1.5 max-h-[55vh] overflow-y-auto pr-1">
          <div v-if="isLoadingCandidates" class="p-8 text-center text-sm text-[var(--muted)] bg-[var(--surface-muted)] rounded-xl">
            <i class="pi pi-spinner pi-spin mr-2"></i>{{ t("common.loading") }}
          </div>
          <div v-else-if="!followingCandidates.length" class="p-8 text-center text-sm text-[var(--muted)] bg-[var(--surface-muted)] rounded-xl">
            {{ t("social.noFollowing") }}
          </div>
          <div v-else-if="!filteredCandidates.length" class="p-8 text-center text-sm text-[var(--muted)] bg-[var(--surface-muted)] rounded-xl">
            {{ t("social.noUsersFound") }}
          </div>
          <article
            v-for="user in filteredCandidates"
            v-else
            :key="user.id"
            class="flex flex-col sm:flex-row sm:items-center gap-3.5 p-3.5 rounded-xl bg-[var(--surface-muted)] border-0 min-w-0"
          >
            <div class="flex items-center gap-3.5 min-w-0 flex-1">
              <span class="w-11 h-11 rounded-full bg-[var(--surface)] flex items-center justify-center text-sm font-bold text-[var(--text)] overflow-hidden shrink-0">
                <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" class="w-full h-full object-cover" />
                <span v-else>{{ userInitials(user) }}</span>
              </span>
              <span class="min-w-0 grid gap-0.5">
                <strong class="text-[15px] font-bold text-[var(--text)] truncate">{{ userDisplayName(user) }}</strong>
                <small class="text-[13px] text-[var(--muted)] truncate">@{{ user.username }}</small>
              </span>
            </div>
            <span v-if="closeFriendIds.has(user.id)" class="close-friend-badge">{{ t("social.alreadyCloseFriend") }}</span>
            <PButton
              v-else
              icon="pi pi-star"
              :label="t('social.addCloseFriend')"
              size="small"
              :loading="busyUserId === user.id"
              @click="add(user)"
            />
          </article>
        </div>
      </section>
    </PDialog>
  </section>
</template>

<style scoped>
.close-friend-badge {
  align-self: flex-end;
  border-radius: 999px;
  background: var(--surface);
  color: var(--muted);
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1;
  font-weight: 800;
  white-space: nowrap;
}

@media (min-width: 640px) {
  .close-friend-badge {
    align-self: center;
  }
}
</style>
