<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  file: File;
}>();

const emit = defineEmits<{
  cancel: [];
  apply: [file: File, previewUrl: string];
}>();

const { t } = useI18n();
const objectUrl = ref("");
const imageElement = ref<HTMLImageElement | null>(null);
const naturalSize = ref({ width: 1, height: 1 });
const offset = ref({ x: 0, y: 0 });
const zoom = ref(1);
const rotation = ref(0);
const dragStart = ref<{ pointerX: number; pointerY: number; offsetX: number; offsetY: number } | null>(null);
const cropSize = 300;
const outputSize = 512;

const baseSize = computed(() => {
  const rotated = rotation.value % 180 !== 0;
  const width = rotated ? naturalSize.value.height : naturalSize.value.width;
  const height = rotated ? naturalSize.value.width : naturalSize.value.height;
  const scale = Math.max(cropSize / width, cropSize / height);
  return {
    width: naturalSize.value.width * scale,
    height: naturalSize.value.height * scale,
  };
});

const imageStyle = computed(() => ({
  width: `${baseSize.value.width}px`,
  height: `${baseSize.value.height}px`,
  transform: `translate(calc(-50% + ${offset.value.x}px), calc(-50% + ${offset.value.y}px)) scale(${zoom.value}) rotate(${rotation.value}deg)`,
}));

const loadObjectUrl = () => {
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value);
  objectUrl.value = URL.createObjectURL(props.file);
  offset.value = { x: 0, y: 0 };
  zoom.value = 1;
  rotation.value = 0;
};

watch(() => props.file, loadObjectUrl, { immediate: true });

onBeforeUnmount(() => {
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value);
  removeDragListeners();
});

const onImageLoad = (event: Event) => {
  const image = event.target as HTMLImageElement;
  naturalSize.value = {
    width: image.naturalWidth || 1,
    height: image.naturalHeight || 1,
  };
  imageElement.value = image;
};

const onPointerDown = (event: PointerEvent) => {
  dragStart.value = {
    pointerX: event.clientX,
    pointerY: event.clientY,
    offsetX: offset.value.x,
    offsetY: offset.value.y,
  };
  window.addEventListener("pointermove", onPointerMove);
  window.addEventListener("pointerup", onPointerUp, { once: true });
};

const onPointerMove = (event: PointerEvent) => {
  if (!dragStart.value) return;
  offset.value = {
    x: dragStart.value.offsetX + event.clientX - dragStart.value.pointerX,
    y: dragStart.value.offsetY + event.clientY - dragStart.value.pointerY,
  };
};

const onPointerUp = () => {
  dragStart.value = null;
  removeDragListeners();
};

function removeDragListeners() {
  window.removeEventListener("pointermove", onPointerMove);
  window.removeEventListener("pointerup", onPointerUp);
}

const rotate = () => {
  rotation.value = (rotation.value + 90) % 360;
};

const applyCrop = async () => {
  const image = imageElement.value;
  if (!image) return;

  const canvas = document.createElement("canvas");
  canvas.width = outputSize;
  canvas.height = outputSize;
  const context = canvas.getContext("2d");
  if (!context) return;

  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, outputSize, outputSize);
  context.translate(outputSize / 2 + (offset.value.x / cropSize) * outputSize, outputSize / 2 + (offset.value.y / cropSize) * outputSize);
  context.rotate((rotation.value * Math.PI) / 180);
  context.scale(zoom.value, zoom.value);

  const drawWidth = (baseSize.value.width / cropSize) * outputSize;
  const drawHeight = (baseSize.value.height / cropSize) * outputSize;
  context.drawImage(image, -drawWidth / 2, -drawHeight / 2, drawWidth, drawHeight);

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, props.file.type || "image/jpeg", 0.92));
  if (!blob) return;

  const croppedFile = new File([blob], props.file.name, { type: blob.type || props.file.type });
  emit("apply", croppedFile, URL.createObjectURL(blob));
};
</script>

<template>
  <PDialog
    :visible="true"
    modal
    dismissable-mask
    class="w-full max-w-[460px]"
    :header="t('profile.cropPhoto')"
    @update:visible="emit('cancel')"
  >
    <div class="grid gap-4 py-2">
      <div class="grid gap-3 justify-items-center">
        <div 
          class="w-full max-w-[300px] aspect-square relative overflow-hidden rounded-xl bg-[#101828] touch-none cursor-move"
          @pointerdown="onPointerDown"
        >
          <img 
            ref="imageElement"
            :src="objectUrl" 
            alt="" 
            :style="imageStyle" 
            class="absolute left-1/2 top-1/2 max-w-none select-none origin-center pointer-events-none"
            @load="onImageLoad" 
          />
          <div class="absolute inset-0 border border-white/80 rounded-xl shadow-[inset_0_0_0_999px_rgba(16,24,40,0.16)] pointer-events-none" aria-hidden="true"></div>
        </div>
        <p class="m-0 text-xs text-[var(--muted)] text-center">{{ t("profile.cropInstructions") }}</p>
      </div>

      <div class="flex items-end gap-3 px-1">
        <div class="flex-1 grid gap-1.5">
          <span class="text-xs font-bold text-[var(--muted)]">{{ t("profile.zoom") }}</span>
          <input 
            v-model.number="zoom" 
            type="range" 
            min="1" 
            max="3" 
            step="0.01" 
            class="w-full accent-[var(--text)] h-1.5 bg-[var(--surface-muted)] rounded-lg appearance-none cursor-pointer"
          />
        </div>
        <PButton 
          icon="pi pi-refresh" 
          variant="text" 
          severity="secondary" 
          class="w-10 h-10" 
          :aria-label="t('profile.rotate')" 
          @click="rotate" 
        />
      </div>
    </div>

    <template #footer>
      <div class="flex items-center gap-3 w-full">
        <PButton 
          :label="t('common.cancel')" 
          variant="text" 
          severity="secondary" 
          class="flex-1" 
          @click="emit('cancel')" 
        />
        <PButton 
          :label="t('common.apply')" 
          class="flex-1" 
          @click="applyCrop" 
        />
      </div>
    </template>
  </PDialog>
</template>
