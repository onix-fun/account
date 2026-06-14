import { runtimeConfig } from "@/runtime-config";

function isTrustedRedirect(url: URL): boolean {
    if (url.origin === window.location.origin) return true;

    if (
        import.meta.env.DEV &&
        (url.hostname === "localhost" || url.hostname === "127.0.0.1") &&
        (url.protocol === "http:" || url.protocol === "https:")
    ) {
        return true;
    }

    return runtimeConfig.trustedRedirectOrigins.includes(url.origin);
}

export function trustedRedirectUrl(value: unknown): string | null {
    const rawUrl = Array.isArray(value) ? value[0] : value;
    if (!rawUrl) return null;

    try {
        const url = new URL(decodeURIComponent(String(rawUrl)), window.location.origin);
        return isTrustedRedirect(url) ? url.toString() : null;
    } catch {
        return null;
    }
}
