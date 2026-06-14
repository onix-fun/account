import { mergeConfig } from "vite";
import { defineConfig } from "vitest/config";
import viteConfig from "./vite.config";

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      exclude: ["e2e/**", "node_modules/**", "dist/**"],
      coverage: {
        provider: "v8",
        reporter: ["text", "lcov"],
        include: ["src/**/*.{ts,vue}"],
        exclude: ["src/main.ts", "src/**/*.d.ts"],
        thresholds: {
          lines: 1,
          functions: 1,
          branches: 1,
          statements: 1,
        },
      },
    },
  }),
);
