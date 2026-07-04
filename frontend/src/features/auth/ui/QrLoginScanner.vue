<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from "vue";
import { useI18n } from "vue-i18n";
import type { Html5Qrcode } from "html5-qrcode";

const props = defineProps<{
  visible: boolean;
  loading?: boolean;
  message?: string;
}>();

const emit = defineEmits<{
  close: [];
  submit: [payload: { scanToken?: string; manualCode?: string }];
}>();

const { t } = useI18n();
const readerId = `qr-login-reader-${Math.random().toString(36).slice(2)}`;
const scanner = shallowRef<Html5Qrcode | null>(null);
const manualCode = ref("");
const cameraError = ref("");
const isStartingCamera = ref(false);
const hasSubmittedScan = ref(false);

const normalizedManualCode = computed(() => manualCode.value.replace(/[^a-z0-9]/gi, "").toUpperCase());
const canSubmitManualCode = computed(() => normalizedManualCode.value.length === 12 && !props.loading);

watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      manualCode.value = "";
      cameraError.value = "";
      hasSubmittedScan.value = false;
      await nextTick();
      await startCamera();
    } else {
      await stopCamera();
    }
  },
);

onBeforeUnmount(() => {
  void stopCamera();
});

async function startCamera() {
  if (!navigator.mediaDevices?.getUserMedia) {
    cameraError.value = t("auth.qr.cameraUnavailable");
    return;
  }
  isStartingCamera.value = true;
  try {
    const { Html5Qrcode } = await import("html5-qrcode");
    const cameras = await Html5Qrcode.getCameras();
    if (!cameras.length) {
      cameraError.value = t("auth.qr.cameraUnavailable");
      return;
    }
    scanner.value = new Html5Qrcode(readerId);
    await scanner.value.start(
      { facingMode: "environment" },
      { fps: 10, qrbox: { width: 240, height: 240 } },
      async (decodedText) => {
        if (hasSubmittedScan.value) return;
        hasSubmittedScan.value = true;
        await stopCamera();
        emit("submit", { scanToken: decodedText });
      },
      () => undefined,
    );
  } catch {
    cameraError.value = t("auth.qr.cameraDenied");
  } finally {
    isStartingCamera.value = false;
  }
}

async function stopCamera() {
  const current = scanner.value;
  scanner.value = null;
  if (!current) return;
  await current.stop().catch(() => undefined);
  current.clear();
}

function submitManualCode() {
  if (!canSubmitManualCode.value) return;
  emit("submit", { manualCode: normalizedManualCode.value });
}

async function close() {
  await stopCamera();
  emit("close");
}
</script>

<template>
  <PDialog
    :visible="visible"
    modal
    :draggable="false"
    :header="t('auth.qr.signInTitle')"
    class="mobile-fullscreen-dialog w-[100vw] h-[100dvh] max-h-[100dvh] sm:w-[calc(100vw-32px)] sm:h-auto sm:max-w-[460px]"
    content-class="h-full sm:h-auto"
    @update:visible="close"
  >
    <section class="grid gap-4">
      <h2 class="m-0 text-base font-bold text-[var(--text)] lg:hidden">{{ t("auth.qr.signInTitle") }}</h2>

      <div class="scanner-shell">
        <div :id="readerId" class="scanner-reader"></div>
        <div v-if="isStartingCamera" class="scanner-state">
          <i class="pi pi-spinner pi-spin"></i>
          <span>{{ t("auth.qr.cameraStarting") }}</span>
        </div>
        <div v-else-if="cameraError" class="scanner-state">
          <i class="pi pi-video-slash"></i>
          <span>{{ cameraError }}</span>
        </div>
      </div>

      <form class="grid gap-2" @submit.prevent="submitManualCode">
        <label class="text-[13px] font-bold text-[var(--muted)]" for="qr-manual-code">{{ t("auth.qr.manualCode") }}</label>
        <PInputText
          id="qr-manual-code"
          v-model="manualCode"
          autocomplete="one-time-code"
          inputmode="text"
          class="w-full text-center font-black tracking-[0.08em]"
          placeholder="ABCD-EFGH-IJKL"
        />
        <PMessage v-if="message" severity="error" variant="simple">{{ message }}</PMessage>
        <PButton
          type="submit"
          class="w-full"
          icon="pi pi-qrcode"
          :label="t('auth.qr.signInAction')"
          :disabled="!canSubmitManualCode"
          :loading="loading"
        />
      </form>

      <PButton :label="t('common.cancel')" variant="text" severity="secondary" class="w-full" @click="close" />
    </section>
  </PDialog>
</template>

<style scoped>
.scanner-shell {
  position: relative;
  min-height: 280px;
  overflow: hidden;
  border-radius: 18px;
  background: var(--surface-muted);
  display: grid;
  place-items: center;
}

.scanner-reader {
  width: 100%;
  min-height: 280px;
}

.scanner-state {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  gap: 10px;
  align-content: center;
  padding: 20px;
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
  text-align: center;
  background: var(--surface-muted);
}
</style>
