import Digid
import SwiftUI

// The app's own words for the SDK's codes, the same as the drop-in flow's (the keys are in
// en.lproj and sw.lproj/Localizable.strings). The SDK's enums can gain cases in a newer
// version, hence the `@unknown default` in each switch.

extension StepKind {
    /// The camera's title for a step.
    var labelKey: String {
        switch self {
        case .scanBack: return "step_card_back"
        case .selfie, .holdCard: return "step_selfie"
        default: return "step_card"
        }
    }
}

extension Guidance {
    /// The flow's words for every guidance code (neutral: any ID card, no product names). Nil
    /// for a code from a newer engine: `NidaKyc.text(for:lang:)` has the engine's own words for it.
    var labelKey: String? {
        switch self {
        case .noCard: return "guidance_no_card"
        case .moveCloser: return "guidance_move_closer"
        case .tooClose: return "guidance_too_close"
        case .blurry: return "guidance_blurry"
        case .glare: return "guidance_glare"
        case .tilted: return "guidance_tilted"
        case .holdStill: return "guidance_hold_still"
        case .flipToBack: return "guidance_flip_to_back"
        case .noFace: return "guidance_no_face"
        case .multipleFaces: return "guidance_multiple_faces"
        case .faceTooSmall: return "guidance_face_too_small"
        case .cardFaceTooSmall: return "guidance_card_face_too_small"
        case .turnLeft: return "guidance_turn_left"
        case .turnRight: return "guidance_turn_right"
        case .blink: return "guidance_blink"
        case .lookStraight: return "guidance_look_straight"
        case .licenceRequired: return "guidance_licence_required"
        case .done: return "guidance_done"
        case .unknown: return nil
        @unknown default: return nil
        }
    }
}

extension CheckId {
    /// Nil for a check this version of the app does not know: show it by its code.
    var labelKey: String? {
        switch self {
        case .requiredFields: return "check_required_fields"
        case .ninFormat: return "check_nin_format"
        case .ninDobMatch: return "check_nin_dob_match"
        case .dobPlausible: return "check_dob_plausible"
        case .expiryValid: return "check_expiry_valid"
        case .cardTypeKnown: return "check_card_type_known"
        case .ninChecksum: return "check_nin_checksum"
        case .licenceNumberFormat: return "check_licence_number_format"
        case .issueDateValid: return "check_issue_date_valid"
        case .sidesMatch: return "check_sides_match"
        case .backRead: return "check_back_read"
        case .unknown: return nil
        @unknown default: return nil
        }
    }
}

/// The three outcomes shown to people, with their colour and icon.
enum Outcome: CaseIterable {
    case passed, toReview, failed

    var labelKey: String {
        switch self {
        case .passed: return "details_pass"
        case .toReview: return "details_review"
        case .failed: return "details_fail"
        }
    }

    var countKey: String {
        switch self {
        case .passed: return "details_count_pass"
        case .toReview: return "details_count_review"
        case .failed: return "details_count_fail"
        }
    }

    var tone: Tone {
        switch self {
        case .passed: return .success
        case .toReview: return .warning
        case .failed: return .danger
        }
    }

    /// The filled dot of a line in "What we checked".
    var dot: String {
        switch self {
        case .passed: return "checkmark.circle.fill"
        case .toReview: return "exclamationmark.circle.fill"
        case .failed: return "xmark.circle.fill"
        }
    }

    /// The result's status shield.
    var shield: String {
        switch self {
        case .passed: return "checkmark.shield"
        case .toReview: return "exclamationmark.shield"
        case .failed: return "xmark.shield"
        }
    }
}

extension Overall {
    var outcome: Outcome {
        switch self {
        case .pass: return .passed
        case .fail: return .failed
        case .review, .unknown: return .toReview
        @unknown default: return .toReview
        }
    }
}

extension CheckStatus {
    /// Nil for checks with nothing to show (not applicable).
    var outcome: Outcome? {
        switch self {
        case .pass: return .passed
        case .fail: return .failed
        case .warn, .unverifiable, .unknown: return .toReview
        case .notApplicable: return nil
        @unknown default: return .toReview
        }
    }
}

extension Verdict {
    var outcome: Outcome {
        switch self {
        case .match: return .passed
        case .noMatch: return .failed
        case .uncertain, .unknown: return .toReview
        @unknown default: return .toReview
        }
    }

    var labelKey: String {
        switch self {
        case .match: return "verdict_match"
        case .noMatch: return "verdict_no_match"
        case .uncertain, .unknown: return "verdict_uncertain"
        @unknown default: return "verdict_uncertain"
        }
    }
}

extension Sex {
    var labelKey: String? {
        switch self {
        case .male: return "sex_male"
        case .female: return "sex_female"
        case .unknown: return nil
        @unknown default: return nil
        }
    }
}

extension CardType {
    var labelKey: String {
        switch self {
        case .citizen: return "card_type_citizen"
        case .resident: return "card_type_resident"
        case .refugee: return "card_type_refugee"
        case .unknown: return "card_type_unknown"
        @unknown default: return "card_type_unknown"
        }
    }
}

/// The NIN as printed on the card: 8-5-5-2 digits.
func formatNin(_ nin: String) -> String {
    guard nin.count == 20, nin.allSatisfy(\.isNumber) else { return nin }
    let d = Array(nin)
    return "\(String(d[0..<8]))-\(String(d[8..<13]))-\(String(d[13..<18]))-\(String(d[18...]))"
}

extension NidaDate {
    /// Day/month/year, the order used in Tanzania.
    var display: String { String(format: "%02d/%02d/%04d", day, month, year) }

    /// Whether the date is before today.
    var isPast: Bool {
        let now = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        let today = (now.year ?? 0) * 10_000 + (now.month ?? 0) * 100 + (now.day ?? 0)
        return year * 10_000 + month * 100 + day < today
    }
}
