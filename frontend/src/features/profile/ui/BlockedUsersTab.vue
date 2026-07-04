<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import type { PublicUser } from "@/api/services/ProfileSocialService";
import { useProfileSocialStore } from "@/infra/store";
import ProfileSearchOverlay from "@/features/profile/ui/ProfileSearchOverlay.vue";
import ProfileUserRow from "@/features/profile/ui/ProfileUserRow.vue";

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "warning" | "info"];
}>();

const { t } = useI18n();
const socialStore = useProfileSocialStore();
const isAddOpen = ref(false);
const busyUserId = ref<string | null>(null);

onMounted(() => {
  socialStore.loadBlockedUsers().catch((cause) => emit("message", apiErrorMessage(cause), "error"));
});

async function unblock(user: PublicUser) {
  busyUserId.value = user.id;
  try {
    await socialStore.unblock(user);
    emit("message", t("social.unblockActionDone"));
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
      <h2 class="text-base font-bold m-0 text-[var(--text)]">{{ t("profile.blocked") }}</h2>
      <PButton icon="pi pi-plus" :label="t('social.add')" size="small" @click="isAddOpen = true" />
    </div>

    <div class="grid gap-1.5">
      <div v-if="!socialStore.blockedUsers.length" class="p-9 text-center text-sm text-[var(--muted)] bg-[var(--surface)] rounded-xl">
        {{ t("social.noBlockedUsers") }}
      </div>
      <ProfileUserRow
        v-for="user in socialStore.blockedUsers"
        v-else
        :key="user.id"
        :user="user"
        mode="blocked-remove"
        :busy="busyUserId === user.id"
        @unblock="unblock"
      />
    </div>

    <ProfileSearchOverlay
      :visible="isAddOpen"
      mode="blocked"
      @close="isAddOpen = false"
      @message="(message, tone) => emit('message', message, tone)"
    />
  </section>
</template>
