<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/infra/store";
import { userDisplayName, userInitials } from "@/shared/lib/user";

defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  close: [];
}>();

const authStore = useAuthStore();
const { t } = useI18n();

const switchAccount = async (userId: string) => {
  await authStore.switchAccount(userId);
  emit("close");
};

const addAccount = () => {
  authStore.promptAddAccount();
  emit("close");
};

const logoutAll = async () => {
  await authStore.logoutAll();
  emit("close");
};
</script>

<template>
  <PDialog
    :visible="visible"
    modal
    dismissable-mask
    :header="t('profile.accountsTitle')"
    class="w-full max-w-[400px]"
    @update:visible="emit('close')"
  >
    <div class="grid gap-1.5 p-1">
      <button
        v-for="account in authStore.storedAccounts"
        :key="account.id"
        class="w-full min-h-[64px] flex items-center gap-3.5 p-3 rounded-xl border border-transparent transition-colors text-left group"
        :class="account.id === authStore.currentUser?.id ? 'bg-[var(--surface-active)] cursor-default' : 'bg-[var(--surface-raised)] hover:bg-[var(--surface-active)]'"
        type="button"
        @click="account.id === authStore.currentUser?.id ? emit('close') : switchAccount(account.id)"
      >
        <span class="w-10 h-10 rounded-full bg-[var(--surface-muted)] flex items-center justify-center text-sm font-bold text-[var(--text)] overflow-hidden shrink-0">
          <img v-if="account.avatarUrl" :src="account.avatarUrl" alt="" class="w-full h-full object-cover" />
          <span v-else>{{ userInitials(account) }}</span>
        </span>
        <span class="flex-1 min-w-0 grid gap-0.5">
          <strong class="text-[15px] font-bold text-[var(--text)] truncate">{{ userDisplayName(account) }}</strong>
          <small class="text-[13px] text-[var(--muted)] truncate">{{ account.email }}</small>
        </span>
        <i v-if="account.id === authStore.currentUser?.id" class="pi pi-check text-[var(--success)] shrink-0"></i>
      </button>

      <button 
        class="w-full min-h-[64px] flex items-center gap-3.5 p-3 rounded-xl bg-[var(--surface-raised)] hover:bg-[var(--surface-active)] transition-colors text-left group" 
        type="button" 
        @click="addAccount"
      >
        <span class="w-10 h-10 rounded-lg bg-[var(--surface-muted)] flex items-center justify-center text-[var(--muted)] shrink-0">
          <i class="pi pi-plus"></i>
        </span>
        <span class="flex-1 min-w-0">
          <strong class="text-sm font-bold text-[var(--text)] truncate">{{ t("profile.addAccount") }}</strong>
        </span>
      </button>
    </div>

    <template #footer>
      <div class="flex items-center gap-3 p-3 pt-0">
        <PButton
          :label="t('profile.signOutAll')"
          severity="danger"
          variant="text"
          class="w-full"
          @click="logoutAll"
        />
      </div>
    </template>
  </PDialog>
</template>
