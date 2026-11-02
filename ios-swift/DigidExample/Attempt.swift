import AVFoundation
import Digid
import Foundation

/// One verification attempt: the session and everything that calls into it.
///
/// Every call into the session (frames, the photo, finish) runs on `queue`, in order: a session
/// must not be used from two threads at once. The camera delivers its frames on the same queue.
///
/// The front of the card is read from a photo, as in the digid drop-in flows: during that step
/// frames only run the cheap viewfinder check (`checkFrame`), and once the card is framed one
/// full-resolution photo is taken and read with `pushStill`. The back of the card and the selfie
/// are read live, through the SDK's `NidaCaptureAdapter`.
final class Attempt: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, @unchecked Sendable {
    /// What the attempt tells the screen. Called on `queue`.
    struct Callbacks {
        /// The viewfinder check of one frame on the front-of-card step.
        var onCheck: (FrameCheck) -> Void
        /// The engine's update after a frame or a photo, and whether the evaluation cap is reached.
        var onUpdate: (StepUpdate, Bool) -> Void
        /// A photo is being taken and read (true), or is done (false).
        var onPhotographing: (Bool) -> Void
        /// The flow is done: the typed result and the same result as JSON, for the backend.
        var onFinish: (KycResult, String) -> Void
        /// The licence refused a photo: `.QuotaReached` or `.ConnectionRequired`.
        var onRefused: (DigidError) -> Void
    }

    let session: NidaKycSession
    let queue: DispatchQueue
    private let camera: CameraController
    private let callbacks: Callbacks

    /// The SDK's frame adapter: BGRA frames into the session (back of the card, selfie).
    private lazy var adapter = NidaCaptureAdapter(
        session: session,
        rotationDeg: 0, // the camera delivers upright frames
        mirrored: false, // and never mirrored ones
        onError: { log.error("frame failed: \(String(describing: $0), privacy: .public)") },
        onUpdate: { [weak self] update in self?.handle(update) }
    )

    // The state below is read and written on `queue` only.
    private var step: StepKind = .scanFront
    private var ended = false
    /// Viewfinder checks in a row that said ready; at `readyFrames` the photo is taken.
    private var readyStreak = 0
    /// A photo is being taken or read: frames are dropped meanwhile.
    private var photographing = false
    /// After a photo that was not accepted, frames resume at this time.
    private var pausedUntil: CFAbsoluteTime = 0

    /// Viewfinder checks in a row that must say ready before the photo is taken.
    static let readyFrames = 3
    /// After a photo that was not accepted, a pause before the viewfinder resumes.
    static let photoPause: CFTimeInterval = 0.9

    init(session: NidaKycSession, camera: CameraController, callbacks: Callbacks) {
        self.session = session
        self.camera = camera
        self.callbacks = callbacks
        queue = DispatchQueue(label: "digid-example.session", qos: .userInitiated)
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        // After the attempt ended, or while a photo is taken and read.
        guard !ended, !photographing, CFAbsoluteTimeGetCurrent() >= pausedUntil else { return }
        if step == .scanFront {
            check(sampleBuffer)
        } else {
            adapter.captureOutput(output, didOutput: sampleBuffer, from: connection)
        }
    }

    /// The front of the card: is it framed well enough for the photo? (No text is read.)
    private func check(_ sampleBuffer: CMSampleBuffer) {
        guard let pixels = CMSampleBufferGetImageBuffer(sampleBuffer), let frame = NidaImage.bgra(pixels) else { return }
        do {
            let check = try session.checkFrame(frame.data, width: frame.width, height: frame.height, format: .bgra8,
                                               rowStride: frame.rowStride, rotationDeg: 0)
            readyStreak = check.ready ? readyStreak + 1 : 0
            callbacks.onCheck(check)
            if readyStreak >= Self.readyFrames { takePhoto() }
        } catch {
            log.error("frame check failed: \(String(describing: error), privacy: .public)")
        }
    }

    /// The automatic photo, and the manual shutter: one full-resolution photo of the card.
    func takePhoto() {
        queue.async { [self] in
            guard !ended, !photographing else { return }
            photographing = true
            callbacks.onPhotographing(true)
            camera.capturePhoto { [weak self] image in
                guard let self else { return }
                self.queue.async { self.read(image) }
            }
        }
    }

    /// On `queue`: read the photo. If it was not read well enough, the viewfinder resumes and
    /// another photo follows (the engine combines the reads).
    private func read(_ image: NidaImage?) {
        defer {
            readyStreak = 0
            pausedUntil = CFAbsoluteTimeGetCurrent() + Self.photoPause
            photographing = false
            if !ended { callbacks.onPhotographing(false) }
        }
        guard !ended, let image else { return }
        do {
            let update = try session.pushStill(image)
            log.info("photo \(update.stepDone ? "accepted" : "not read", privacy: .public): step=\(update.step.rawValue, privacy: .public) guidance=\(update.guidance.rawValue, privacy: .public)")
            handle(update)
        } catch let error as DigidError where error == .QuotaReached || error.connectionReason != nil {
            log.warning("photo refused by the licence: \(String(describing: error), privacy: .public)")
            ended = true
            callbacks.onRefused(error)
        } catch {
            log.error("photo not read: \(String(describing: error), privacy: .public)")
        }
    }

    /// On `queue`, after each frame or photo.
    private func handle(_ update: StepUpdate) {
        guard !ended else { return }
        if update.result != nil {
            finish()
            return
        }
        if update.step != .done && update.step != .unknown { step = update.step }
        callbacks.onUpdate(update, session.evaluationCapReached)
    }

    /// Ends the attempt now with the best result so far (the flow's own end, and the debug
    /// "Finish now"). Safe to call from any thread.
    func finishNow() {
        queue.async { [self] in finish() }
    }

    /// On `queue`.
    private func finish() {
        guard !ended else { return }
        ended = true
        do {
            // finishJSON() is the engine's result as JSON text, the same on every platform: send
            // it to your backend as it is. Do not log it: it holds personal data.
            let json = try session.finishJSON()
            let result = try KycResult.fromJSON(json)
            callbacks.onFinish(result, json)
        } catch {
            log.error("finish failed: \(String(describing: error), privacy: .public)")
        }
    }

    /// Stops using the session (the camera's back arrow). Queued behind any frame in progress.
    func cancel() {
        queue.async { [self] in ended = true }
    }
}
