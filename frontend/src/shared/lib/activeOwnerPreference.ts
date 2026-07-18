import type { OwnerIdentity, OwnerType } from "@/shared/model/domain";

interface ActiveOwnerPreference {
  userId: string;
  ownerType: OwnerType;
  ownerId: string;
  updatedAt: string;
}

const STORAGE_KEY = "onix.activeOwner.v1";

export function rememberActiveOwnerPreference(userId: string, owner: OwnerIdentity): void {
  const preference: ActiveOwnerPreference = {
    userId,
    ownerType: owner.ownerType,
    ownerId: owner.ownerId,
    updatedAt: new Date().toISOString(),
  };
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(preference));
}

export function rememberUserOwnerPreference(userId: string): void {
  rememberActiveOwnerPreference(userId, {
    ownerType: "USER",
    ownerId: userId,
    username: "",
    displayName: "",
  });
}

export function clearActiveOwnerPreference(): void {
  window.localStorage.removeItem(STORAGE_KEY);
}
