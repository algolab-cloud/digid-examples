import SwiftUI

// The look of the digid drop-in flow, with its default colours (the same as the Android
// example's ui/theme/Theme.kt):
// - A light grey page, flat white and lavender panels, no borders, no shadows; 1 pt hairlines
//   only divide rows of text.
// - Light and large for headlines; nothing heavier than medium.
// - One black pill per screen; secondary actions are "›" text links.
// - The accent appears as tints, small tags and toggles, never on a button.
// - The camera is full-bleed: corner brackets turn green when the card is framed, and the
//   instruction sits in a navy box.
// - Status colour stays small: green pass, amber review, red fail.
//
// The flow uses Inter; this example uses the system font, which is close enough. Bundle Inter
// (SIL OFL) and change `FlowType` if you want the exact look.

extension Color {
    /// A colour from 0xAARRGGBB.
    init(argb: UInt32) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255,
            opacity: Double((argb >> 24) & 0xFF) / 255
        )
    }
}

/// Colour roles. Screens say what a colour is for, never which colour it is.
enum FlowColors {
    /// The page behind everything.
    static let canvas = Color(argb: 0xFFF6F6F6)
    /// Cards on the page.
    static let surface = Color.white
    /// Segmented-control tracks and disabled fills.
    static let surfaceMuted = Color(argb: 0xFFECECEC)

    /// Headlines and strong labels; running copy; secondary copy; the quietest labels.
    static let text = Color.black
    static let textBody = Color(argb: 0xFF2B2B2B)
    static let textMuted = Color(argb: 0xFF5A5A5A)
    static let textSubtle = Color(argb: 0xFF787878)

    /// The one primary action, and its text.
    static let primaryFill = Color.black
    static let onPrimary = Color.white

    /// Hairlines between rows of text. Never around a card.
    static let line = Color(argb: 0xFFD4D4D4)

    /// Accent panel fills, lightest to strongest.
    static let tintSoft = Color(argb: 0xFFF4F5FF)
    static let tint = Color(argb: 0xFFE9EAFF)
    static let tintStrong = Color(argb: 0xFFD3D5FF)

    /// Tags, illustrations, toggles. Never a button fill.
    static let accent = Color(argb: 0xFF3F48FD)
    /// Tag text on `tint`.
    static let accentDeep = Color(argb: 0xFF020CD0)

    /// The instruction box over the camera (navy at 90 %), and its text.
    static let instruction = Color(argb: 0xE6010334)
    static let onInstruction = Color.white
    /// The round buttons over the camera (the same navy at 45 %).
    static let chromeButton = Color(argb: 0x73010334)

    /// The camera brackets / oval, and their ready colour.
    static let frame = Color.white
    static let frameReady = Color(argb: 0xFF22C55E)

    static let success = Color(argb: 0xFF15803D)
    static let warning = Color(argb: 0xFFB45309)
    static let danger = Color(argb: 0xFFB42318)
    static let successBg = Color(argb: 0xFFEAF7EE)
    static let warningBg = Color(argb: 0xFFFEF3C7)
    static let dangerBg = Color(argb: 0xFFFEECEC)
}

/// Status tones: pass, review, fail, plus neutral.
enum Tone {
    case neutral, success, warning, danger

    var color: Color {
        switch self {
        case .neutral: return FlowColors.textMuted
        case .success: return FlowColors.success
        case .warning: return FlowColors.warning
        case .danger: return FlowColors.danger
        }
    }

    var background: Color {
        switch self {
        case .neutral: return FlowColors.surfaceMuted
        case .success: return FlowColors.successBg
        case .warning: return FlowColors.warningBg
        case .danger: return FlowColors.dangerBg
        }
    }
}

/// Corner radii (the flow's default radius of 24).
enum FlowShape {
    /// Cards, panels and the instruction box.
    static let card: CGFloat = 24
    /// Small tags.
    static let tag: CGFloat = 8
}

/// 4-pt spacing grid.
enum FlowSpace {
    static let xxs: CGFloat = 2
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
    static let xxxl: CGFloat = 48
    static let page: CGFloat = xl
}

enum FlowSize {
    /// Primary buttons: 56 pt tall.
    static let buttonHeight: CGFloat = 56
    static let icon: CGFloat = 20
}

/// The type roles (sentence case everywhere; nothing heavier than medium):
/// - `displaySmall` the one big headline per screen (38, light)
/// - `titleLarge` step names (22, light)
/// - `titleMedium` buttons and links (16, medium); `titleSmall` top-bar titles (15, medium)
/// - `bodyLarge` the lead under a headline (18, light); `bodyMedium` running text (15);
///   `bodySmall` secondary text and detail labels (14)
/// - `labelLarge` group titles (15, medium); `labelMedium` tags and segmented controls (13,
///   medium); `labelSmall` step numbers (13)
/// - `minimalTitle` / `minimalLead`: the minimal layout's short title (20, medium) and its one
///   muted line.
enum FlowType {
    static let displaySmall = Font.system(size: 38, weight: .light)
    static let titleLarge = Font.system(size: 22, weight: .light)
    static let titleMedium = Font.system(size: 16, weight: .medium)
    static let titleSmall = Font.system(size: 15, weight: .medium)
    static let labelLarge = Font.system(size: 15, weight: .medium)
    static let labelMedium = Font.system(size: 13, weight: .medium)
    static let labelSmall = Font.system(size: 13)
    static let bodyLarge = Font.system(size: 18, weight: .light)
    static let bodyMedium = Font.system(size: 15)
    static let bodySmall = Font.system(size: 14)

    static func minimalTitle(_ size: CGFloat = 20) -> Font { .system(size: size, weight: .medium) }
    static let minimalLead = bodyMedium
}
