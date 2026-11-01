# digid Flutter example

A small, complete Flutter app that verifies a NIDA ID card or a Tanzanian driving licence with the
digid Flutter plugin ([`digid` on pub.dev](https://pub.dev/packages/digid)). It uses the plugin's
ready-made screens, the **drop-in flow**, in its minimal layout, as an app would: the app keeps
its own start screen, opens the flow, and gets the result back.

It shows how to:

- create the engine **once per process** with the licence key (`lib/main.dart`);
- show an **evaluation banner** when the SDK runs without a valid licence;
- build the flow's config for **ID card only** or **ID card and selfie**, with an optional
  **back of the card** step, and open the flow with `DigidFlow.start` (`lib/home_page.dart`);
- own the **language** in the app: the EN / SW control is the app's own, and the open flow follows
  it through `DigidFlow.start(language: …)` (`lib/strings.dart`);
- hand the result to your backend (`lib/backend.dart`, a `TODO(integrator)`), and show the person
  a short summary: the verdict, the document type, the selfie and liveness verdicts
  (`lib/result_page.dart`). The face match is shown as its verdict only.

```
lib/
├── main.dart         the licence key, the engine (created once), the app's theme
├── home_page.dart    the app's start screen; builds the config and opens the flow
├── result_page.dart  what the flow returned, for the person holding the phone
├── backend.dart      sendToBackend(payload): the TODO for your server call
└── strings.dart      the app-owned language (EN / SW)
```

To embed the flow in one of your own pages instead of pushing it, use the `DigidFlowView` widget
with the same config and callbacks, and rebuild it with `config.copyWith(language: …)` when your
language control changes.

## Requirements

- Flutter 3.47 stable (Dart 3.13) or newer. This example was built with Flutter 3.47.5.
- **Android:** 7.0 (API 24) or newer: `minSdk = 24` in `android/app/build.gradle.kts`.
- **iOS:** 15.0 or newer: `platform :ios, '15.0'` in `ios/Podfile` (and the Runner target).
- **Camera permission.** The plugin declares no permissions; the app does:
  - Android: `<uses-permission android:name="android.permission.CAMERA" />` in
    `android/app/src/main/AndroidManifest.xml` (the manifest also removes the audio and storage
    permissions the camera plugin would add: the flow records no audio and saves nothing);
  - iOS: `NSCameraUsageDescription` in `ios/Runner/Info.plist`.

  The flow shows the system prompt when the camera opens. If the user refuses, the flow says so and
  offers the app's settings.

## The licence key

Without a key the SDK runs in **evaluation mode**: everything works, but results are watermarked
(`DigidFlowResult.evaluation`) and never better than `review`. The start screen shows a banner.

Pass your key at build time; never put it in the code or commit it:

```sh
flutter run --dart-define=DIGID_LICENCE=digidlic1.…
```

or keep it in a local file, `digid.local.json` in this folder (git-ignored):

```json
{ "DIGID_LICENCE": "digidlic1.…" }
```

```sh
flutter run --dart-define-from-file=digid.local.json
```

Your app can also fetch the key from your backend at start-up and pass it to
`DigidFlow.createEngine(licence: …)`.

A key is **bound to your app's identity**, checked on the phone. Change the ids to the ones your key
was issued for:

- Android: `applicationId` in `android/app/build.gradle.kts` (now `tz.co.digid.example.flutter`),
  and sign with your certificate;
- iOS: the Runner target's bundle identifier (`PRODUCT_BUNDLE_IDENTIFIER`, now
  `tz.co.digid.example.flutter`).

With a key for another app id, the banner says the key is not valid for this app. See
[Licence keys](https://digid.co.tz/docs/licence-keys).

## Build and run

```sh
flutter pub get
flutter run --dart-define-from-file=digid.local.json   # or --dart-define=DIGID_LICENCE=…, or no key
```

Release builds:

```sh
flutter build apk --release --dart-define-from-file=digid.local.json
flutter build ipa --dart-define-from-file=digid.local.json
```

The release APK is signed with the debug key, as `flutter create` sets it up. Add your own
signing config, the one your licence is bound to.

## Results are decided on your backend

The result screens are for the person holding the phone. Your server makes the decision: when the
flow is done, `DigidFlowResult.payload` is the result document (`digid.kyc/1`) as JSON text, the
same on every digid SDK. Send it as it is to your backend over your own authenticated connection
(`sendToBackend` in `lib/backend.dart`), and check it there: the licence state, the document type,
freshness, the verdict and the face match. Do not parse and re-serialise it first, and do not log
it: it holds personal data.

See [Verify on your backend](https://digid.co.tz/docs/backend-verification) and
[Quick start: Flutter](https://digid.co.tz/docs/quick-start-flutter). Every config key is in the
[config reference](https://digid.co.tz/docs/config-reference).

## Notes

- The flow logs steps and guidance codes in debug builds only, never field values. The example's
  own `onStepChanged` / `onError` logging does the same.
- No third-party assets: the app uses Material icons and the Inter font the plugin ships
  (`packages/digid/Inter`).
