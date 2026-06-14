export interface AccountRuntimeConfig {
  apiBaseUrl: string;
  frontendBasePath: string;
  trustedRedirectOrigins: string[];
}

declare global {
  interface Window {
    __ACCOUNT_CONFIG__?: Partial<AccountRuntimeConfig>;
  }
}

function normalizePath(value: string, fallback: string): string {
  const path = value.trim() || fallback;
  return `/${path.replace(/^\/+|\/+$/g, "")}${path === "/" ? "" : "/"}`.replace("//", "/");
}

const source = window.__ACCOUNT_CONFIG__ || {};

export const runtimeConfig: AccountRuntimeConfig = {
  apiBaseUrl: (source.apiBaseUrl || "/api").replace(/\/$/, ""),
  frontendBasePath: normalizePath(source.frontendBasePath || "/", "/"),
  trustedRedirectOrigins: (source.trustedRedirectOrigins || []).map((origin) => origin.replace(/\/$/, "")),
};
