<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { apiErrorMessage } from "@/api/client";
import { AuthService } from "@/api/services/AuthService";
import { useAuthStore } from "@/infra/store";
import type { Organization, OrganizationMember } from "@/domain";

const props = defineProps<{
  organization: Organization;
}>();

const emit = defineEmits<{
  message: [message: string, tone?: "success" | "error" | "info"];
}>();

const authStore = useAuthStore();
const { t } = useI18n();
const members = ref<OrganizationMember[]>([]);
const isLoading = ref(false);
const busyKey = ref<string | null>(null);
const invite = reactive({
  username: "",
});

watch(() => props.organization.id, () => loadMembers(), { immediate: true });

async function loadMembers() {
  isLoading.value = true;
  try {
    members.value = await AuthService.organizationMembers(props.organization.id);
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isLoading.value = false;
  }
}

async function inviteMember() {
  if (props.organization.role !== "OWNER" || !invite.username.trim()) return;
  busyKey.value = "invite";
  try {
    await authStore.inviteOrganizationMember(props.organization.id, { username: invite.username.trim(), role: "CONTRIBUTOR" });
    invite.username = "";
    await loadMembers();
    emit("message", t("organizations.invited"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyKey.value = null;
  }
}

async function setRole(member: OrganizationMember, role: "OWNER" | "CONTRIBUTOR") {
  if (props.organization.role !== "OWNER") return;
  busyKey.value = `${member.userId}:role`;
  try {
    await AuthService.updateOrganizationMember(props.organization.id, member.userId, role);
    await Promise.all([loadMembers(), authStore.loadOrganizationContext()]);
    emit("message", t("organizations.updated"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyKey.value = null;
  }
}

async function remove(member: OrganizationMember) {
  if (props.organization.role !== "OWNER") return;
  busyKey.value = `${member.userId}:remove`;
  try {
    await AuthService.removeOrganizationMember(props.organization.id, member.userId);
    await Promise.all([loadMembers(), authStore.loadOrganizationContext()]);
    emit("message", t("organizations.updated"));
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    busyKey.value = null;
  }
}

function memberName(member: OrganizationMember): string {
  return [member.firstName, member.lastName].filter(Boolean).join(" ") || member.username;
}
</script>

<template>
  <section class="grid gap-4">
    <article v-if="organization.role === 'OWNER'" class="member-card">
      <div class="min-w-0">
        <h2>{{ t("organizations.invite") }}</h2>
        <p>{{ t("organizations.inviteHint") }}</p>
      </div>
      <div class="grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
        <PInputText v-model="invite.username" :placeholder="t('organizations.inviteUsername')" @keyup.enter="inviteMember" />
        <PButton icon="pi pi-send" :label="t('organizations.invite')" :loading="busyKey === 'invite'" @click="inviteMember" />
      </div>
    </article>

    <article class="member-card">
      <div class="flex items-center justify-between gap-3">
        <div class="min-w-0">
          <h2>{{ t("organizations.members") }}</h2>
          <p>{{ organization.role === "OWNER" ? t("organizations.membersHint") : t("organizations.membersReadonlyHint") }}</p>
        </div>
        <PButton icon="pi pi-refresh" rounded variant="text" severity="secondary" :loading="isLoading" @click="loadMembers" />
      </div>

      <div class="grid gap-2">
        <article v-for="member in members" :key="member.userId" class="member-row">
          <span class="member-avatar">
            <img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" />
            <span v-else>{{ member.username.slice(0, 1).toUpperCase() }}</span>
          </span>
          <span class="min-w-0">
            <strong>{{ memberName(member) }}</strong>
            <small>@{{ member.username }} · {{ member.role }}</small>
          </span>
          <span v-if="organization.role === 'OWNER'" class="member-actions">
            <PButton
              :label="member.role === 'OWNER' ? 'Contributor' : 'Owner'"
              size="small"
              variant="text"
              severity="secondary"
              :loading="busyKey === `${member.userId}:role`"
              @click="setRole(member, member.role === 'OWNER' ? 'CONTRIBUTOR' : 'OWNER')"
            />
            <PButton
              icon="pi pi-trash"
              rounded
              variant="text"
              severity="secondary"
              :loading="busyKey === `${member.userId}:remove`"
              @click="remove(member)"
            />
          </span>
        </article>
      </div>
    </article>
  </section>
</template>

<style scoped>
.member-card {
  border-radius: 18px;
  background: var(--surface);
  padding: 16px;
  display: grid;
  gap: 14px;
}

.member-card h2 {
  margin: 0;
  color: var(--text);
  font-size: 16px;
  font-weight: 900;
}

.member-card p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
  font-weight: 600;
}

.member-row {
  min-height: 58px;
  border-radius: 14px;
  background: var(--surface-muted);
  padding: 10px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.member-avatar {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: var(--surface);
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
}

.member-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.member-row strong,
.member-row small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-row small {
  margin-top: 2px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.member-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

@media (max-width: 560px) {
  .member-row {
    grid-template-columns: 42px minmax(0, 1fr);
  }

  .member-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}
</style>
