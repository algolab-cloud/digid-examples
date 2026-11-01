import { cpSync, existsSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig, type Plugin } from "vite";

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);

// The SDK loads its models only from the page's own origin, and checks every file against the
// pack's MANIFEST.sha256. This plugin copies the `pack/` folder of @algolab-africa/digid-models,
// as it is (manifest included), to public/models/ before `vite`, `vite build` and `vite preview`.
// Vite then serves it at /models/ in dev and copies it to dist/models/ in the build.
// public/models/ is git-ignored: the models come from npm, never from the repo.
function digidModels(): Plugin {
  return {
    name: "digid-models",
    config() {
      const pack = join(dirname(require.resolve("@algolab-africa/digid-models/package.json")), "pack");
      const target = join(here, "public", "models");
      if (!existsSync(join(pack, "MANIFEST.sha256"))) {
        throw new Error(`@algolab-africa/digid-models: no MANIFEST.sha256 in ${pack}; run npm install`);
      }
      rmSync(target, { recursive: true, force: true }); // a stale pack would fail the manifest check
      cpSync(pack, target, { recursive: true });
    },
  };
}

export default defineConfig({
  plugins: [digidModels()],
  // The SDK starts its worker as an ES module (src/main.ts imports it with `?worker&url`).
  worker: { format: "es" },
});
