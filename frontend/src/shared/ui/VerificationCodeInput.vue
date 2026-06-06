<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";

const props = withDefaults(defineProps<{
  modelValue: string;
  error?: string;
  disabled?: boolean;
  autofocus?: boolean;
}>(), {
  error: "",
  disabled: false,
  autofocus: false,
});

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

const inputs = ref<HTMLInputElement[]>([]);
const digits = computed(() => Array.from({ length: 6 }, (_, index) => props.modelValue[index] || ""));
const localError = computed(() => {
  if (props.error) return props.error;
  return props.modelValue && props.modelValue.length !== 6 ? "invalid" : "";
});

const setCode = (value: string, focusIndex?: number) => {
  emit("update:modelValue", value.replace(/\D/g, "").slice(0, 6));
  if (focusIndex !== undefined) nextTick(() => inputs.value[focusIndex]?.focus());
};

const onInput = (index: number, event: Event) => {
  const value = (event.target as HTMLInputElement).value.replace(/\D/g, "");
  if (value.length > 1) {
    setCode(value, Math.min(value.length, 6) - 1);
    return;
  }
  const next = [...digits.value];
  next[index] = value;
  setCode(next.join(""), value && index < 5 ? index + 1 : undefined);
};

const onKeydown = (index: number, event: KeyboardEvent) => {
  if (event.key === "Backspace" && !digits.value[index] && index > 0) {
    event.preventDefault();
    const next = [...digits.value];
    next[index - 1] = "";
    setCode(next.join(""), index - 1);
  }
  if (event.key === "ArrowLeft" && index > 0) inputs.value[index - 1]?.focus();
  if (event.key === "ArrowRight" && index < 5) inputs.value[index + 1]?.focus();
};

const onPaste = (event: ClipboardEvent) => {
  const value = event.clipboardData?.getData("text") || "";
  if (!value) return;
  event.preventDefault();
  setCode(value, Math.min(value.replace(/\D/g, "").length, 6) - 1);
};

watch(
  () => props.autofocus,
  (enabled) => {
    if (enabled) nextTick(() => inputs.value[0]?.focus());
  },
  { immediate: true },
);
</script>

<template>
  <div class="verification-code-field">
    <div class="verification-code-input" :class="{ invalid: Boolean(localError) }" @paste="onPaste">
      <input
        v-for="(_, index) in digits"
        :key="index"
        :ref="(element) => { if (element) inputs[index] = element as HTMLInputElement }"
        :value="digits[index]"
        :disabled="disabled"
        :aria-label="`${index + 1}`"
        :aria-invalid="Boolean(localError)"
        inputmode="numeric"
        pattern="[0-9]"
        maxlength="1"
        autocomplete="one-time-code"
        @input="onInput(index, $event)"
        @keydown="onKeydown(index, $event)"
      />
    </div>
    <span v-if="error" class="validation-message text-danger">{{ error }}</span>
  </div>
</template>
