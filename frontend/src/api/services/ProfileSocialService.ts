import { profileClient } from "@/api/client";

export interface PublicUser {
  id: string;
  username: string;
  firstName?: string | null;
  lastName?: string | null;
  avatarUrl?: string | null;
  birthday?: BirthdayParts | null;
  socialLinks?: SocialLink[];
}

export interface BirthdayParts {
  day: number;
  month: number;
}

export interface SocialLink {
  label: string;
  url: string;
}

export interface Relationship {
  isFollowing: boolean;
  isFollowedBy: boolean;
  isFriend: boolean;
  isBlocked: boolean;
  hasPendingRequest: boolean;
}

export interface RelatedUser extends PublicUser {
  relationship?: Relationship;
}

export interface ProfileSummary extends PublicUser {
  bio?: string | null;
  birthDate?: string | null;
  followersCount: number;
  followingCount: number;
  isPrivate: boolean;
  unreadNotificationCount: number;
  pendingRequestsCount: number;
}

export interface Page<T> {
  items: T[];
  totalCount: number;
}

export interface SubscriptionRequest {
  id: string;
  subscriberId: string;
  subscribedToId: string;
  status: "PENDING" | "ACCEPTED";
  isCloseFriend: boolean;
  createdAt: string;
  subscriber?: PublicUser | null;
}

export interface PrivacySettings {
  isPrivate: boolean;
  fieldVisibility: FieldVisibility;
}

export type VisibilityAudience = "public" | "followers" | "friends" | "private";

export interface FieldVisibility {
  bio: VisibilityAudience;
  birthday: VisibilityAudience;
  socialLinks: VisibilityAudience;
}

export interface NotificationPrefs {
  inAppSubscriptions: boolean;
  inAppPublications: boolean;
  inAppAuthorMentions: boolean;
  inAppPostComments: boolean;
  inAppNewStories: boolean;
  inAppBirthdays: boolean;
}

export type NotificationAction =
  | { kind: "accept_follow"; targetUserId: string }
  | { kind: "reject_follow"; targetUserId: string }
  | { kind: "open_url"; href: string };

export interface NotificationMetadata {
  href?: string;
  titleKey?: string;
  bodyKey?: string;
  actions: NotificationAction[];
}

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  body: string;
  isRead: boolean;
  actorId?: string | null;
  entityType?: string | null;
  entityId?: string | null;
  metadataJson?: string;
  metadata: NotificationMetadata;
  createdAt: string;
}

interface NotificationResponse extends Omit<NotificationItem, "metadata"> {
  metadata?: Partial<NotificationMetadata>;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function normalizeAction(value: unknown): NotificationAction | null {
  if (!isRecord(value) || typeof value.kind !== "string") return null;
  if (value.kind === "accept_follow" && typeof value.targetUserId === "string") {
    return { kind: "accept_follow", targetUserId: value.targetUserId };
  }
  if (value.kind === "reject_follow" && typeof value.targetUserId === "string") {
    return { kind: "reject_follow", targetUserId: value.targetUserId };
  }
  if (value.kind === "open_url" && typeof value.href === "string") {
    return { kind: "open_url", href: value.href };
  }
  return null;
}

export function parseNotificationMetadata(input?: string | Partial<NotificationMetadata> | null): NotificationMetadata {
  let source: unknown = input;
  if (typeof input === "string") {
    try {
      source = JSON.parse(input);
    } catch {
      source = null;
    }
  }
  if (!isRecord(source)) return { actions: [] };
  const actions = Array.isArray(source.actions) ? source.actions.map(normalizeAction).filter(Boolean) as NotificationAction[] : [];
  return {
    href: typeof source.href === "string" ? source.href : undefined,
    titleKey: typeof source.titleKey === "string" ? source.titleKey : undefined,
    bodyKey: typeof source.bodyKey === "string" ? source.bodyKey : undefined,
    actions,
  };
}

function normalizeNotification(item: NotificationResponse): NotificationItem {
  return {
    ...item,
    metadata: parseNotificationMetadata(item.metadata || item.metadataJson),
  };
}

export class ProfileSocialService {
  static async getSummary(): Promise<ProfileSummary> {
    const response = await profileClient.get<ProfileSummary>("/profile/me");
    return response.data;
  }

  static async getFollowers(userId: string, page = 1, limit = 20): Promise<Page<RelatedUser>> {
    const response = await profileClient.get<Page<RelatedUser>>(`/profile/${userId}/followers`, { params: { page, limit } });
    return response.data;
  }

  static async getFollowing(userId: string, page = 1, limit = 20): Promise<Page<RelatedUser>> {
    const response = await profileClient.get<Page<RelatedUser>>(`/profile/${userId}/following`, { params: { page, limit } });
    return response.data;
  }

  static async searchUsers(query: string, limit = 10): Promise<RelatedUser[]> {
    const response = await profileClient.get<RelatedUser[]>("/profile/search", { params: { q: query, limit } });
    return response.data;
  }

  static async follow(userId: string): Promise<Relationship> {
    const response = await profileClient.post<Relationship>(`/profile/${userId}/follow`);
    return response.data;
  }

  static async unfollow(userId: string): Promise<void> {
    await profileClient.delete(`/profile/${userId}/follow`);
  }

  static async block(userId: string): Promise<void> {
    await profileClient.post(`/profile/${userId}/block`);
  }

  static async unblock(userId: string): Promise<void> {
    await profileClient.delete(`/profile/${userId}/block`);
  }

  static async getRequests(page = 1, limit = 20): Promise<Page<SubscriptionRequest>> {
    const response = await profileClient.get<Page<SubscriptionRequest>>("/profile/me/requests", { params: { page, limit } });
    return response.data;
  }

  static async acceptRequest(userId: string): Promise<void> {
    await profileClient.post(`/profile/requests/${userId}/accept`);
  }

  static async rejectRequest(userId: string): Promise<void> {
    await profileClient.delete(`/profile/requests/${userId}/reject`);
  }

  static async getCloseFriends(): Promise<RelatedUser[]> {
    const response = await profileClient.get<RelatedUser[]>("/profile/me/close-friends");
    return response.data;
  }

  static async addCloseFriend(userId: string): Promise<void> {
    await profileClient.post("/profile/me/close-friends", { userId });
  }

  static async removeCloseFriend(userId: string): Promise<void> {
    await profileClient.delete(`/profile/me/close-friends/${userId}`);
  }

  static async getBlockedUsers(): Promise<RelatedUser[]> {
    const response = await profileClient.get<RelatedUser[]>("/profile/me/blocked");
    return response.data;
  }

  static async getPrivacy(): Promise<PrivacySettings> {
    const response = await profileClient.get<PrivacySettings>("/profile/me/privacy");
    return response.data;
  }

  static async updatePrivacy(settings: PrivacySettings | boolean): Promise<void> {
    await profileClient.put("/profile/me/privacy", typeof settings === "boolean" ? { isPrivate: settings } : settings);
  }

  static async getNotificationPrefs(): Promise<NotificationPrefs> {
    const response = await profileClient.get<NotificationPrefs>("/notifications/preferences");
    return response.data;
  }

  static async updateNotificationPrefs(prefs: NotificationPrefs): Promise<void> {
    await profileClient.put("/notifications/preferences", prefs);
  }

  static async getNotifications(page = 1, limit = 20): Promise<Page<NotificationItem>> {
    const response = await profileClient.get<Page<NotificationResponse>>("/notifications", { params: { page, limit } });
    return {
      items: response.data.items.map(normalizeNotification),
      totalCount: response.data.totalCount,
    };
  }

  static async markNotificationRead(id: string): Promise<void> {
    await profileClient.put(`/notifications/${id}/read`);
  }

  static async markAllNotificationsRead(): Promise<void> {
    await profileClient.put("/notifications/read-all");
  }
}
