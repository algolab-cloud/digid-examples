import Digid
import DigidModels
import Foundation
import os

let log = Logger(subsystem: "tz.co.digid.example", category: "DigidExample")

/// The one `NidaKyc` engine of the process. Loading the models takes a second or more, so it
/// starts on a background thread as soon as the app starts and the screens observe `state`.
@MainActor
final class DigidEngine: ObservableObject {
    enum State {
        case loading
        /// `stepTimeoutS`: how long a step runs before the engine gives up on it (the back of
        /// the card is then skipped).
        case ready(licence: LicenceStatus, hasFaceModels: Bool, stepTimeoutS: Float)
        case failed(String)
    }

    @Published private(set) var state: State = .loading

    /// The key from Secrets.xcconfig (empty without one), passed through Info.plist.
    let licenceKey: String

    private let loading: Task<Loaded, Error>

    /// The engine and what the screens need to know about it, read off the main thread.
    private struct Loaded: @unchecked Sendable {
        let kyc: NidaKyc
        let licence: LicenceStatus
        let hasFaceModels: Bool
        let stepTimeoutS: Float
    }

    init(licenceKey: String) {
        self.licenceKey = licenceKey
        // Every engine call here can block for a while (the first one waits for the models):
        // none of them runs on the main thread.
        loading = Task.detached(priority: .userInitiated) {
            let t0 = Date()
            let kyc = try NidaKyc(modelDirectory: DigidModels.directory)
            // applyLicence never fails on a bad key: the engine stays in evaluation mode, with a reason.
            let licence = licenceKey.isEmpty ? kyc.licence : try kyc.applyLicence(licenceKey)
            let loaded = Loaded(kyc: kyc, licence: licence, hasFaceModels: kyc.hasFaceModels, stepTimeoutS: Self.stepTimeout(kyc))
            log.info("engine \(NidaKyc.apiVersion, privacy: .public) ready in \(Int(Date().timeIntervalSince(t0) * 1000)) ms: licence=\(licence.state.rawValue, privacy: .public) reason=\(licence.reason ?? "-", privacy: .public) faceModels=\(loaded.hasFaceModels)")
            return loaded
        }
        Task { await settle() }
    }

    private func settle() async {
        do {
            let loaded = try await loading.value
            state = .ready(licence: loaded.licence, hasFaceModels: loaded.hasFaceModels, stepTimeoutS: loaded.stepTimeoutS)
        } catch {
            log.error("engine failed to load: \(String(describing: error), privacy: .public)")
            state = .failed(error.localizedDescription)
        }
    }

    /// A new session for one verification attempt. Waits for the models if they are still loading.
    /// On the free plan each check asks digid for permission before it starts: `prepare()` fetches
    /// it here, before the camera opens, so the first frame never waits on the network (it throws
    /// what the first push would: `.ConnectionRequired(reason: "check")` offline, `.QuotaReached`
    /// once the month's checks are used up). Paid plans return at once.
    func newSession(_ flow: NidaFlow) async throws -> NidaKycSession {
        let kyc = try await loading.value.kyc
        let session = try await Task.detached { try kyc.newSession(flow) }.value
        try await session.prepare()
        return session
    }

    /// The licence status now: the monthly checks left (free plan) and whether verifying waits
    /// until the phone reaches digid. Checked before the camera opens.
    func licence() async throws -> LicenceStatus {
        let kyc = try await loading.value.kyc
        return await Task.detached { kyc.licence }.value
    }

    /// digid refused to renew the licence (connecting again will not help); nil otherwise.
    func renewalRefusal() async -> String? {
        try? await loading.value.kyc.lastRenewalRefusal?.code
    }

    /// The phone is online: send the usage counts and renew the licence if it needs it. The SDK
    /// also does this by itself in the background; the "connect to the internet" screen calls it
    /// from its Try again button.
    func online() async {
        guard let kyc = try? await loading.value.kyc else { return }
        await Task.detached { _ = await kyc.online() }.value
    }

    var stepTimeoutS: Float {
        if case .ready(_, _, let t) = state { return t }
        return 30
    }

    /// The engine's settings are a JSON text; only the step timeout matters to the screens.
    private nonisolated static func stepTimeout(_ kyc: NidaKyc) -> Float {
        guard let json = try? kyc.configJSON(),
              let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any],
              let value = object["step_timeout_s"] as? NSNumber
        else { return 30 }
        return value.floatValue
    }
}
