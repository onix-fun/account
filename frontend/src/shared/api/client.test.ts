// @vitest-environment jsdom

import { beforeAll, describe, expect, it } from "vitest";

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

describe("API errors", () => {
  it("parses structured backend errors and localizes their field code", async () => {
    const { apiErrorMessage, parseApiError } = await import("./client");
    const cause = {
      isAxiosError: true,
      response: {
        data: {
          code: "AUTH_INVALID_PASSWORD",
          numericCode: 2002,
          message: "Invalid password",
          fieldErrors: [{ field: "password", code: "AUTH_INVALID_PASSWORD", numericCode: 2002 }],
          requestId: "request-1",
        },
        headers: {},
      },
    };

    expect(parseApiError(cause)).toMatchObject({
      code: "AUTH_INVALID_PASSWORD",
      numericCode: 2002,
      requestId: "request-1",
    });
    expect(apiErrorMessage(cause)).toContain("Incorrect password");
    expect(apiErrorMessage(cause)).toContain("request-1");
  });
});
