<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/shared/api/client";
import type { PublicUser } from "@/shared/api/services/ProfileSocialService";
import { useProfileSocialStore } from "@/shared/model/store";
import ProfileSearchOverlay from "@/features/profile/ui/ProfileSearchOverlay.vue";
import ProfileUserRow from "@/features/connections/ui/ProfileUserRow.vue";

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
    <UiSectionHeader :title="t('profile.blocked')">
      <template #actions>
        <PButton icon="pi pi-plus" :label="t('social.add')" size="small" @click="isAddOpen = true" />
      </template>
    </UiSectionHeader>

    <div class="ui-list">
      <div v-if="!socialStore.blockedUsers.length" class="ui-empty">
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
