import AVFoundation
import Digid
import SwiftUI

/// What the start screen lets the person choose. The language belongs to the app, not the SDK.
struct Settings: Equatable {
    var withSelfie = true
    var scanBack = false
    var lang: Lang = .device

    var flow: NidaFlow { withSelfie ? .cardThenSelfie(requireBack: scanBack) : .cardOnly(requireBack: scanBack) }
}

/// The live state of the camera screen, from the latest update.
struct Capture {
    var step: StepKind = .scanFront
    var guidance: Guidance = .noCard
    var progress: Float = 0
    var overlay = Overlay()
    /// Frames are reaching the engine (the camera is running).
    var running = false
    /// The step whose capture was just accepted: the frame turns green and a check shows for a
    /// moment (the next step, or the result, follows).
    var accepted: StepKind?
    /// The engine gave up on the back of the card (its step timed out) and moved on.
    var backSkipped = false
    /// Front of the card: the viewfinder check says the card is framed well enough for the photo.
    var ready = false
    /// A photo is being taken and read ("Reading the card…").
    var photographing = false
    /// The manual shutter: offered when the automatic photo has not happened after a while.
    var shutter = false
    var evaluationCapReached = false
}

/// The camera permission and the camera itself.
enum CameraState: Equatable {
    case asking, denied, starting, running, failed
}

/// A blocking problem, shown on a message screen.
enum Problem: Equatable {
    case notLicensed, evaluationCap, startFailed
    /// This month's checks are used up (free plan): verifying starts again on the 1st of
    /// `resumeMonth` (1–12).
    case quotaReached(resumeMonth: Int)
    /// Verifying waits until the phone reaches digid once. `busy`: Try again is connecting.
    case connectionRequired(busy: Bool)
    /// The free plan: this check could not get digid's permission to start (Try again asks again).
    case connectionCheck(busy: Bool)
    /// digid refused to renew the app's licence: connecting again will not help.
    case renewalRefused
}

/// Start (the app's own) → Intro → Camera → Result: the screens of the drop-in flow.
enum Screen {
    case start
    case intro
    case camera
    /// `json` is what the app sends to its backend; `settings` are those the attempt ran with.
    case result(KycResult, json: String, settings: Settings)
    case message(Problem)
}

@MainActor
final class KycModel: ObservableObject {
    @Published private(set) var screen: Screen = .start
    @Published var settings = Settings()
    @Published private(set) var capture = Capture()
    @Published private(set) var cameraState: CameraState = .asking
    @Published private(set) var torchAvailable = false
    @Published var torchOn = false {
        didSet { camera.setTorch(torchOn) }
    }

    let engine: DigidEngine
    let camera = CameraController()

    private var attempt: Attempt?
    /// Counts attempts, so a delayed step of one attempt never lands on the next.
    private var attemptId = 0
    /// Which camera is open (the selfie step switches to the front one).
    private var frontCamera = false
    /// When the back-of-card step began (to tell a read back from a skipped one).
    private var backSince = Date.distantPast

    /// How long the green frame and the check stay after a capture is accepted.
    static let acceptedSeconds = 0.75
    /// When the manual shutter appears, if no photo was taken automatically.
    static let shutterAfterSeconds = 6.0

    init(engine: DigidEngine) {
        self.engine = engine
    }

    /// Start → the flow's intro.
    func openIntro() { screen = .intro }

    /// The intro's back arrow, and Done on the result.
    func backToStart() { screen = .start }

    /// A message screen's action: back to the intro.
    func dismissProblem() { screen = .intro }

    /// Open the camera: a new session for one attempt (the intro's pill, and Try again).
    func start() {
        let settings = self.settings
        attemptId += 1
        let id = attemptId
        Task {
            // Before the camera opens: the licence may already say this attempt cannot run.
            if let problem = await licenceProblem() {
                if id == attemptId { screen = .message(problem) }
                return
            }
            let session: NidaKycSession
            do {
                session = try await engine.newSession(settings.flow)
            } catch DigidError.FeatureNotLicensed(let feature) {
                log.warning("not licensed: \(feature, privacy: .public)")
                screen = .message(.notLicensed)
                return
            } catch DigidError.QuotaReached {
                log.warning("this month's checks are used up")
                screen = .message(await quotaProblem())
                return
            } catch DigidError.ConnectionRequired(let reason) {
                log.warning("connection required: \(reason, privacy: .public)")
                screen = .message(await connectionProblem(ConnectionReason(rawValue: reason)))
                return
            } catch {
                log.error("could not start a session: \(String(describing: error), privacy: .public)")
                screen = .message(.startFailed)
                return
            }
            guard id == attemptId else { return }
            if session.evaluationCapReached {
                screen = .message(.evaluationCap)
                return
            }
            log.info("session started: \(settings.flow.json, privacy: .public)")
            attempt = makeAttempt(session, id: id)
            capture = Capture()
            torchOn = false
            screen = .camera
            armShutter(.scanFront, id: id)
            openCamera()
        }
    }

    /// The problem screen the licence status calls for before a session starts, if any.
    private func licenceProblem() async -> Problem? {
        guard let licence = try? await engine.licence() else { return nil }
        if licence.connectionRequired != nil { return await connectionProblem() }
        if licence.quotaReached { return await quotaProblem() }
        return nil
    }

    /// "Connect to the internet": for this check's permission (`.check`), or to renew and report;
    /// "could not be renewed" once digid has refused a renewal.
    private func connectionProblem(_ reason: ConnectionReason? = nil) async -> Problem {
        if reason == .check { return .connectionCheck(busy: false) }
        return await engine.renewalRefusal() != nil ? .renewalRefused : .connectionRequired(busy: false)
    }

    /// "This month's checks are used up", with the month verifying starts again.
    private func quotaProblem() async -> Problem {
        .quotaReached(resumeMonth: Self.monthAfter((try? await engine.licence())?.quota?.period))
    }

    /// The month (1–12) after `period` (`YYYY-MM`, the licence's quota month); without one, the
    /// month after the current one in East Africa Time, the licence's calendar.
    static func monthAfter(_ period: String?) -> Int {
        var month = period.flatMap { $0.split(separator: "-").dropFirst().first }.flatMap { Int($0) } ?? 0
        if !(1...12).contains(month) {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = TimeZone(secondsFromGMT: 3 * 3600)!
            month = calendar.component(.month, from: Date())
        }
        return month % 12 + 1
    }

    /// The connection screen's Try again: send the usage and renew the licence (off the main
    /// thread), then try to start again, which shows the screen again if verifying still waits.
    /// For a check's permission, starting again is enough: it asks for the same check.
    func retryOnline() {
        let busy: Problem
        switch screen {
        case .message(.connectionRequired(busy: false)): busy = .connectionRequired(busy: true)
        case .message(.connectionCheck(busy: false)): busy = .connectionCheck(busy: true)
        default: return
        }
        screen = .message(busy)
        Task {
            if case .connectionRequired = busy { await engine.online() }
            guard case .message(busy) = screen else { return }
            start()
        }
    }

    /// The licence refused a photo (checks used up, or verifying waits for the licence server):
    /// end the attempt and show the problem screen.
    private func stopForLicence(_ error: DigidError) {
        attemptId += 1
        let id = attemptId
        attempt?.cancel()
        attempt = nil
        camera.stop()
        Task {
            let problem: Problem
            if case .QuotaReached = error { problem = await quotaProblem() } else { problem = await connectionProblem(error.connectionReason) }
            guard id == attemptId, case .camera = screen else { return }
            screen = .message(problem)
        }
    }

    private func makeAttempt(_ session: NidaKycSession, id: Int) -> Attempt {
        // The attempt calls back on its own queue: hop to the main actor, for this attempt only.
        func onMain(_ body: @escaping @MainActor (KycModel) -> Void) {
            DispatchQueue.main.async { [weak self] in
                guard let self, self.attemptId == id else { return }
                body(self)
            }
        }
        return Attempt(session: session, camera: camera, callbacks: .init(
            onCheck: { check in onMain { $0.onCheck(check) } },
            onUpdate: { update, capReached in onMain { $0.onUpdate(update, capReached: capReached) } },
            onPhotographing: { on in onMain { $0.updateCapture { $0.photographing = on; if !on { $0.ready = false } } } },
            onFinish: { result, json in onMain { $0.finishAttempt(result, json: json) } },
            onRefused: { error in onMain { $0.stopForLicence(error) } }
        ))
    }

    // MARK: - The camera

    /// Asks for the camera if needed, then opens the back camera.
    func openCamera() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            bindCamera(front: capture.step == .selfie)
        case .notDetermined:
            cameraState = .asking
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    guard let self, case .camera = self.screen else { return }
                    if granted { self.bindCamera(front: self.capture.step == .selfie) } else { self.cameraState = .denied }
                }
            }
        default:
            // Denied (or restricted): only the Settings app can allow it now.
            cameraState = .denied
        }
    }

    /// Back from the Settings app: the camera may be allowed now.
    func recheckCamera() {
        guard case .camera = screen, cameraState == .denied else { return }
        openCamera()
    }

    private func bindCamera(front: Bool) {
        guard let attempt else { return }
        frontCamera = front
        cameraState = .starting
        let id = attemptId
        camera.start(front: front, delegate: attempt, queue: attempt.queue) { [weak self] result in
            guard let self, self.attemptId == id else { return }
            switch result {
            case .success(let torch):
                self.torchAvailable = torch
                self.cameraState = .running
            case .failure:
                self.cameraState = .failed
            }
        }
    }

    /// The manual shutter.
    func takePhoto() { attempt?.takePhoto() }

    // MARK: - Updates

    private func onCheck(_ check: FrameCheck) {
        updateCapture {
            $0.guidance = check.guidance
            $0.overlay = check.overlay
            $0.ready = check.ready
            $0.progress = 0
            $0.running = true
        }
    }

    private func onUpdate(_ update: StepUpdate, capReached: Bool) {
        let previous = capture
        if previous.step != update.step || previous.guidance != update.guidance {
            log.info("step=\(update.step.rawValue, privacy: .public) guidance=\(update.guidance.rawValue, privacy: .public) progress=\(update.progress)")
        }
        let moved = previous.step != update.step && update.step != .done && update.step != .unknown
        let now = Date()
        // The back of the card is live: when the engine moves on before reading it, its step timed out.
        let skipped = moved && previous.step == .scanBack
            && now.timeIntervalSince(backSince) > Double(max(engine.stepTimeoutS - 5, 1))
        if moved && update.step == .scanBack { backSince = now }
        updateCapture {
            $0.step = update.step
            $0.guidance = update.guidance
            $0.progress = update.progress
            $0.overlay = update.overlay
            $0.running = true
            if moved {
                $0.accepted = previous.step
                $0.backSkipped = skipped
                $0.shutter = false
            }
            $0.ready = false
            $0.evaluationCapReached = update.guidance == .licenceRequired || capReached
        }
        guard moved else { return }
        let id = attemptId
        if update.step == .scanBack { armShutter(.scanBack, id: id) }
        // The selfie step uses the front camera; the same session continues.
        if update.step == .selfie && !frontCamera { bindCamera(front: true) }
        // A side was accepted: the check shows for a moment, then the next step's guidance.
        Task {
            try? await Task.sleep(nanoseconds: UInt64(Self.acceptedSeconds * 1e9))
            if id == attemptId { updateCapture { $0.accepted = nil } }
        }
    }

    /// Offer the manual shutter if `step` is still waiting for a photo after a while.
    private func armShutter(_ step: StepKind, id: Int) {
        Task {
            try? await Task.sleep(nanoseconds: UInt64(Self.shutterAfterSeconds * 1e9))
            guard id == attemptId else { return }
            updateCapture { if $0.step == step && $0.accepted == nil { $0.shutter = true } }
        }
    }

    /// Changes the camera screen's state; a no-op once the screen has moved on.
    private func updateCapture(_ change: (inout Capture) -> Void) {
        guard case .camera = screen else { return }
        change(&capture)
    }

    /// The flow is done: keep the JSON for the backend; the frame turns green and the check
    /// shows for a moment, then the result.
    private func finishAttempt(_ result: KycResult, json: String) {
        let lastStep = capture.step
        attempt = nil
        camera.stop()
        // Outcomes only: no field values.
        let document = result.drivingLicence != nil ? "licence" : result.card != nil ? "nida" : "none"
        let checks = result.checks.map { "\($0.id.rawValue):\($0.status.rawValue)" }.joined(separator: ",")
        log.info("result overall=\(result.overall.rawValue, privacy: .public) evaluation=\(result.evaluation) document=\(document, privacy: .public) checks=\(checks, privacy: .public) face=\(result.face?.verdict.rawValue ?? "-", privacy: .public) liveness=\(result.liveness.map { String($0.passed) } ?? "-", privacy: .public) back=\(result.cardBack != nil)")

        // TODO(integrator): POST `json` to your backend over your own authenticated connection
        // and decide there (https://digid.co.tz/docs/backend-verification). For example:
        //   var request = URLRequest(url: yourBackendURL)
        //   request.httpMethod = "POST"
        //   request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        //   request.httpBody = Data(json.utf8)
        //   let (_, response) = try await URLSession.shared.data(for: request)

        let step = (lastStep == .done || lastStep == .unknown) ? StepKind.scanFront : lastStep
        updateCapture {
            $0.accepted = step
            $0.backSkipped = step == .scanBack && result.cardBack == nil
        }
        let id = attemptId
        let settings = self.settings
        Task {
            try? await Task.sleep(nanoseconds: UInt64(Self.acceptedSeconds * 1e9))
            // Only if the person is still on this attempt's camera screen (they may have left meanwhile).
            guard id == attemptId, case .camera = screen else { return }
            screen = .result(result, json: json, settings: settings)
        }
    }

    /// End the attempt now with the best result so far (`finish()` works at any step). The
    /// example offers it in debug builds, to see the result screen without a card.
    func finishNow() { attempt?.finishNow() }

    /// The camera's back arrow: end the attempt, back to the intro.
    func cancel() {
        attemptId += 1
        attempt?.cancel()
        attempt = nil
        camera.stop()
        screen = .intro
    }
}
