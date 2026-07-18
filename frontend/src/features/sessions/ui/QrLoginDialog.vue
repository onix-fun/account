<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import QRCode from "qrcode";
import { themes } from "@onix/design-system";
import { apiErrorMessage } from "@/shared/api/client";
import { AuthService, type QrLoginChallenge, type QrLoginChallengeStatus } from "@/shared/api/services/AuthService";

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  close: [];
  consumed: [];
  message: [message: string, tone?: "success" | "error" | "warning" | "info"];
}>();

const { t } = useI18n();
const challenge = ref<QrLoginChallenge | null>(null);
const status = ref<QrLoginChallengeStatus["status"]>("PENDING");
const qrDataUrl = ref("");
const isLoading = ref(false);
const secondsLeft = ref(0);
let pollTimer: ReturnType<typeof setInterval> | null = null;
let tickTimer: ReturnType<typeof setInterval> | null = null;

const isPending = computed(() => status.value === "PENDING" && secondsLeft.value > 0);
const statusLabel = computed(() => {
  if (status.value === "CONSUMED") return t("auth.qr.consumed");
  if (status.value === "CANCELLED") return t("auth.qr.cancelled");
  if (status.value === "EXPIRED" || secondsLeft.value <= 0) return t("auth.qr.expired");
  return t("auth.qr.waiting");
});
const formattedTime = computed(() => {
  const value = Math.max(0, secondsLeft.value);
  return `${Math.floor(value / 60)}:${String(value % 60).padStart(2, "0")}`;
});

watch(
  () => props.visible,
  (visible) => {
    if (visible) createChallenge();
    else cleanup();
  },
  { immediate: true },
);

onBeforeUnmount(cleanup);

async function createChallenge() {
  cleanup();
  isLoading.value = true;
  status.value = "PENDING";
  qrDataUrl.value = "";
  try {
    challenge.value = await AuthService.createQrLoginChallenge();
    status.value = challenge.value.status;
    qrDataUrl.value = await QRCode.toDataURL(challenge.value.scanToken, {
      margin: 1,
      width: 232,
      color: {
        dark: themes.light.text,
        light: themes.light.surface,
      },
    });
    updateCountdown();
    startTimers();
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
  } finally {
    isLoading.value = false;
  }
}

async function refreshChallenge() {
  if (challenge.value && isPending.value) {
    await AuthService.cancelQrLoginChallenge(challenge.value.id).catch(() => undefined);
  }
  await createChallenge();
}

async function cancelChallenge() {
  const id = challenge.value?.id;
  if (id && isPending.value) {
    await AuthService.cancelQrLoginChallenge(id).catch(() => undefined);
  }
  emit("close");
}

function startTimers() {
  stopTimers();
  pollTimer = setInterval(pollStatus, 2000);
  tickTimer = setInterval(updateCountdown, 1000);
}

function stopTimers() {
  if (pollTimer) clearInterval(pollTimer);
  if (tickTimer) clearInterval(tickTimer);
  pollTimer = null;
  tickTimer = null;
}

async function pollStatus() {
  const id = challenge.value?.id;
  if (!id || !isPending.value) return;
  try {
    const next = await AuthService.getQrLoginChallenge(id);
    status.value = next.status;
    if (next.status === "CONSUMED") {
      stopTimers();
      emit("message", t("auth.qr.loginConfirmed"), "success");
      emit("consumed");
    }
  } catch (cause) {
    emit("message", apiErrorMessage(cause), "error");
    stopTimers();
  }
}

function updateCountdown() {
  if (!challenge.value) {
    secondsLeft.value = 0;
    return;
  }
  secondsLeft.value = Math.max(0, Math.ceil((new Date(challenge.value.expiresAt).getTime() - Date.now()) / 1000));
  if (secondsLeft.value <= 0 && status.value === "PENDING") {
    status.value = "EXPIRED";
    stopTimers();
  }
}

function cleanup() {
  stopTimers();
  challenge.value = null;
  qrDataUrl.value = "";
  secondsLeft.value = 0;
  status.value = "PENDING";
  isLoading.value = false;
}
</script>

<template>
  <PDialog
    :visible="visible"
    modal
    :header="t('auth.qr.otherDeviceTitle')"
    class="mobile-fullscreen-dialog w-[calc(100vw-24px)] max-w-[420px]"
    @update:visible="cancelChallenge"
  >
    <section class="grid gap-4">
      <h2 class="m-0 text-base font-bold text-[var(--text)] lg:hidden">{{ t("auth.qr.otherDeviceTitle") }}</h2>

      <div class="qr-frame">
        <div v-if="isLoading" class="qr-loading">
          <i class="pi pi-spinner pi-spin"></i>
          <span>{{ t("common.loading") }}</span>
        </div>
        <img v-else-if="qrDataUrl" :src="qrDataUrl" alt="" class="qr-image" />
        <i v-else class="pi pi-qrcode text-4xl text-[var(--muted)]"></i>
      </div>

      <div class="grid gap-2 text-center">
        <strong class="text-sm text-[var(--text)]">{{ statusLabel }}</strong>
        <span class="text-xs font-semibold text-[var(--muted)]">{{ t("auth.qr.timeLeft", { time: formattedTime }) }}</span>
      </div>

      <div class="manual-code">
        <span>{{ t("auth.qr.manualCode") }}</span>
        <strong>{{ challenge?.manualCode || "---- ---- ----" }}</strong>
      </div>

      <p class="m-0 text-xs leading-relaxed text-[var(--muted)] text-center">{{ t("auth.qr.generatorHint") }}</p>

      <div class="flex flex-col sm:flex-row gap-2 justify-end">
        <PButton :label="t('common.close')" variant="text" severity="secondary" class="w-full sm:w-auto" @click="cancelChallenge" />
        <PButton :label="t('auth.qr.refreshCode')" icon="pi pi-refresh" class="w-full sm:w-auto" :loading="isLoading" @click="refreshChallenge" />
      </div>
    </section>
  </PDialog>
</template>

<style scoped>
.qr-frame {
  min-height: 252px;
  border-radius: 18px;
  background: var(--surface-muted);
  display: grid;
  place-items: center;
  padding: 10px;
}

.qr-image {
  width: 232px;
  height: 232px;
  border-radius: 12px;
  background: var(--btn-primary-text);
}

.qr-loading {
  display: grid;
  gap: 10px;
  place-items: center;
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.manual-code {
  display: grid;
  gap: 6px;
  padding: 12px;
  border-radius: 14px;
  background: var(--surface);
  text-align: center;
}

.manual-code span {
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
}

.manual-code strong {
  color: var(--text);
  font-size: 20px;
  line-height: 1.1;
  font-weight: 900;
  letter-spacing: 0.08em;
}
</style>
