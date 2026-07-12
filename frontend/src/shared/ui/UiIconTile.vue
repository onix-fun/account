<script setup lang="ts">
withDefaults(defineProps<{
  tone?: "neutral" | "info" | "success" | "warning" | "danger" | "pink" | "cyan";
  size?: "sm" | "md";
}>(), {
  tone: "neutral",
  size: "md",
});
</script>

<template>
  <span
    class="ui-icon-tile"
    :class="[
      size === 'sm' ? 'ui-icon-tile-sm' : '',
      tone === 'info' ? 'ui-icon-info' : '',
      tone === 'success' ? 'ui-icon-success' : '',
      tone === 'warning' ? 'ui-icon-warning' : '',
      tone === 'danger' ? 'ui-icon-danger' : '',
      tone === 'pink' ? 'ui-icon-pink' : '',
      tone === 'cyan' ? 'ui-icon-cyan' : '',
    ]"
    aria-hidden="true"
  >
    <slot />
  </span>
</template>
