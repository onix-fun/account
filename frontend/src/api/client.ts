import axios from "axios";
import { i18n } from "@/shared/i18n";

export const APP_API_BASE_URL = (
  import.meta.env.VITE_APP_API_URL ||
  import.meta.env.VITE_API_URL ||
  "/api"
).replace(/\/$/, "");
export const PROFILE_BASE_URL = "/api";
export const DOMAIN_BASE_URL = `${APP_API_BASE_URL}/domain`;
export const ANALYTICS_BASE_URL = `${APP_API_BASE_URL}/analytics`;

export function contactsWsBaseUrl(): string {
  const configured = import.meta.env.VITE_CONTACTS_WS_URL as string | undefined;
  if (configured) return configured.replace(/\/$/, "");

  const apiUrl = new URL(APP_API_BASE_URL, window.location.origin);
  apiUrl.protocol = apiUrl.protocol === "https:" ? "wss:" : "ws:";
  apiUrl.pathname = `${apiUrl.pathname.replace(/\/$/, "")}/contacts/ws`;
  return apiUrl.toString().replace(/\/$/, "");
}

export const domainClient = axios.create({
  baseURL: DOMAIN_BASE_URL,
  timeout: 8000,
  withCredentials: true,
});

export const analyticsClient = axios.create({
  baseURL: ANALYTICS_BASE_URL,
  timeout: 8000,
  withCredentials: true,
});

export const profileClient = axios.create({
  baseURL: PROFILE_BASE_URL,
  timeout: 8000,
  withCredentials: true,
});

let csrfToken: string | null = null;
let csrfRequest: Promise<string> | null = null;
let csrfRefreshRequest: Promise<string> | null = null;
let sessionRefreshRequest: Promise<void> | null = null;

function isUnsafeMethod(method?: string): boolean {
  return ["post", "put", "patch", "delete"].includes((method || "").toLowerCase());
}

export async function initializeCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken;
  if (!csrfRequest) {
    csrfRequest = profileClient
      .get<{ csrfToken: string }>("/auth/csrf")
      .then((response) => {
        csrfToken = response.data.csrfToken;
        return csrfToken;
      })
      .finally(() => {
        csrfRequest = null;
      });
  }
  return csrfRequest;
}

function csrfHeader(config: { headers?: unknown }): string | undefined {
  const headers = config.headers as { get?: (name: string) => unknown; [key: string]: unknown } | undefined;
  const value = headers?.get ? headers.get("X-CSRF-Token") : headers?.["X-CSRF-Token"];
  return typeof value === "string" ? value : undefined;
}

async function refreshCsrfToken(staleToken?: string): Promise<string> {
  if (csrfToken && staleToken && csrfToken !== staleToken) return csrfToken;
  if (!csrfRefreshRequest) {
    csrfToken = null;
    csrfRefreshRequest = initializeCsrfToken().finally(() => {
      csrfRefreshRequest = null;
    });
  }
  return csrfRefreshRequest;
}

export async function refreshBrowserSession(): Promise<void> {
  if (!sessionRefreshRequest) {
    sessionRefreshRequest = profileClient
      .post("/auth/refresh")
      .then(() => undefined)
      .finally(() => {
        sessionRefreshRequest = null;
      });
  }
  return sessionRefreshRequest;
}

[domainClient, analyticsClient, profileClient].forEach((client) => {
  client.interceptors.request.use(async (config) => {
    config.headers.set("Accept-Language", i18n.global.locale.value);
    if (isUnsafeMethod(config.method) && !config.url?.startsWith("/auth/token")) {
      config.headers.set("X-CSRF-Token", await initializeCsrfToken());
    }
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    async (error) => {
      const config = error.config as (typeof error.config & { _csrfRetry?: boolean; _sessionRetry?: boolean }) | undefined;
      const data = error.response?.data as { message?: string; code?: string } | undefined;
      if (
        error.response?.status === 403 &&
        (data?.code === "SECURITY_CSRF_INVALID" || data?.message === "Valid CSRF token is required") &&
        config &&
        isUnsafeMethod(config.method) &&
        !config._csrfRetry
      ) {
        config._csrfRetry = true;
        await refreshCsrfToken(csrfHeader(config));
        return client.request(config);
      }
      const isAuthRequest = config?.url?.startsWith("/auth/");
      if (error.response?.status !== 401 || !config || config._sessionRetry || isAuthRequest) {
        return Promise.reject(error);
      }
      config._sessionRetry = true;
      await refreshBrowserSession();
      return client.request(config);
    },
  );
});

export interface ApiFieldError {
  field: string;
  code: string;
  numericCode: number;
}

export interface ApiError {
  code: string;
  numericCode?: number;
  message?: string;
  fieldErrors: ApiFieldError[];
  requestId?: string;
}

const translateError = (code: string): string => {
  const key = `errors.${code}`;
  return i18n.global.te(key) ? i18n.global.t(key) : i18n.global.t("errors.UNKNOWN");
};

export function parseApiError(error: unknown): ApiError {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as Partial<ApiError> | undefined;
    if (data?.code) {
      return {
        code: data.code,
        numericCode: data.numericCode,
        message: data.message,
        fieldErrors: Array.isArray(data.fieldErrors) ? data.fieldErrors : [],
        requestId: data.requestId || error.response?.headers?.["x-correlation-id"],
      };
    }
    const code = error.code === "ECONNABORTED"
      ? "NETWORK_TIMEOUT"
      : error.response?.status && error.response.status >= 500
        ? "SERVICE_UNAVAILABLE"
        : error.response
          ? "UNKNOWN"
          : "NETWORK_UNAVAILABLE";
    return { code, fieldErrors: [], requestId: error.response?.headers?.["x-correlation-id"] };
  }
  return { code: "UNKNOWN", fieldErrors: [] };
}

export function apiErrorMessage(error: unknown): string {
  const parsed = parseApiError(error);
  const message = translateError(parsed.code);
  return parsed.requestId ? `${message} (${i18n.global.t("errors.REQUEST_ID")}: ${parsed.requestId})` : message;
}

export function apiFieldError(error: unknown, field: string): string | null {
  const parsed = parseApiError(error);
  const item = parsed.fieldErrors.find((entry) => entry.field === field);
  return item ? translateError(item.code) : null;
}
