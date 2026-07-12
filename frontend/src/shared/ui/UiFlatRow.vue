<script setup lang="ts">
withDefaults(defineProps<{
  as?: string;
  active?: boolean;
  muted?: boolean;
  clickable?: boolean;
}>(), {
  as: "article",
  active: false,
  muted: false,
  clickable: false,
});
</script>

<template>
  <component
    :is="as"
    class="ui-row"
    :class="[
      active ? 'ui-row-active' : '',
      muted ? 'ui-row-muted' : '',
      clickable ? 'ui-row-clickable' : '',
    ]"
  >
    <slot />
  </component>
</template>
