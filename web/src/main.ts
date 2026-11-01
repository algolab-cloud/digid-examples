// The host page around the digid drop-in flow: the page owns the settings, the language switch
// and the evaluation banner; the flow brings its own screens (intro, camera, result).
import { createFlowEngine, mountFlow, type FlowCompleteEvent, type FlowConfig, type FlowHandle } from "@algolab-africa/digid/ui";
import type { KycResult } from "@algolab-africa/digid";
// INTEGRATION: let Vite bundle the SDK worker (with the engine it imports) as a file of its own.
// Without this, a production build inlines the small worker file as a data: URL, and the worker
// cannot load the engine next to it.
import workerUrl from "@algolab-africa/digid/worker?worker&url";
import { text, type Lang, type TextId } from "./text";
import "./style.css";

// INTEGRATION: the licence key comes from .env.local (VITE_DIGID_LICENCE), never from the code.
// It is checked in the browser against this page's origin. Empty: evaluation mode.
const LICENCE = (import.meta.env.VITE_DIGID_LICENCE ?? "").trim();

// INTEGRATION: the model pack, copied to this site's own origin by vite.config.ts.
// The SDK refuses models from another origin.
const MODELS = `${import.meta.env.BASE_URL}models/`;

const $ = <T extends HTMLElement>(sel: string) => document.querySelector<T>(sel)!;

let lang: Lang = "en";
let flow: FlowHandle | null = null;

// INTEGRATION: one engine for the page. The flow is mounted again when the settings change
// (its config is fixed at mount), and the shared engine keeps the models it already loaded.
// The flow's `engine` config is not applied to an engine passed in, so engine settings go here
// (snake_case). An expired document needs review, as the flow assumes by default.
const engine = createFlowEngine({
  models: MODELS,
  licence: LICENCE || undefined,
  workerUrl,
  config: { expiry_policy: "review" },
});
engine.onChange(updateBanner);

/** The flow config, built from the page's own controls. Every key is in the config reference. */
function flowConfig(): FlowConfig {
  const selfie = $<HTMLInputElement>('input[name="steps"][value="card_selfie"]').checked;
  return {
    flow: {
      steps: selfie ? ["card", "selfie"] : ["card"],
      requireBack: $<HTMLInputElement>("#back").checked ? "optional" : "off",
    },
    language: lang, // the page owns the language; the flow has no switch of its own
    theme: { layout: "minimal" },
    // Never offer the JSON download where end users see the flow: the document is for your backend.
    screens: { result: { details: "collapsed", download: false } },
  };
}

function mount(): void {
  flow?.destroy(); // frees the camera; the shared engine stays loaded
  flow = mountFlow($("#kyc"), {
    engine,
    config: flowConfig(),
    onStep: ({ step }) => {
      // Lock the page's settings while a verification runs.
      $<HTMLFieldSetElement>("#options").disabled = !["intro", "result", "done"].includes(step);
      if (step === "scan_front") $("#result").hidden = true;
    },
    onComplete: ({ result, payload }: FlowCompleteEvent) => {
      showResult(result);
      void sendToBackend(payload);
    },
    onCancel: ({ step, reason }) => console.info(`digid flow left at ${step} (${reason})`),
    // The flow shows its own error screen as well; this is for your logging.
    onError: (error) => console.warn("digid flow error:", error.message),
  });
}

/**
 * TODO(integrator): send the result JSON to your backend and decide there.
 *
 * `payload` is the `digid.kyc/1` document as compact JSON text. Send it as it is, over your own
 * authenticated connection: parsing and re-serialising it can fail the integrity check on your
 * server. Do not log it: it holds personal data. On the server, check the licence state, the
 * document type, freshness and the verdict: https://digid.co.tz/docs/backend-verification
 */
async function sendToBackend(payload: string): Promise<void> {
  void payload;
  // const res = await fetch("/api/kyc", {
  //   method: "POST",
  //   headers: { "content-type": "application/json" },
  //   credentials: "same-origin",
  //   body: payload,
  // });
  // if (!res.ok) throw new Error(`KYC upload failed: ${res.status}`);
}

let lastResult: KycResult | null = null;

/** What the host page shows: the verdict only. The decision is your backend's. */
function showResult(result: KycResult): void {
  lastResult = result;
  const doc = result.driving_licence ? t("doc_licence") : result.card ? t("doc_nida") : t("doc_none");
  const rows: [string, string][] = [
    [t("verdict"), t(`overall_${result.overall}`)],
    [t("document"), doc],
    [t("back_of_card"), result.card_back ? t("read") : t("not_read")],
  ];
  if (result.face) rows.push([t("face"), t(`face_${result.face.verdict}`)]); // the verdict, never the numbers
  if (result.liveness) rows.push([t("liveness"), result.liveness.passed ? t("passed") : t("not_passed")]);
  if (result.evaluation) rows.push([t("mode"), t("evaluation")]);

  const dl = $("#result-rows");
  dl.replaceChildren(
    ...rows.flatMap(([k, v]) => {
      const dt = Object.assign(document.createElement("dt"), { textContent: k });
      const dd = Object.assign(document.createElement("dd"), { textContent: v });
      return [dt, dd];
    }),
  );
  $("#result").dataset.overall = result.overall;
  $("#result").hidden = false;
}

function updateBanner(): void {
  const banner = $("#evaluation");
  // Before the engine has loaded, `licence` is null: show the banner when there is no key.
  const state = engine.licence?.state ?? (LICENCE ? "licensed" : "evaluation");
  banner.hidden = state !== "evaluation";
  banner.textContent = LICENCE ? t("banner_rejected") : t("banner_no_key");
  if (state === "evaluation" && LICENCE && engine.licence?.reason) console.warn("digid licence:", engine.licence.reason);
}

function t(id: TextId | string): string {
  return text[lang][id as TextId] ?? id;
}

function setLanguage(next: Lang): void {
  lang = next;
  document.documentElement.lang = next;
  for (const el of document.querySelectorAll<HTMLElement>("[data-t]")) el.textContent = t(el.dataset.t!);
  for (const b of document.querySelectorAll<HTMLButtonElement>("[data-lang]")) {
    b.setAttribute("aria-pressed", String(b.dataset.lang === next));
  }
  $(".lang").setAttribute("aria-label", t("language"));
  updateBanner();
  if (lastResult) showResult(lastResult);
  // INTEGRATION: the open screen of the flow switches in place.
  flow?.setLanguage(next);
}

for (const b of document.querySelectorAll<HTMLButtonElement>("[data-lang]")) {
  b.addEventListener("click", () => setLanguage(b.dataset.lang as Lang));
}
$("#options").addEventListener("change", () => {
  $("#result").hidden = true;
  mount();
});

setLanguage(lang);
mount();
