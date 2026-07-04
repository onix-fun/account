import { computed, ref } from "vue";
import { defineStore } from "pinia";
import {
  ProfileSocialService,
  type NotificationItem,
  type NotificationPrefs,
  type Page,
  type PrivacySettings,
  type ProfileSummary,
  type PublicUser,
  type RelatedUser,
  type Relationship,
  type SubscriptionRequest,
} from "@/api/services/ProfileSocialService";

type PagedState<T> = Page<T> & { page: number; limit: number; isLoading: boolean };

function emptyPage<T>(limit = 20): PagedState<T> {
  return { items: [], totalCount: 0, page: 1, limit, isLoading: false };
}

const defaultPrefs: NotificationPrefs = {
  inAppSubscriptions: true,
  inAppPublications: true,
  inAppAuthorMentions: true,
  inAppPostComments: true,
  inAppNewStories: true,
};

export const useProfileSocialStore = defineStore("profileSocial", () => {
  const summary = ref<ProfileSummary | null>(null);
  const privacy = ref<PrivacySettings>({ isPrivate: false });
  const notificationPrefs = ref<NotificationPrefs>({ ...defaultPrefs });
  const followers = ref<PagedState<RelatedUser>>(emptyPage());
  const following = ref<PagedState<RelatedUser>>(emptyPage());
  const requests = ref<PagedState<SubscriptionRequest>>(emptyPage());
  const notifications = ref<PagedState<NotificationItem>>(emptyPage());
  const closeFriends = ref<RelatedUser[]>([]);
  const blockedUsers = ref<RelatedUser[]>([]);
  const isLoadingSummary = ref(false);

  const unreadNotificationCount = computed(() => summary.value?.unreadNotificationCount ?? 0);
  const pendingRequestsCount = computed(() => summary.value?.pendingRequestsCount ?? 0);

  async function refreshSummary() {
    isLoadingSummary.value = true;
    try {
      summary.value = await ProfileSocialService.getSummary();
      privacy.value = { isPrivate: summary.value.isPrivate };
    } finally {
      isLoadingSummary.value = false;
    }
  }

  async function loadSettings() {
    const [nextPrivacy, nextPrefs] = await Promise.all([
      ProfileSocialService.getPrivacy(),
      ProfileSocialService.getNotificationPrefs(),
    ]);
    privacy.value = nextPrivacy;
    notificationPrefs.value = nextPrefs;
  }

  async function setPrivacy(isPrivate: boolean) {
    const previous = privacy.value.isPrivate;
    privacy.value = { isPrivate };
    if (summary.value) summary.value = { ...summary.value, isPrivate };
    try {
      await ProfileSocialService.updatePrivacy(isPrivate);
    } catch (cause) {
      privacy.value = { isPrivate: previous };
      if (summary.value) summary.value = { ...summary.value, isPrivate: previous };
      throw cause;
    }
  }

  async function setNotificationPref(key: keyof NotificationPrefs, value: boolean) {
    const previous = { ...notificationPrefs.value };
    notificationPrefs.value = { ...notificationPrefs.value, [key]: value };
    try {
      await ProfileSocialService.updateNotificationPrefs(notificationPrefs.value);
    } catch (cause) {
      notificationPrefs.value = previous;
      throw cause;
    }
  }

  async function loadFollowers(page = followers.value.page, limit = followers.value.limit) {
    if (!summary.value) await refreshSummary();
    const userId = summary.value?.id;
    if (!userId) return;
    followers.value.isLoading = true;
    try {
      const next = await ProfileSocialService.getFollowers(userId, page, limit);
      followers.value = { ...next, page, limit, isLoading: false };
    } catch (cause) {
      followers.value.isLoading = false;
      throw cause;
    }
  }

  async function loadFollowing(page = following.value.page, limit = following.value.limit) {
    if (!summary.value) await refreshSummary();
    const userId = summary.value?.id;
    if (!userId) return;
    following.value.isLoading = true;
    try {
      const next = await ProfileSocialService.getFollowing(userId, page, limit);
      following.value = { ...next, page, limit, isLoading: false };
    } catch (cause) {
      following.value.isLoading = false;
      throw cause;
    }
  }

  async function loadRequests(page = requests.value.page, limit = requests.value.limit) {
    requests.value.isLoading = true;
    try {
      const next = await ProfileSocialService.getRequests(page, limit);
      requests.value = { ...next, page, limit, isLoading: false };
    } catch (cause) {
      requests.value.isLoading = false;
      throw cause;
    }
  }

  async function loadNotifications(page = notifications.value.page, limit = notifications.value.limit) {
    notifications.value.isLoading = true;
    try {
      const next = await ProfileSocialService.getNotifications(page, limit);
      notifications.value = { ...next, page, limit, isLoading: false };
    } catch (cause) {
      notifications.value.isLoading = false;
      throw cause;
    }
  }

  async function loadCloseFriends() {
    closeFriends.value = await ProfileSocialService.getCloseFriends();
  }

  async function loadBlockedUsers() {
    blockedUsers.value = await ProfileSocialService.getBlockedUsers();
  }

  async function syncAfterSocialMutation() {
    await Promise.allSettled([
      refreshSummary(),
      loadRequests(),
      loadFollowers(),
      loadFollowing(),
      loadCloseFriends(),
      loadBlockedUsers(),
      loadNotifications(),
    ]);
  }

  async function follow(user: PublicUser): Promise<Relationship> {
    const relationship = await ProfileSocialService.follow(user.id);
    await syncAfterSocialMutation();
    return relationship;
  }

  async function unfollow(user: PublicUser) {
    await ProfileSocialService.unfollow(user.id);
    await syncAfterSocialMutation();
  }

  async function block(user: PublicUser) {
    await ProfileSocialService.block(user.id);
    await syncAfterSocialMutation();
  }

  async function unblock(user: PublicUser) {
    await ProfileSocialService.unblock(user.id);
    await syncAfterSocialMutation();
  }

  async function acceptRequest(userId: string) {
    await ProfileSocialService.acceptRequest(userId);
    await syncAfterSocialMutation();
  }

  async function rejectRequest(userId: string) {
    await ProfileSocialService.rejectRequest(userId);
    await syncAfterSocialMutation();
  }

  async function addCloseFriend(user: PublicUser) {
    await ProfileSocialService.addCloseFriend(user.id);
    await syncAfterSocialMutation();
  }

  async function removeCloseFriend(user: PublicUser) {
    await ProfileSocialService.removeCloseFriend(user.id);
    await syncAfterSocialMutation();
  }

  async function markNotificationRead(id: string) {
    await ProfileSocialService.markNotificationRead(id);
    await Promise.all([loadNotifications(), refreshSummary()]);
  }

  async function markAllNotificationsRead() {
    await ProfileSocialService.markAllNotificationsRead();
    await Promise.all([loadNotifications(), refreshSummary()]);
  }

  return {
    summary,
    privacy,
    notificationPrefs,
    followers,
    following,
    requests,
    notifications,
    closeFriends,
    blockedUsers,
    isLoadingSummary,
    unreadNotificationCount,
    pendingRequestsCount,
    refreshSummary,
    loadSettings,
    setPrivacy,
    setNotificationPref,
    loadFollowers,
    loadFollowing,
    loadRequests,
    loadNotifications,
    loadCloseFriends,
    loadBlockedUsers,
    follow,
    unfollow,
    block,
    unblock,
    acceptRequest,
    rejectRequest,
    addCloseFriend,
    removeCloseFriend,
    markNotificationRead,
    markAllNotificationsRead,
    syncAfterSocialMutation,
  };
});
