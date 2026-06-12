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
  <div class="grid gap-2 w-full">
    <div 
      class="grid grid-cols-6 gap-2 w-full" 
      :class="{ 'opacity-60 pointer-events-none': disabled }"
      @paste="onPaste"
    >
      <input
        v-for="(_, index) in 6"
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
        class="w-full aspect-square text-center text-xl font-bold bg-[var(--surface-muted)] text-[var(--text)] rounded-[10px] outline-none transition-all focus:bg-[var(--surface-raised)] focus:ring-3 focus:ring-[var(--focus-ring)] border-0"
        :class="{ 'bg-[var(--toast-error-bg)] ring-2 ring-[var(--danger)]/30': Boolean(localError) && modelValue.length === 6 }"
        @input="onInput(index, $event)"
        @keydown="onKeydown(index, $event)"
      />
    </div>
    <span v-if="error" class="text-xs font-semibold text-[var(--danger)]">{{ error }}</span>
  </div>
</template>
