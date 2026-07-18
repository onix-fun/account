import { describe, expect, it } from "vitest";
import {
  describeSocialLink,
  hasDuplicateSocialLinks,
  hasInvalidSocialLinks,
  normalizeSocialLinks,
} from "./model";

describe("social links", () => {
  it("detects supported platforms from strict urls", () => {
    expect(describeSocialLink({ label: "", url: "https://t.me/onix" }).meta.key).toBe("telegram");
    expect(describeSocialLink({ label: "", url: "https://instagram.com/onix" }).meta.key).toBe("instagram");
    expect(describeSocialLink({ label: "", url: "https://x.com/onix" }).meta.key).toBe("x");
    expect(describeSocialLink({ label: "", url: "https://tiktok.com/@onix" }).meta.key).toBe("tiktok");
    expect(describeSocialLink({ label: "", url: "https://youtu.be/demo" }).meta.key).toBe("youtube");
    expect(describeSocialLink({ label: "", url: "https://github.com/onix" }).meta.key).toBe("github");
    expect(describeSocialLink({ label: "", url: "https://linkedin.com/company/onix" }).meta.key).toBe("linkedin");
    expect(describeSocialLink({ label: "", url: "mailto:hello@example.com" }).meta.key).toBe("email");
    expect(describeSocialLink({ label: "", url: "tel:+123456789" }).meta.key).toBe("phone");
  });

  it("requires full urls and rejects duplicates", () => {
    const links = [
      { label: "", url: "@onix" },
      { label: "", url: "example.com" },
      { label: "Site", url: "https://example.com" },
      { label: "Again", url: "https://example.com/" },
    ];

    expect(hasInvalidSocialLinks(links)).toBe(true);
    expect(hasDuplicateSocialLinks(links)).toBe(true);
  });

  it("normalizes labels from platform when label is empty", () => {
    expect(normalizeSocialLinks([{ label: "", url: "https://github.com/onix" }])).toEqual([
      { label: "GitHub", url: "https://github.com/onix" },
    ]);
  });
});
