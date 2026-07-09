<script setup lang="ts">
import { useI18n } from "vue-i18n";

defineProps<{
  mode: "user" | "organization";
}>();

const emit = defineEmits<{
  user: [];
  organization: [];
}>();

const { t } = useI18n();
</script>

<template>
  <div class="grid grid-cols-2 gap-1 rounded-full bg-[var(--surface-muted)] p-1" role="group" :aria-label="t('organizations.ownerMode')">
    <button
      v-tooltip.bottom="t('organizations.userMode')"
      type="button"
      class="owner-mode-button"
      :class="{ active: mode === 'user' }"
      :aria-label="t('organizations.userMode')"
      @click="emit('user')"
    >
      <i class="pi pi-user"></i>
    </button>
    <button
      v-tooltip.bottom="t('organizations.organizationMode')"
      type="button"
      class="owner-mode-button"
      :class="{ active: mode === 'organization' }"
      :aria-label="t('organizations.organizationMode')"
      @click="emit('organization')"
    >
      <i class="pi pi-building"></i>
    </button>
  </div>
</template>

<style scoped>
.owner-mode-button {
  width: 38px;
  height: 34px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.owner-mode-button:hover,
.owner-mode-button.active {
  background: var(--text);
  color: var(--surface);
}

.owner-mode-button.active {
  transform: translateY(-1px);
}
</style>
