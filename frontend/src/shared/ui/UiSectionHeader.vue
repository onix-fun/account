<script setup lang="ts">
defineProps<{
  title: string;
  caption?: string;
}>();
</script>

<template>
  <div class="ui-section-header">
    <div class="min-w-0">
      <h2 class="ui-section-title truncate">{{ title }}</h2>
      <p v-if="caption" class="ui-section-caption">{{ caption }}</p>
    </div>
    <div v-if="$slots.actions" class="ui-action-group shrink-0">
      <slot name="actions" />
    </div>
  </div>
</template>
