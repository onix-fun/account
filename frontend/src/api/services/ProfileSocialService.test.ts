// @vitest-environment jsdom

import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";

beforeAll(() => {
  const storage = new Map<string, string>();
  Object.defineProperty(window, "localStorage", {
    configurable: true,
    value: {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
    },
  });
});

describe("ProfileSocialService", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("parses only known notification metadata actions", async () => {
    const { parseNotificationMetadata } = await import("@/api/services/ProfileSocialService");
    const metadata = parseNotificationMetadata(JSON.stringify({
      href: "/posts/1",
      titleKey: "postPublished",
      bodyKey: "postPublished",
      actions: [
        { kind: "accept_follow", targetUserId: "user-1" },
        { kind: "reject_follow", targetUserId: "user-1" },
        { kind: "open_url", href: "https://example.com" },
        { kind: "delete_everything", targetUserId: "user-2" },
        { kind: "accept_follow" },
      ],
    }));

    expect(metadata.href).toBe("/posts/1");
    expect(metadata.titleKey).toBe("postPublished");
    expect(metadata.bodyKey).toBe("postPublished");
    expect(metadata.actions).toEqual([
      { kind: "accept_follow", targetUserId: "user-1" },
      { kind: "reject_follow", targetUserId: "user-1" },
      { kind: "open_url", href: "https://example.com" },
    ]);
  });

  it("tolerates invalid notification metadata json", async () => {
    const { parseNotificationMetadata } = await import("@/api/services/ProfileSocialService");
    expect(parseNotificationMetadata("{not-json")).toEqual({ actions: [] });
  });

  it("uses profile social endpoints without auth service coupling", async () => {
    const { profileClient } = await import("@/api/client");
    const { ProfileSocialService } = await import("@/api/services/ProfileSocialService");
    const get = vi.spyOn(profileClient, "get").mockResolvedValue({ data: { items: [], totalCount: 0 } });
    const post = vi.spyOn(profileClient, "post").mockResolvedValue({ data: { isFollowing: true } });
    const put = vi.spyOn(profileClient, "put").mockResolvedValue({ data: { success: true } });

    await ProfileSocialService.getFollowers("user-1", 2, 30);
    await ProfileSocialService.searchUsers("ann", 12);
    await ProfileSocialService.follow("user-2");
    await ProfileSocialService.updatePrivacy(true);

    expect(get).toHaveBeenCalledWith("/profile/user-1/followers", { params: { page: 2, limit: 30 } });
    expect(get).toHaveBeenCalledWith("/profile/search", { params: { q: "ann", limit: 12 } });
    expect(post).toHaveBeenCalledWith("/profile/user-2/follow");
    expect(put).toHaveBeenCalledWith("/profile/me/privacy", { isPrivate: true });
  });
});
