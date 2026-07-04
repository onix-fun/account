<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import type { PublicUser, Relationship } from "@/api/services/ProfileSocialService";

const props = withDefaults(defineProps<{
  user: PublicUser;
  relationship?: Relationship;
  busy?: boolean;
  mode?: "default" | "request" | "close-add" | "close-remove" | "blocked-remove";
}>(), {
  busy: false,
  mode: "default",
});

const emit = defineEmits<{
  follow: [user: PublicUser];
  unfollow: [user: PublicUser];
  block: [user: PublicUser];
  unblock: [user: PublicUser];
  accept: [user: PublicUser];
  reject: [user: PublicUser];
  addClose: [user: PublicUser];
  removeClose: [user: PublicUser];
}>();

const { t } = useI18n();

const displayName = computed(() => [props.user.firstName, props.user.lastName].filter(Boolean).join(" ") || props.user.username);
const initials = computed(() => displayName.value
  .split(/\s+/)
  .filter(Boolean)
  .slice(0, 2)
  .map((part) => part[0]?.toUpperCase())
  .join(""));
</script>

<template>
  <article class="flex flex-col sm:flex-row sm:items-center gap-3.5 p-3.5 rounded-xl bg-[var(--surface)] border-0 min-w-0">
    <div class="flex items-center gap-3.5 min-w-0 flex-1">
      <span class="w-11 h-11 rounded-full bg-[var(--surface-muted)] flex items-center justify-center text-sm font-bold text-[var(--text)] overflow-hidden shrink-0">
        <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" class="w-full h-full object-cover" />
        <span v-else>{{ initials }}</span>
      </span>
      <span class="min-w-0 grid gap-0.5">
        <strong class="text-[15px] font-bold text-[var(--text)] truncate">{{ displayName }}</strong>
        <small class="text-[13px] text-[var(--muted)] truncate">@{{ user.username }}</small>
      </span>
    </div>

    <div class="flex flex-wrap items-center justify-end gap-2">
      <template v-if="mode === 'request'">
        <PButton icon="pi pi-check" :label="t('social.accept')" size="small" :loading="busy" @click="emit('accept', user)" />
        <PButton icon="pi pi-times" :label="t('social.reject')" variant="text" severity="secondary" size="small" :disabled="busy" @click="emit('reject', user)" />
      </template>

      <template v-else-if="mode === 'close-add'">
        <PButton icon="pi pi-star" :label="t('social.addCloseFriend')" size="small" :loading="busy" @click="emit('addClose', user)" />
      </template>

      <template v-else-if="mode === 'close-remove'">
        <PButton icon="pi pi-star-fill" :label="t('social.remove')" variant="text" severity="secondary" size="small" :loading="busy" @click="emit('removeClose', user)" />
      </template>

      <template v-else-if="mode === 'blocked-remove'">
        <PButton icon="pi pi-lock-open" :label="t('social.unblock')" variant="text" severity="secondary" size="small" :loading="busy" @click="emit('unblock', user)" />
      </template>

      <template v-else>
        <PButton
          v-if="relationship?.isFollowing || relationship?.hasPendingRequest"
          :icon="relationship?.hasPendingRequest ? 'pi pi-clock' : 'pi pi-user-minus'"
          :label="relationship?.hasPendingRequest ? t('social.requested') : t('social.unfollow')"
          variant="text"
          severity="secondary"
          size="small"
          :loading="busy"
          @click="emit('unfollow', user)"
        />
        <PButton
          v-else
          icon="pi pi-user-plus"
          :label="t('social.follow')"
          size="small"
          :loading="busy"
          @click="emit('follow', user)"
        />
        <PButton
          v-if="relationship?.isBlocked"
          icon="pi pi-lock-open"
          :label="t('social.unblock')"
          variant="text"
          severity="secondary"
          size="small"
          :disabled="busy"
          @click="emit('unblock', user)"
        />
        <PButton
          v-else
          icon="pi pi-ban"
          :label="t('social.block')"
          variant="text"
          severity="danger"
          size="small"
          :disabled="busy"
          @click="emit('block', user)"
        />
      </template>
    </div>
  </article>
</template>
