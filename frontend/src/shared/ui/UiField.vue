<script setup lang="ts">
defineProps<{
  label: string;
  forId?: string;
  caption?: string;
}>();
</script>

<template>
  <div class="ui-field">
    <label v-if="forId" class="ui-label" :for="forId">{{ label }}</label>
    <span v-else class="ui-label">{{ label }}</span>
    <slot />
    <p v-if="caption" class="m-0 ui-caption">{{ caption }}</p>
  </div>
</template>
