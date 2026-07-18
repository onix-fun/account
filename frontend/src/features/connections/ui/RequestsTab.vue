<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/shared/api/client";
import type { PublicUser } from "@/shared/api/services/ProfileSocialService";
import { useProfileSocialStore } from "@/shared/model/store";
import ProfileUserRow from "@/features/connections/ui/ProfileUserRow.vue";

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "warning"];
}>();

const { t } = useI18n();
const socialStore = useProfileSocialStore();
const busyUserId = ref<string | null>(null);
const requestUsers = computed(() => socialStore.requests.items.flatMap((request) => request.subscriber ? [request.subscriber] : []));

onMounted(() => {
  socialStore.loadRequests().catch((cause) => emit("message", apiErrorMessage(cause), "error"));
});

async function run(user: PublicUser, action: () => Promise<void>, successKey: string) {
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
    <UiSectionHeader :title="t('profile.requests')">
      <template #actions>
        <PButton icon="pi pi-refresh" :label="t('profile.refresh')" variant="text" severity="secondary" size="small" :loading="socialStore.requests.isLoading" @click="socialStore.loadRequests()" />
      </template>
    </UiSectionHeader>

    <div class="ui-list">
      <div v-if="socialStore.requests.isLoading" class="ui-empty">
        <i class="pi pi-spinner pi-spin mr-2"></i>{{ t("common.loading") }}
      </div>
      <div v-else-if="!requestUsers.length" class="ui-empty">
        {{ t("social.noRequests") }}
      </div>
      <ProfileUserRow
        v-for="user in requestUsers"
        v-else
        :key="user.id"
        :user="user"
        mode="request"
        :busy="busyUserId === user.id"
        @accept="(user) => run(user, () => socialStore.acceptRequest(user.id), 'social.requestAccepted')"
        @reject="(user) => run(user, () => socialStore.rejectRequest(user.id), 'social.requestRejected')"
      />
    </div>
  </section>
</template>
