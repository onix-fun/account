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
        connections: "Connections",
        requests: "Requests",
        close: "Close friends",
        blocked: "Blocked",
        settings: "Settings",
        sessions: "Sessions",
        system: "System",
        menu: {
          search: "Find people.",
          profile: "Edit profile.",
          connections: "Followers and following.",
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
  it("renders profile sections as query-backed views", async () => {
    const wrapper = mount(ProfileMobileMenu, {
      global: { plugins: [i18n] },
    });

    const rows = wrapper.findAll(".profile-menu-row");
    expect(rows).toHaveLength(7);
    expect(rows[0].text()).toContain("Profile");

    await rows[0].trigger("click");
    await rows[3].trigger("click");

    expect(wrapper.emitted("openView")).toEqual([["profile"], ["blocked"]]);
  });

  it("does not render follow requests as a separate section", () => {
    const wrapper = mount(ProfileMobileMenu, {
      global: { plugins: [i18n] },
    });

    expect(wrapper.text()).not.toContain("Requests");
    expect(wrapper.findAll(".profile-menu-row")).toHaveLength(7);
  });
});
