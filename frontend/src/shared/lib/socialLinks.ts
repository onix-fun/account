export interface SocialLinkInput {
  label: string;
  url: string;
}

export type SocialPlatform =
  | "telegram"
  | "instagram"
  | "x"
  | "tiktok"
  | "youtube"
  | "github"
  | "linkedin"
  | "email"
  | "phone"
  | "website";

export interface SocialPlatformMeta {
  key: SocialPlatform;
  label: string;
  tone: "neutral" | "info" | "success" | "warning" | "danger" | "pink" | "cyan";
  color: string;
}

const platformMeta: Record<SocialPlatform, SocialPlatformMeta> = {
  telegram: { key: "telegram", label: "Telegram", tone: "cyan", color: "#229ed9" },
  instagram: { key: "instagram", label: "Instagram", tone: "pink", color: "#e94891" },
  x: { key: "x", label: "X", tone: "neutral", color: "#17191c" },
  tiktok: { key: "tiktok", label: "TikTok", tone: "neutral", color: "#111820" },
  youtube: { key: "youtube", label: "YouTube", tone: "danger", color: "#ff0033" },
  github: { key: "github", label: "GitHub", tone: "neutral", color: "#24292f" },
  linkedin: { key: "linkedin", label: "LinkedIn", tone: "info", color: "#0a66c2" },
  email: { key: "email", label: "Email", tone: "warning", color: "#d88a00" },
  phone: { key: "phone", label: "Phone", tone: "success", color: "#2fa55a" },
  website: { key: "website", label: "Website", tone: "info", color: "#3478f6" },
};

export interface SocialLinkView {
  label: string;
  url: string;
  displayUrl: string;
  preview: string;
  isValid: boolean;
  meta: SocialPlatformMeta;
}

export function describeSocialLink(link: SocialLinkInput): SocialLinkView {
  const url = link.url.trim();
  const parsed = parseStrictUrl(url);
  const meta = platformMeta[parsed ? detectPlatform(parsed) : "website"];

  return {
    label: meta.label,
    url,
    displayUrl: parsed ? displayUrl(parsed) : url,
    preview: parsed ? previewValue(parsed, meta.key) : url,
    isValid: Boolean(parsed),
    meta,
  };
}

export function normalizeSocialLinks(links: SocialLinkInput[]): SocialLinkInput[] {
  return links
    .map((link) => {
      const view = describeSocialLink(link);
      return view.isValid ? { label: view.meta.label, url: view.url } : null;
    })
    .filter((link): link is SocialLinkInput => Boolean(link));
}

export function hasInvalidSocialLinks(links: SocialLinkInput[]): boolean {
  return links.some((link) => {
    if (!link.label.trim() && !link.url.trim()) return false;
    return !describeSocialLink(link).isValid;
  });
}

export function hasDuplicateSocialLinks(links: SocialLinkInput[]): boolean {
  const seen = new Set<string>();
  for (const link of links) {
    const normalized = normalizedSocialUrl(link.url);
    if (!normalized) continue;
    if (seen.has(normalized)) return true;
    seen.add(normalized);
  }
  return false;
}

export function normalizedSocialUrl(value: string): string | null {
  const parsed = parseStrictUrl(value.trim());
  return parsed ? parsed.href.replace(/\/$/, "").toLowerCase() : null;
}

function parseStrictUrl(value: string): URL | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    if (url.protocol === "http:" || url.protocol === "https:" || url.protocol === "mailto:" || url.protocol === "tel:") return url;
    return null;
  } catch {
    return null;
  }
}

function detectPlatform(url: URL): SocialPlatform {
  if (url.protocol === "mailto:") return "email";
  if (url.protocol === "tel:") return "phone";

  const host = url.hostname.toLowerCase().replace(/^www\./, "");
  if (host === "t.me" || host.endsWith(".t.me") || host === "telegram.me" || host.endsWith(".telegram.org")) return "telegram";
  if (host === "instagram.com" || host.endsWith(".instagram.com")) return "instagram";
  if (host === "x.com" || host === "twitter.com" || host.endsWith(".twitter.com")) return "x";
  if (host === "tiktok.com" || host.endsWith(".tiktok.com")) return "tiktok";
  if (host === "youtube.com" || host.endsWith(".youtube.com") || host === "youtu.be") return "youtube";
  if (host === "github.com" || host.endsWith(".github.com")) return "github";
  if (host === "linkedin.com" || host.endsWith(".linkedin.com")) return "linkedin";
  return "website";
}

function displayUrl(url: URL): string {
  if (url.protocol === "mailto:") return url.pathname;
  if (url.protocol === "tel:") return url.pathname;
  const host = url.hostname.replace(/^www\./, "");
  const path = url.pathname === "/" ? "" : url.pathname.replace(/\/$/, "");
  return `${host}${path}`;
}

function previewValue(url: URL, platform: SocialPlatform): string {
  if (url.protocol === "mailto:") return url.pathname;
  if (url.protocol === "tel:") return url.pathname;

  const segments = url.pathname.split("/").filter(Boolean);
  const first = segments[0] || "";
  if ((platform === "telegram" || platform === "instagram" || platform === "x" || platform === "tiktok" || platform === "github") && first) {
    return first.startsWith("@") ? first : `@${first}`;
  }
  if (platform === "youtube") {
    const channel = segments.find((segment) => segment.startsWith("@")) || first;
    return channel ? (channel.startsWith("@") ? channel : channel) : displayUrl(url);
  }
  if (platform === "linkedin") {
    const last = segments[segments.length - 1];
    return last ? `@${last}` : displayUrl(url);
  }
  return displayUrl(url);
}
