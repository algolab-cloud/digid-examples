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
    func newSession(_ flow: NidaFlow) async throws -> NidaKycSession {
        let kyc = try await loading.value.kyc
        return try await Task.detached { try kyc.newSession(flow) }.value
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
