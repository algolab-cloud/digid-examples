# digid web example (Vite, TypeScript)

A small, complete web page that verifies a NIDA ID card or a Tanzanian driving licence in the
browser with the digid web SDK (`@algolab-africa/digid`) and its models
(`@algolab-africa/digid-models`). It uses the SDK's **drop-in flow** in its minimal layout: the
flow brings its own screens (intro, camera or photo, back of the card, selfie, result), and the
page around it is yours. Plain TypeScript and Vite, no framework.

It shows how to:

- copy the model pack to the site's **own origin** at build time, manifest included, without
  committing the models (`vite.config.ts`);
- let Vite bundle the SDK's worker as a file of its own (`src/main.ts`, `?worker&url`);
- read the licence key from a git-ignored `.env.local`, and show an evaluation banner when there
  is none, or when the key is not accepted for this site;
- build the flow config from the page's own controls: **card only** or **card and selfie**, and
  an optional back-of-card step, mounting the flow again when they change while one shared engine
  keeps the models it already loaded;
- own the language on the page: the page's EN / SW switch passes `language` to the flow and calls
  `flow.setLanguage()` when it changes (the flow has no switch of its own);
- lock the page's settings while a verification runs (`onStep`);
- show the verdict on the host page and hand the result JSON to `sendToBackend`, a
  `TODO(integrator)` for your own upload.

```
web/
├── index.html        the host page: header with EN / SW, settings, the flow's mount point, the result
├── src/main.ts       the integration: engine, flow config, mount, callbacks, the backend TODO
├── src/text.ts       the page's own words in English and Swahili
├── src/style.css     the page's look (the flow is themed by its config or --digid-* CSS properties)
├── vite.config.ts    copies the model pack to public/models/ (git-ignored)
└── .env.example      copy to .env.local for your licence key
```

## Requirements

- Node.js 20.19+ or 22.12+ (what Vite 8 needs; tested with Node 26.12.1 and npm 11.20.0).
- A current browser with WebAssembly SIMD and module workers (tested in headless Chrome).
- The camera needs a **secure context**: `https://`, or `http://localhost` while you develop.
  Over plain `http://` on another host (a phone on your LAN, say) the browser offers no camera;
  serve the page over HTTPS for that.

## The licence key

Without a key the SDK runs in **evaluation mode**: everything works, but results are watermarked
(`result.evaluation`) and never better than `review`. The page shows a banner.

To use your key, copy `.env.example` to `.env.local` (git-ignored; never commit a key or put it in
the code) and paste it:

```sh
VITE_DIGID_LICENCE=digidlic1.…
```

Vite builds the value into the page: like any web licence key it is public once the site is
served, and it is bound to the site's origin (`location.origin`), checked in the browser.

- **Local development** (`http://localhost:5173` for `npm run dev`, `http://localhost:4173` for
  `npm run preview`): use your product's **sandbox key**. Local origins (`localhost`,
  `127.0.0.1`, `*.localhost`, `*.test`) work with any sandbox key without being listed, and
  never with a production key.
- **Your site**: list its origin (e.g. `https://kyc.example.co.tz`) on the app your licence
  covers, and build with the production key. A staging site can use the sandbox key once its
  origin is listed.

A key that does not match the origin is not an error: the SDK falls back to evaluation mode, and
the page says so. See [Licence keys](https://digid.co.tz/docs/licence-keys).

## Build and run

```sh
npm install
npm run dev        # http://localhost:5173
npm run build      # typecheck, then build to dist/ (with dist/models/)
npm run preview    # serve dist/ at http://localhost:4173
```

`npm run typecheck` runs the TypeScript check on its own.

## Hosting

Deploy `dist/` as static files on your origin. It holds:

- the page and its script;
- the SDK's worker and its engine (`assets/*.wasm`, which must be served as `application/wasm`);
- `models/`: the model pack, copied as it is from `@algolab-africa/digid-models`, manifest
  included. The SDK checks every file against `MANIFEST.sha256`, and refuses models from another
  origin. Keep the SDK and the models on the same version.

The first visit downloads the engine and the card models; the face models load only when a
selfie starts. Cache them with long-lived headers. A content security policy needs only
`'wasm-unsafe-eval'` beyond the usual:

```text
default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; worker-src 'self'; connect-src 'self'; img-src 'self' blob:; style-src 'self'
```

## Results are decided on your backend

The verdict on the page is for the person in front of the camera. Your server makes the decision:
`onComplete` gets `payload`, the `digid.kyc/1` document as JSON text, the same on every platform.
Send it **as it is** to your backend over your own authenticated connection (see
`sendToBackend` in `src/main.ts`): parsing and re-serialising it can fail the integrity check.
Check it there: the licence state, the document type, freshness, the verdict and the face match.
Do not log it: it holds personal data.

See [Verify on your backend](https://digid.co.tz/docs/backend-verification) and
[Quick start: Web](https://digid.co.tz/docs/quick-start-web).

## Notes

- Everything runs in the browser. The SDK makes no requests but its own files and the models,
  from your origin. Nothing leaves the page unless your code sends it.
- The flow's config (`flowConfig()` in `src/main.ts`) is fixed at mount. To change steps, mount it
  again; the shared engine (`createFlowEngine`) stays loaded. Engine settings (here
  `expiry_policy`) go on the shared engine, as the flow's `engine` config only applies to an
  engine it creates itself.
- `screens.result.download` stays off: the document is for your backend, not for the person
  being verified. Every config key is in the [config reference](https://digid.co.tz/docs/config-reference),
  and the look of the flow is in [Drop-in screens and config](https://digid.co.tz/docs/drop-in-flow).
- This example adds no third-party assets: the page uses the system font, and the flow's own
  icons and wordmark come with the SDK.
