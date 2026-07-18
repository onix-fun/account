<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { trustedRedirectUrl } from "@/shared/lib/trustedRedirect";

const route = useRoute();
const backUrl = computed(() => {
    return trustedRedirectUrl(route.query.redirect);
});
</script>

<template>
    <div class="profile-layout">
        <div class="top-nav">
            <a v-if="backUrl" :href="backUrl" class="btn btn-ghost back-btn" aria-label="Back">
                <i class="pi pi-arrow-left"></i>
                <span>Back</span>
            </a>
        </div>

        <main class="profile-content">
            <slot />
        </main>
    </div>
</template>

<style scoped>
.profile-layout {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
}

.top-nav {
    padding: 1.5rem;
    position: sticky;
    top: 0;
    z-index: 10;
    display: flex;
    align-items: center;
    min-height: 72px;
}

.back-btn {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    font-weight: 500;
    color: var(--muted);
    font-size: 14px;
}

.back-btn i {
    font-size: 1rem;
}

.profile-content {
    flex: 1;
    padding: 1rem;
    display: flex;
    flex-direction: column;
}
</style>
