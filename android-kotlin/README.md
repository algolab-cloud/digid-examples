# digid Android example (Kotlin, Jetpack Compose)

A small, complete Android app that verifies a NIDA ID card or a Tanzanian driving licence with the
digid Android SDK (`tz.co.digid:digid`). The Android SDK gives you the engine, a CameraX analyzer and
typed results, and you build the screens. This app is a template for those screens.

The screens mirror the digid Flutter and web drop-in flow in its minimal layout, screen for screen
(intro → camera → result, same colours, type, icons and wording in English and Swahili). Copy
them into your app and restyle them as you like: the look lives in `ui/theme/Theme.kt` and the
building blocks in `ui/Kit.kt`.

It shows how to:

- create the engine **once per process**, loading the models off the main thread (`DigidEngine.kt`);
- apply a licence key, and show an evaluation banner when there is none;
- run a **card only** or **card and selfie** flow, with an optional back-of-card step;
- read the **front of the card from one full-resolution photo**, as the Flutter and web flows do:
  `NidaFrameAnalyzer.onFrameCheck` checks the framing on 1080p preview frames, the photo is taken
  once the card is ready (or with the shutter that appears after a few seconds) and read with
  `session.pushStill` (`KycViewModel.kt`);
- feed the back of the card and the selfie live with `NidaFrameAnalyzer`, switching to the front
  camera for the selfie, with `mirrored` left false: camera frames are never mirrored, only the
  preview is (`CameraScreen.kt`);
- show the guidance for each of the engine's codes in the language the **app** chose (English or
  Swahili), a progress bar, the card brackets (green once the card is framed) or the selfie oval, a
  check when a side is accepted, a torch button and a back arrow;
- ask for the camera permission properly: a rationale when declined, a link to the settings once blocked;
- show the result: the verdict, the card fields, the back of the card, and what was checked
  (behind "See details") as Passed / To review / Failed, including the face match verdict and
  liveness (`ResultScreen.kt`). The face match is shown as its verdict only.

```
app/src/main/kotlin/tz/co/digid/example/
├── ExampleApp.kt        Application: owns the one DigidEngine
├── DigidEngine.kt       loads the models in the background, applies the licence, opens sessions
├── KycViewModel.kt      settings, the running session, the camera state, the result
├── MainActivity.kt      Compose host: Start → Intro → Camera → Result (+ a message screen)
└── ui/
    ├── StartScreen.kt   the app's own screen: flow choice, back-of-card switch, EN/SW, evaluation banner
    ├── IntroScreen.kt   before the camera: title, one line, "Open the camera"
    ├── CameraScreen.kt  CameraX preview + analyzer, brackets / oval, guidance box, torch, permission
    ├── ResultScreen.kt  status shield, tags, fields, back of card, what was checked
    ├── ProblemScreen.kt not licensed, evaluation limit, could not start
    ├── Kit.kt           the flow's building blocks (pill, panels, top bar, "Powered by digid", …)
    ├── Icons.kt         Tabler icons (MIT)        Brand.kt  the digid wordmark
    ├── Labels.kt        the app's words for the SDK's codes
    ├── Localized.kt     the app-owned language switch
    └── theme/Theme.kt   colours, type (Inter), radii, spacing
```

## Requirements

- Android Studio, or JDK 17 to 21 for the command line (Gradle 9.1 does not run on newer JDKs;
  Android Studio's bundled JDK works: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` on macOS).
- Android SDK 36. The app runs on Android 7.0 (API 24) and newer.

## The licence key

Without a key the SDK runs in **evaluation mode**: everything works, but results are watermarked
(`KycResult.evaluation`) and never better than `review`. The start screen shows a banner.

To use your key, add it to `local.properties` in this folder (the file is git-ignored; never commit
a key or put it in the code):

```properties
DIGID_LICENCE=digidlic1.…
```

A CI machine can set the `DIGID_LICENCE` environment variable instead. The build passes it to the
app as `BuildConfig.DIGID_LICENCE`.

A licence is bound to the app's package name and signing certificate. Change `applicationId` in
`app/build.gradle.kts` to the package your licence was issued for, and sign with that certificate.
See [Licence keys](https://digid.co.tz/docs/licence-keys).

## Build and run

The SDK comes from Maven Central:

```sh
./gradlew :app:installDebug
```

or open this folder in Android Studio and run the `app` configuration.

## Results are decided on your backend

The result screen is for the person holding the phone. Your server makes the decision: when the
flow is done, `KycViewModel` keeps `session.finishJson()`, the engine's result as JSON text, the same
on every platform. Send it as it is to your backend over your own authenticated connection (see the
`TODO(integrator)` in `KycViewModel.finishAttempt`), and check it there: the licence state, the
document type, freshness, the verdict and the face match. Do not log it: it holds personal data.

See [Verify on your backend](https://digid.co.tz/docs/backend-verification) and
[Quick start: Android](https://digid.co.tz/docs/quick-start-android).

## Notes

- **Debug builds** show a "Finish now (debug)" button on the camera screen. It calls
  `session.finish()` to end the attempt with the best result so far, which is handy to see the result
  screen without a card. Release builds hide it.
- `digid-nomodels` is the variant without models inside: load them with `Digid.fromDirectory(context, dir)`
  in `DigidEngine` (for example after downloading them with Play Asset Delivery).
- The language switch is the app's own: it changes the app's strings (`res/values`,
  `res/values-sw`, the drop-in flow's wording, guidance included). `Digid.guidanceText` is only
  the fallback for a guidance code newer than the app. An app that follows the phone's language
  can drop `ProvideLang`.
- Third-party notices (Tabler Icons, MIT; Inter, SIL OFL): [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
