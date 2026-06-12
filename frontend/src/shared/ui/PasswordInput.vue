<script setup lang="ts">
import { useAttrs } from "vue";

defineOptions({ inheritAttrs: false });

defineProps<{
  modelValue: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

const attrs = useAttrs();
</script>

<template>
  <PPassword
    v-bind="attrs"
    :model-value="modelValue"
    toggle-mask
    :feedback="false"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>
