<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { describeSocialLink, normalizedSocialUrl, type SocialLinkInput } from "@/features/social-links-edit/model";
import SocialPlatformIcon from "@/features/social-links-edit/ui/SocialPlatformIcon.vue";

const props = withDefaults(defineProps<{
  modelValue: SocialLinkInput[];
  disabled?: boolean;
}>(), {
  disabled: false,
});

const emit = defineEmits<{
  "update:modelValue": [links: SocialLinkInput[]];
  remove: [index: number];
}>();

const { t } = useI18n();

const duplicateUrls = computed(() => {
  const counts = new Map<string, number>();
  props.modelValue.forEach((link) => {
    const key = normalizedSocialUrl(link.url);
    if (!key) return;
    counts.set(key, (counts.get(key) || 0) + 1);
  });
  return new Set([...counts.entries()].filter((entry) => entry[1] > 1).map(([url]) => url));
});

function viewFor(link: SocialLinkInput) {
  return describeSocialLink(link);
}

function isDuplicate(link: SocialLinkInput) {
  const view = describeSocialLink(link);
  if (!view.isValid) return false;
  return duplicateUrls.value.has(normalizedSocialUrl(view.url) || "");
}

function updateLink(index: number, patch: Partial<SocialLinkInput>) {
  const next = props.modelValue.map((link, current) => current === index ? { ...link, ...patch } : link);
  emit("update:modelValue", next);
}

function updateUrl(index: number, value: string | undefined) {
  updateLink(index, { label: "", url: String(value || "") });
}

function onUrlInput(index: number, event: Event) {
  updateUrl(index, (event.target as HTMLInputElement).value);
}
</script>

<template>
  <div class="social-link-editor">
    <div v-if="!modelValue.length" class="text-sm text-[var(--subtle)] px-1">{{ t("profile.noSocialLinks") }}</div>

    <article
      v-for="(link, index) in modelValue"
      :key="index"
      class="social-link-row"
      :class="{ 'social-link-row-invalid': (link.url.trim() && !viewFor(link).isValid) || isDuplicate(link) }"
    >
      <UiIconTile :tone="viewFor(link).meta.tone" class="social-link-icon">
        <SocialPlatformIcon :platform="viewFor(link).meta.key" class="social-link-svg" />
      </UiIconTile>

      <div class="social-link-fields">
        <PInputText
          :value="link.url"
          :disabled="disabled"
          :placeholder="t('profile.socialLinkUrl')"
          autocomplete="url"
          class="w-full"
          @input="onUrlInput(index, $event)"
        />
      </div>

      <small v-if="link.url.trim() && !viewFor(link).isValid" class="social-link-meta social-link-error">{{ t("profile.socialLinkInvalid") }}</small>
      <small v-if="isDuplicate(link)" class="social-link-meta social-link-error">{{ t("profile.socialLinkDuplicate") }}</small>

      <PButton
        class="social-link-remove"
        icon="pi pi-trash"
        rounded
        variant="text"
        severity="secondary"
        :disabled="disabled"
        @click="emit('remove', index)"
      />
    </article>
  </div>
</template>

<style scoped>
.social-link-editor {
  display: grid;
  gap: 8px;
}

.social-link-row {
  min-width: 0;
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 10px;
  border-radius: var(--radius-md);
  background: var(--surface-muted);
  transition: background var(--motion), box-shadow var(--motion);
}

.social-link-row-invalid {
  background: var(--danger-soft);
}

.social-link-icon {
  margin-top: 0;
}

.social-link-svg {
  width: 17px;
  height: 17px;
}

.social-link-fields {
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
}

.social-link-meta {
  grid-column: 2 / -1;
  grid-row: 2;
  min-width: 0;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 680;
}

.social-link-remove {
  grid-column: 3;
  grid-row: 1;
  align-self: center;
}

.social-link-error {
  color: var(--danger);
}

@media (max-width: 639.98px) {
  .social-link-row {
    grid-template-columns: 40px minmax(0, 1fr) auto;
  }

  .social-link-fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
