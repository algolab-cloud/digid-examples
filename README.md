# digid examples

Small, complete apps that verify a Tanzanian NIDA ID card (and a driving licence) on the phone or
in the browser with the [digid](https://digid.co.tz) SDKs. Copy what you need: the example code is
MIT-licensed. The SDKs themselves are proprietary and come from each platform's registry.

| Example | SDK | Registry |
|---|---|---|
| [`android-kotlin/`](android-kotlin) | `tz.co.digid:digid` | [Maven Central](https://central.sonatype.com/artifact/tz.co.digid/digid) |
| `ios-swift/` (coming soon) | `Digid` | [Swift Package Manager](https://github.com/algolab-cloud/digid-ios) |
| [`flutter/`](flutter) | `digid` | [pub.dev](https://pub.dev/packages/digid) |
| [`web/`](web) | `@algolab-africa/digid` | [npm](https://www.npmjs.com/package/@algolab-africa/digid) |

Everything runs on the device: no image or result leaves it unless your app sends it.

## Licence keys

Without a key the SDKs run in **evaluation mode**: everything works, but results are watermarked
and never better than "review". Each example reads its key from a local file that is never
committed; its README says which. A key is bound to your app (Android package name and signing
certificate, iOS bundle identifier, web origin), so it works in an example only once you give the
example your app's identity. See [Licence keys](https://digid.co.tz/docs/licence-keys).

## Decide on your backend

The result screen is for the person holding the phone. Send the SDK's result JSON to your server
and decide there: [Verify on your backend](https://digid.co.tz/docs/backend-verification).

## Docs

[Get the SDK](https://digid.co.tz/docs/get-the-sdk) ·
[Android](https://digid.co.tz/docs/quick-start-android) ·
[iOS](https://digid.co.tz/docs/quick-start-ios) ·
[Flutter](https://digid.co.tz/docs/quick-start-flutter) ·
[Web](https://digid.co.tz/docs/quick-start-web)

## Licence

The example code: [MIT](LICENSE), © 2026 Algolab Limited. Each example lists its third-party
assets (icons, fonts) in its own notices file. The digid SDKs are licensed separately under the
[digid SDK licence](https://digid.co.tz/terms).
