import type { User } from "@/shared/model/domain";

export function userDisplayName(user: User | null | undefined): string {
  if (!user) return "";
  return [user.firstName, user.lastName].filter(Boolean).join(" ") || user.username;
}

export function userInitials(user: User | null | undefined): string {
  const source = userDisplayName(user) || user?.email || "A";
  return source
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}
