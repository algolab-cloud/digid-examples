import SwiftUI

/// A blocking problem before the camera opens, on the flow's message screen. The connection
/// screen's Try again (`onRetry`) sends the usage and renews the licence, then starts again.
struct ProblemScreen: View {
    @Environment(\.strings) private var s
    let problem: Problem
    let onBack: () -> Void
    let onRetry: () -> Void

    var body: some View {
        switch problem {
        case .quotaReached(let resumeMonth):
            MessageScreen(heading: s("error_quota_heading"),
                          message: s("error_quota_message", s("month_\(resumeMonth)")),
                          systemName: "exclamationmark", tone: .warning, onBack: onBack)
        case .connectionCheck(let busy):
            MessageScreen(heading: s("error_connection_check_heading"), message: "",
                          systemName: "wifi.slash", tone: .warning,
                          actionLabel: s(busy ? "error_connection_retrying" : "common_try_again"),
                          onAction: onRetry, onBack: onBack)
        case .connectionRequired(let busy):
            MessageScreen(heading: s("error_connection_heading"), message: s("error_connection_message"),
                          systemName: "wifi.slash", tone: .warning,
                          actionLabel: s(busy ? "error_connection_retrying" : "common_try_again"),
                          onAction: onRetry, onBack: onBack)
        default:
            let (heading, message): (String, String) = {
                switch problem {
                case .notLicensed: return ("error_not_licensed_heading", "error_not_licensed_message")
                case .evaluationCap: return ("error_evaluation_cap_heading", "error_evaluation_cap_message")
                case .renewalRefused: return ("error_renewal_refused_heading", "error_renewal_refused_message")
                default: return ("error_session_heading", "error_camera_message")
                }
            }()
            MessageScreen(heading: s(heading), message: s(message), onBack: onBack)
        }
    }
}
