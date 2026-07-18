<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/shared/api/client";
import { useAuthStore } from "@/shared/model/store";

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "info"];
}>();

const authStore = useAuthStore();
const { t } = useI18n();

async function respond(invitationId: string, accept: boolean) {
  try {
    await authStore.respondOrganizationInvitation(invitationId, accept);
    emit("message", accept ? t("organizations.accepted") : t("organizations.declined"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  }
}
</script>

<template>
  <section class="grid gap-3">
    <article v-if="!authStore.organizationInvitations.length" class="invite-empty">
      <UiIconTile tone="info" size="md" class="invite-empty-icon"><i class="pi pi-inbox"></i></UiIconTile>
      <h2>{{ t("organizations.noInvitations") }}</h2>
      <p>{{ t("organizations.noInvitationsHint") }}</p>
    </article>

    <article v-for="invitation in authStore.organizationInvitations" :key="invitation.id" class="invite-card">
      <span class="invite-avatar">
        <img v-if="invitation.organization.avatarUrl" :src="invitation.organization.avatarUrl" alt="" />
        <UiIconTile v-else tone="cyan" size="md" class="invite-avatar-icon"><i class="pi pi-building"></i></UiIconTile>
      </span>
      <span class="min-w-0">
        <strong>{{ invitation.organization.displayName }}</strong>
        <small>{{ invitation.organization.orgName }} · {{ invitation.role }}</small>
      </span>
      <span class="invite-actions">
        <PButton :label="t('common.accept')" size="small" @click="respond(invitation.id, true)" />
        <PButton :label="t('common.decline')" size="small" severity="secondary" variant="text" @click="respond(invitation.id, false)" />
      </span>
    </article>
  </section>
</template>

<style scoped>
.invite-empty,
.invite-card {
  border-radius: 18px;
  background: var(--surface);
  padding: 16px;
}

.invite-empty {
  min-height: 280px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  text-align: center;
}

.invite-avatar {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  background: var(--surface-muted);
  color: var(--muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.invite-empty-icon,
.invite-avatar-icon {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  font-size: 17px;
}

.invite-empty h2 {
  margin: 0;
  color: var(--text);
  font-size: 18px;
  font-weight: 900;
}

.invite-empty p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  font-weight: 600;
}

.invite-card {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.invite-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.invite-card strong,
.invite-card small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.invite-card small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.invite-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 560px) {
  .invite-card {
    grid-template-columns: 48px minmax(0, 1fr);
  }

  .invite-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}
</style>
