// @vitest-environment jsdom

import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { createI18n } from "vue-i18n";
import PasswordInput from "./PasswordInput.vue";

const i18n = createI18n({
  legacy: false,
  locale: "en",
  messages: {
    en: {
      auth: {
        showPassword: "Show password",
        hidePassword: "Hide password",
      },
    },
  },
});

describe("PasswordInput", () => {
  it("toggles password visibility without changing the value", async () => {
    const wrapper = mount(PasswordInput, {
      props: { modelValue: "secret123" },
      attrs: { class: "input", autocomplete: "current-password" },
      global: { plugins: [i18n] },
    });

    const input = wrapper.get("input");
    expect(input.attributes("type")).toBe("password");
    expect(input.attributes("autocomplete")).toBe("current-password");

    await wrapper.get("button").trigger("click");

    expect(input.attributes("type")).toBe("text");
    expect(input.element.value).toBe("secret123");
  });
});
