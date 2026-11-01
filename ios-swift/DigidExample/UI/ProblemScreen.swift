import SwiftUI

/// A blocking problem before the camera opens, on the flow's message screen.
struct ProblemScreen: View {
    @Environment(\.strings) private var s
    let problem: Problem
    let onBack: () -> Void

    var body: some View {
        let (heading, message): (String, String) = {
            switch problem {
            case .notLicensed: return ("error_not_licensed_heading", "error_not_licensed_message")
            case .evaluationCap: return ("error_evaluation_cap_heading", "error_evaluation_cap_message")
            case .startFailed: return ("error_session_heading", "error_camera_message")
            }
        }()
        MessageScreen(heading: s(heading), message: s(message), onBack: onBack)
    }
}
