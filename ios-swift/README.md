# digid iOS example (Swift, SwiftUI)

A small, complete iPhone app that verifies a NIDA ID card or a Tanzanian driving licence with the
digid iOS SDK (the Swift package [`Digid`](https://github.com/algolab-cloud/digid-ios)). The iOS SDK
gives you the engine, an AVFoundation adapter and typed results, and you build the screens. This
app is a template for those screens.

The screens follow the digid drop-in flow in its minimal layout, screen for screen, like the
[Android example](../android-kotlin): start → intro → camera → result, with the same colours, icons
(SF Symbols here) and wording in English and Swahili. Copy them into your app and restyle them as
you like: the look lives in `UI/Theme.swift` and the building blocks in `UI/Kit.swift`.

It shows how to:

- create the engine **once per process**, loading the models off the main thread (`DigidEngine.swift`);
- apply a licence key, and show an evaluation banner when there is none;
- run a **card only** or **card and selfie** flow, with an optional back-of-card step;
- read the **front of the card from one full-resolution photo**, as the Flutter, web and Android
  flows do: `session.checkFrame` checks the framing on 1080p video frames, the photo is taken once
  the card is ready (or with the shutter that appears after a few seconds) and read with
  `session.pushStill` (`Attempt.swift`);
- feed the back of the card and the selfie live through the SDK's `NidaCaptureAdapter`, switching
  to the front camera for the selfie, with `mirrored` left false: the video buffers are never
  mirrored, only the preview is (`Camera.swift`);
- show the guidance for each of the engine's codes in the language the **app** chose (English or
  Swahili), a progress bar, the card brackets (green once the card is framed) or the selfie oval, a
  check when a side is accepted, a torch button and a back arrow;
- ask for the camera permission, and link to the Settings app once it was declined;
- show the result: the verdict, the card fields, the back of the card, and what was checked
  (behind "See details") as Passed / To review / Failed, including the face match verdict and
  liveness (`UI/ResultScreen.swift`). Outcomes only: the face match is shown as its verdict.

```
DigidExample/
├── DigidExampleApp.swift  @main: owns the one engine; Start → Intro → Camera → Result (+ a message screen)
├── DigidEngine.swift      loads the models in the background, applies the licence, opens sessions
├── KycModel.swift         settings, the running attempt, the camera state, the result
├── Attempt.swift          one session: frame checks, the photo, live frames, finish (one serial queue)
├── Camera.swift           AVFoundation: 1080p BGRA frames, full-resolution photo, torch, preview
├── Info.plist             camera usage text, the licence key from the build settings
├── en.lproj/, sw.lproj/   Localizable.strings (the app's words) and InfoPlist.strings
└── UI/
    ├── StartScreen.swift  the app's own screen: flow choice, back-of-card switch, EN/SW, evaluation banner
    ├── IntroScreen.swift  before the camera: title, one line, "Open the camera"
    ├── CameraScreen.swift preview, brackets / oval, guidance box, shutter, torch, permission
    ├── ResultScreen.swift status shield, tags, fields, back of card, what was checked
    ├── ProblemScreen.swift not licensed, evaluation limit, could not start
    ├── Kit.swift          the flow's building blocks (pill, panels, top bar, "Powered by digid", …)
    ├── Labels.swift       the app's words for the SDK's codes
    ├── Localized.swift    the app-owned language switch
    ├── Brand.swift        the digid wordmark
    └── Theme.swift        colours, type, radii, spacing
```

## Requirements

- Xcode (built and tested with Xcode 27.2; the code uses the Swift 5 language mode). The app runs
  on iOS 15 and newer, iPhone only.
- The SDK, version 0.1.2 or newer (`checkFrame` and `pushStill` arrived in 0.1.2), resolved by
  Xcode from GitHub.
- A real iPhone to verify a card: the simulator has no camera, so there the app stops at "The
  camera could not continue".

## The licence key

Without a key the SDK runs in **evaluation mode**: everything works, but results are watermarked
(`KycResult.evaluation`) and never better than `review`. The start screen shows a banner.

To use your key, copy `Config/Secrets.example.xcconfig` to `Config/Secrets.xcconfig` (git-ignored;
never commit a key or put it in the code) and paste your key:

```
DIGID_LICENCE = digidlic1.…
```

The build passes it to the app through `Info.plist` (`DigidLicence`). A CI machine can write that
file from a secret before building.

A key is bound to your app's **bundle identifier**. Change `PRODUCT_BUNDLE_IDENTIFIER` in
`project.yml` (or in Xcode, under Signing & Capabilities) from `tz.co.digid.example.ios` to the
bundle identifier your key was issued for. See [Licence keys](https://digid.co.tz/docs/licence-keys).

## Build and run

Open `DigidExample.xcodeproj` in Xcode, pick your team under Signing & Capabilities, and run on
your iPhone. Xcode fetches the SDK from GitHub the first time.

From the command line:

```sh
# Simulator (no camera: useful for the screens before the camera)
xcodebuild -project DigidExample.xcodeproj -scheme DigidExample \
  -destination 'generic/platform=iOS Simulator' build

# Device build, without signing (to check it compiles)
xcodebuild -project DigidExample.xcodeproj -scheme DigidExample \
  -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

The project is generated from `project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen).
After changing `project.yml`, or adding files, run `xcodegen generate`.

## Results are decided on your backend

The result screen is for the person holding the phone. Your server makes the decision: when the
flow is done, `Attempt` calls `session.finishJSON()`, the engine's result as JSON text, the same
on every platform. Send it as it is to your backend over your own authenticated connection (see the
`TODO(integrator)` in `KycModel.finishAttempt`), and check it there: the licence state, the
document type, freshness, the verdict and the face match. Do not log it: it holds personal data.

See [Verify on your backend](https://digid.co.tz/docs/backend-verification) and
[Quick start: iOS](https://digid.co.tz/docs/quick-start-ios).

## Notes

- **Debug builds** show a "Finish now (debug)" link on the camera screen. It ends the attempt with
  the best result so far, which is handy to see the result screen without a card. Release builds
  hide it.
- **Frames are upright.** The video connection is set to portrait, so frames reach the engine
  upright and are pushed with `rotationDeg: 0`, from either camera. If you keep the sensor's own
  landscape orientation instead (less work for the camera), pass the rotation that makes each
  frame upright.
- **One queue per attempt.** A session must be used from one thread at a time: the camera delivers
  its frames on the attempt's queue, and the photo, `finish` and every other session call run there too.
- **Engine calls block.** Creating the engine, applying the licence and opening a session can take
  a moment: none of them runs on the main thread.
- The language switch is the app's own: it changes the app's strings (`en.lproj`, `sw.lproj`).
  `NidaKyc.text(for:lang:)` is only the fallback for a guidance code newer than the app. The
  system's camera prompt follows the phone's language. An app that follows the phone's language
  can use `NSLocalizedString` and drop `Strings`.
- `DigidModels` puts the models in the app. To deliver them another way (for example with
  on-demand resources), drop that product and pass their folder to `NidaKyc(modelDirectory:)`.
- The icons are SF Symbols; the type is the system font (the drop-in flow uses Inter). The example
  bundles no third-party assets.
