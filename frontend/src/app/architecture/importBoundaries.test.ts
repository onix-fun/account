import { describe, expect, it } from "vitest";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, extname, resolve, relative } from "node:path";

const currentDir = dirname(fileURLToPath(import.meta.url));
const srcDir = resolve(currentDir, "../..");
const sourceExtensions = new Set([".ts", ".vue"]);
const importPattern = /from\s+["'](@\/[^"']+)["']|import\(["'](@\/[^"']+)["']\)/g;

const forbiddenImports: Array<{ layer: string; forbidden: RegExp }> = [
  { layer: "entities", forbidden: /^@\/(app|pages|features|infra|api)\// },
  { layer: "features", forbidden: /^@\/(app|pages)\// },
  { layer: "pages", forbidden: /^@\/app\// },
  { layer: "shared", forbidden: /^@\/(app|pages|features)\// },
];

function collectFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = resolve(dir, entry);
    if (statSync(path).isDirectory()) return collectFiles(path);
    return sourceExtensions.has(extname(path)) ? [path] : [];
  });
}

function sourceLayer(file: string): string | null {
  const rel = relative(srcDir, file).replaceAll("\\", "/");
  return rel.split("/")[0] || null;
}

describe("frontend import boundaries", () => {
  it("prevents lower layers from importing route and feature owners", () => {
    const violations: string[] = [];

    for (const file of collectFiles(srcDir)) {
      const layer = sourceLayer(file);
      const rule = forbiddenImports.find((item) => item.layer === layer);
      if (!rule) continue;

      const source = readFileSync(file, "utf8");
      for (const match of source.matchAll(importPattern)) {
        const target = match[1] || match[2] || "";
        if (rule.forbidden.test(target)) {
          violations.push(`${relative(srcDir, file)} -> ${target}`);
        }
      }
    }

    expect(violations).toEqual([]);
  });
});
