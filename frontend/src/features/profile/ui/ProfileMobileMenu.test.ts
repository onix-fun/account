// @vitest-environment jsdom

import { mount } from "@vue/test-utils";
import { createI18n } from "vue-i18n";
import { describe, expect, it } from "vitest";
import ProfileMobileMenu from "./ProfileMobileMenu.vue";

const i18n = createI18n({
  legacy: false,
  locale: "en",
  messages: {
    en: {
      social: { search: "Search" },
      profile: {
        profile: "Profile",
        requests: "Requests",
        close: "Close friends",
        blocked: "Blocked",
        settings: "Settings",
        sessions: "Sessions",
        system: "System",
        menu: {
          search: "Find people.",
          profile: "Edit profile.",
          requests: "Follow requests.",
          close: "Close friends.",
          blocked: "Blocked users.",
          settings: "Privacy and notifications.",
          sessions: "Active sessions.",
          system: "System options.",
        },
      },
    },
  },
});

describe("ProfileMobileMenu", () => {
  it("renders search first and opens sections as query-backed views", async () => {
    const wrapper = mount(ProfileMobileMenu, {
      global: { plugins: [i18n] },
    });

    const rows = wrapper.findAll(".profile-menu-row");
    expect(rows).toHaveLength(8);
    expect(rows[0].text()).toContain("Search");
    expect(rows[1].text()).toContain("Profile");

    await rows[0].trigger("click");
    await rows[1].trigger("click");
    await rows[4].trigger("click");

    expect(wrapper.emitted("search")).toHaveLength(1);
    expect(wrapper.emitted("openView")).toEqual([["profile"], ["blocked"]]);
  });
});
