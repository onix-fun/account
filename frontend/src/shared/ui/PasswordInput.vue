<script setup lang="ts">
import { ref, useAttrs } from "vue";
import { useI18n } from "vue-i18n";

defineOptions({ inheritAttrs: false });

defineProps<{
  modelValue: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

const attrs = useAttrs();
const { t } = useI18n();
const isVisible = ref(false);
</script>

<template>
  <div class="password-input">
    <input
      v-bind="attrs"
      :value="modelValue"
      :type="isVisible ? 'text' : 'password'"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
    <button
      class="password-visibility-toggle"
      type="button"
      :aria-label="t(isVisible ? 'auth.hidePassword' : 'auth.showPassword')"
      :title="t(isVisible ? 'auth.hidePassword' : 'auth.showPassword')"
      :aria-pressed="isVisible"
      @click="isVisible = !isVisible"
    >
      <i :class="isVisible ? 'pi pi-eye-slash' : 'pi pi-eye'" aria-hidden="true"></i>
    </button>
  </div>
</template>
