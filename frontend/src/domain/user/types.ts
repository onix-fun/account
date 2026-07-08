export interface User {
  id: string;
  username: string;
  email?: string;
  firstName?: string | null;
  lastName?: string | null;
  avatarUrl?: string | null;
  bio?: string | null;
  birthDate?: string | null;
  birthday?: BirthdayParts | null;
  socialLinks?: SocialLink[];
  emailVerified?: boolean;
  preferredLocale?: "ru" | "en";
  role?: string;
}

export interface BirthdayParts {
  day: number;
  month: number;
}

export interface SocialLink {
  label: string;
  url: string;
}

export interface AuthSession {
  id: string;
  isCurrent?: boolean;
  deviceId?: string | null;
  userAgent?: string | null;
  ipAddress?: string | null;
  lastUsedAt?: string | null;
  expiresAt?: string | null;
  createdAt?: string | null;
}

export interface Permission {
  userId: string;
  deviceId: string;
  role: 'OWNER' | 'USER' | 'VIEWER';
}

export type Role = Permission['role'];
