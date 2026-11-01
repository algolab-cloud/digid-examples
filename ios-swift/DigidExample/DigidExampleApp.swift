import SwiftUI

@main
struct DigidExampleApp: App {
    /// Created once per process; the models start loading right away, off the main thread.
    @StateObject private var engine: DigidEngine
    @StateObject private var model: KycModel

    init() {
        // The key comes from Config/Secrets.xcconfig (git-ignored) through Info.plist.
        let key = (Bundle.main.object(forInfoDictionaryKey: "DigidLicence") as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let engine = DigidEngine(licenceKey: key)
        _engine = StateObject(wrappedValue: engine)
        _model = StateObject(wrappedValue: KycModel(engine: engine))
    }

    var body: some Scene {
        WindowGroup {
            RootView(model: model, engine: engine)
        }
    }
}

/// The app's own start screen, then the screens of the digid drop-in flow (minimal layout):
/// intro → camera → result, and a message screen for blocking problems. One model drives them;
/// a NavigationStack would do the same job.
struct RootView: View {
    @ObservedObject var model: KycModel
    @ObservedObject var engine: DigidEngine

    var body: some View {
        Group {
            switch model.screen {
            case .start:
                StartScreen(model: model, engine: engine)
            case .intro:
                IntroScreen(settings: model.settings, onBack: model.backToStart, onOpenCamera: model.start)
            case .camera:
                CameraScreen(model: model)
            case .result(let result, _, _):
                ResultScreen(result: result, onDone: model.backToStart, onTryAgain: model.start)
            case .message(let problem):
                ProblemScreen(problem: problem, onBack: model.dismissProblem)
            }
        }
        .environment(\.strings, Strings(model.settings.lang))
        .environment(\.locale, Locale(identifier: model.settings.lang.code))
    }
}
