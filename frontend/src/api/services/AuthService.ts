import { profileClient } from "@/api/client";
import { DeviceIdManager } from "@/shared/lib/deviceId";
import type { AuthSession, User } from "@/domain";

interface BrowserAuthResponse {
  user: UserResponse;
}

interface UserResponse {
  id: string;
  email: string;
  username: string;
  firstName?: string | null;
  lastName?: string | null;
  avatarUrl?: string | null;
  bio?: string | null;
  emailVerified?: boolean;
  role?: string;
  status?: string;
}

export interface RegistrationStartedResponse {
  status: "CODE_SENT";
  expiresInSeconds: number;
}

export type AccountLookupState = "ACTIVE" | "NOT_FOUND" | "PENDING_REGISTRATION" | "EMAIL_UNVERIFIED" | "BLOCKED" | "EMAIL_LOGIN";

export interface AccountLookupResponse {
  state: AccountLookupState;
  identifier: string;
  avatarUrl?: string | null;
}

interface RegisterPayload {
  email: string;
  username: string;
  password: string;
}

interface LoginPayload {
  identifier: string;
  password: string;
}

interface UpdateProfilePayload {
  username?: string;
  firstName?: string;
  lastName?: string;
  bio?: string;
}

let currentUser: User | null = null;
let accounts: User[] = [];

function normalizeUser(user: UserResponse): User {
  return {
    id: user.id,
    username: user.username,
    email: user.email,
    firstName: user.firstName,
    lastName: user.lastName,
    avatarUrl: user.avatarUrl,
    bio: user.bio,
    emailVerified: user.emailVerified,
    role: user.role,
  };
}

function rememberUser(user: User): User {
  currentUser = user;
  const index = accounts.findIndex((account) => account.id === user.id);
  if (index >= 0) accounts[index] = { ...accounts[index], ...user };
  else accounts.push(user);
  return user;
}

export interface UserPublicDto {
  id: string;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  avatarUrl?: string;
}

export class AuthService {
  static async isUsernameAvailable(username: string): Promise<boolean> {
    const response = await profileClient.get<{ available: boolean }>("/auth/username-available", {
      params: { username },
    });
    return response.data.available;
  }

  static async lookupAccount(identifier: string): Promise<AccountLookupResponse> {
    const response = await profileClient.get<AccountLookupResponse>("/auth/account-lookup", { params: { identifier } });
    return response.data;
  }

  static async requestPublicVerification(identifier: string): Promise<void> {
    await profileClient.post("/auth/public-verification/request", { identifier });
  }

  static async confirmPublicVerification(identifier: string, code: string): Promise<void> {
    await profileClient.post("/auth/public-verification/confirm", { identifier, code });
  }

  static async searchUsers(query: string): Promise<UserPublicDto[]> {
    const response = await profileClient.get<UserPublicDto[]>("/search/search", { params: { q: query } });
    return response.data;
  }

  static getStoredAccounts(): User[] {
    return [...accounts];
  }

  static getStoredSession(): User | null {
    return currentUser;
  }

  static async loadAccounts(): Promise<User[]> {
    const response = await profileClient.get<UserResponse[]>("/auth/accounts");
    accounts = response.data.map(normalizeUser);
    return this.getStoredAccounts();
  }

  static async switchAccount(userId: string): Promise<User> {
    const response = await profileClient.post<BrowserAuthResponse>("/auth/switch", { userId });
    const user = rememberUser(normalizeUser(response.data.user));
    await this.loadAccounts();
    return user;
  }

  static async login(payload: LoginPayload): Promise<User> {
    const response = await profileClient.post<BrowserAuthResponse>("/auth/login", {
      ...payload,
      deviceId: DeviceIdManager.getId(),
    });
    const user = rememberUser(normalizeUser(response.data.user));
    await this.loadAccounts();
    return user;
  }

  static async register(payload: RegisterPayload): Promise<RegistrationStartedResponse> {
    const response = await profileClient.post<RegistrationStartedResponse>("/auth/register", payload);
    return response.data;
  }

  static async confirmRegistration(email: string, code: string): Promise<User> {
    const response = await profileClient.post<BrowserAuthResponse>("/auth/confirm-registration", {
      identifier: email,
      code,
      deviceId: DeviceIdManager.getId(),
    });
    const user = rememberUser(normalizeUser(response.data.user));
    await this.loadAccounts();
    return user;
  }

  static async resendRegistrationCode(email: string): Promise<RegistrationStartedResponse> {
    const response = await profileClient.post<RegistrationStartedResponse>("/auth/resend-registration-code", { identifier: email });
    return response.data;
  }

  static async requestEmailChange(currentPassword: string, newEmail: string): Promise<void> {
    await profileClient.post("/users/me/email-change/request", { currentPassword, newEmail });
  }

  static async confirmEmailChange(code: string): Promise<User> {
    await profileClient.post("/users/me/email-change/confirm", { code });
    return this.getMe();
  }

  static async cancelEmailChange(): Promise<void> {
    await profileClient.delete("/users/me/email-change");
  }

  static async refresh(): Promise<User | null> {
    try {
      const response = await profileClient.post<BrowserAuthResponse>("/auth/refresh");
      const user = rememberUser(normalizeUser(response.data.user));
      await this.loadAccounts();
      return user;
    } catch {
      currentUser = null;
      await this.loadAccounts().catch(() => {
        accounts = [];
      });
      return null;
    }
  }

  static async getMe(): Promise<User> {
    const response = await profileClient.get<User>("/users/me");
    return rememberUser(response.data);
  }

  static async updateProfile(payload: UpdateProfilePayload): Promise<User> {
    const response = await profileClient.patch<User>("/users/me", payload);
    return rememberUser(response.data);
  }

  static async uploadAvatar(file: File): Promise<User> {
    const form = new FormData();
    form.append("file", file);
    const response = await profileClient.post<User>("/users/me/avatar", form);
    return rememberUser(response.data);
  }

  static async verifyEmail(code: string): Promise<void> {
    await profileClient.post("/auth/verify-email", { code });
  }

  static async resendVerification(): Promise<void> {
    await profileClient.post("/auth/resend-verification");
  }

  static async forgotPassword(identifier: string): Promise<void> {
    await profileClient.post("/auth/forgot-password", { identifier });
  }

  static async resetPassword(identifier: string, code: string, newPassword: string): Promise<void> {
    await profileClient.post("/auth/reset-password", { identifier, code, newPassword });
  }

  static async changePassword(currentPassword: string, newPassword: string): Promise<User | null> {
    await profileClient.post("/auth/change-password", { currentPassword, newPassword });
    currentUser = null;
    await this.loadAccounts();
    return accounts.length ? this.switchAccount(accounts[0].id) : null;
  }

  static async resetPasswordAndEndSession(identifier: string, code: string, newPassword: string): Promise<void> {
    await this.resetPassword(identifier, code, newPassword);
    currentUser = null;
    accounts = [];
  }

  static async deleteAccount(password: string): Promise<User | null> {
    await profileClient.delete("/auth/account", { data: { password } });
    currentUser = null;
    await this.loadAccounts();
    return accounts.length ? this.switchAccount(accounts[0].id) : null;
  }

  static async logout(): Promise<User | null> {
    await profileClient.post("/auth/logout");
    currentUser = null;
    await this.loadAccounts();
    return accounts.length ? this.switchAccount(accounts[0].id) : null;
  }

  static async logoutAll(): Promise<User | null> {
    await profileClient.post("/auth/logout-all");
    currentUser = null;
    await this.loadAccounts();
    return accounts.length ? this.switchAccount(accounts[0].id) : null;
  }

  static async getSessions(): Promise<AuthSession[]> {
    const response = await profileClient.get<AuthSession[]>("/sessions");
    return response.data;
  }

  static async revokeSession(id: string): Promise<void> {
    await profileClient.delete(`/sessions/${id}`);
  }

  static promptAddAccount(): void {
    currentUser = null;
  }
}
