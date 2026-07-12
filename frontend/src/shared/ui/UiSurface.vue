<script setup lang="ts">
withDefaults(defineProps<{
  as?: string;
  tone?: "default" | "muted" | "danger";
  padded?: boolean;
}>(), {
  as: "section",
  tone: "default",
  padded: true,
});
</script>

<template>
  <component
    :is="as"
    class="ui-surface"
    :class="[
      tone === 'muted' ? 'ui-surface-muted' : '',
      tone === 'danger' ? 'ui-surface-danger' : '',
      padded ? 'p-4 sm:p-5' : '',
    ]"
  >
    <slot />
  </component>
</template>
