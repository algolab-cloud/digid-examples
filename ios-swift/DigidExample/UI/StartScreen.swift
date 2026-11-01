import Digid
import SwiftUI

/// The app's own start screen, in the flow's look: the logo and the app's EN / SW switch, a big
/// light headline, the flow choice as tinted panels, the back-of-card switch, then one black
/// pill that opens the flow.
struct StartScreen: View {
    @Environment(\.strings) private var s
    @ObservedObject var model: KycModel
    @ObservedObject var engine: DigidEngine

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                DigidWordmark().frame(height: 27).accessibilityLabel("digid")
                Spacer()
                SegmentedChoice(options: [(Lang.en, "EN"), (Lang.sw, "SW")], value: $model.settings.lang, label: s("language"))
            }
            .padding(.leading, FlowSpace.page)
            .padding(.trailing, FlowSpace.lg)
            .padding(.top, FlowSpace.md)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(s("start_title"))
                        .font(FlowType.displaySmall)
                        .foregroundColor(FlowColors.text)
                        .fadeSlideIn()
                    Spacer().frame(height: FlowSpace.md)
                    Text(s("start_subtitle"))
                        .font(FlowType.bodyLarge)
                        .foregroundColor(FlowColors.textBody)
                        .lineSpacing(4)
                        .fadeSlideIn(delay: 60)

                    Spacer().frame(height: FlowSpace.xl)
                    EngineStatus(state: engine.state, hasKey: !engine.licenceKey.isEmpty).fadeSlideIn(delay: 120)

                    Spacer().frame(height: FlowSpace.xl)
                    Text(s("flow_heading"))
                        .font(FlowType.bodySmall)
                        .foregroundColor(FlowColors.textMuted)
                        .padding(.leading, FlowSpace.xs)
                        .padding(.bottom, FlowSpace.sm)
                    VStack(spacing: FlowSpace.sm) {
                        FlowOption(title: s("flow_card_only"), hint: s("flow_card_only_hint"), selected: !model.settings.withSelfie) {
                            model.settings.withSelfie = false
                        }
                        .fadeSlideIn(delay: 180)
                        FlowOption(title: s("flow_card_selfie"), hint: s("flow_card_selfie_hint"), selected: model.settings.withSelfie,
                                   enabled: hasFaceModels) {
                            model.settings.withSelfie = true
                        }
                        .fadeSlideIn(delay: 240)
                    }

                    Spacer().frame(height: FlowSpace.md)
                    ScanBackSwitch(on: $model.settings.scanBack).fadeSlideIn(delay: 300)
                }
                .padding(.horizontal, FlowSpace.page)
                .padding(.top, FlowSpace.xxxl)
                .padding(.bottom, FlowSpace.lg)
            }

            // The foot: the one black pill.
            PrimaryButton(title: s("start_verification"), enabled: isReady, action: model.openIntro)
                .padding(.horizontal, FlowSpace.page)
                .padding(.top, FlowSpace.sm)
                .padding(.bottom, FlowSpace.lg)
        }
        .background(FlowColors.canvas.ignoresSafeArea())
    }

    private var isReady: Bool {
        if case .ready = engine.state { return true }
        return false
    }

    private var hasFaceModels: Bool {
        if case .ready(_, let face, _) = engine.state { return face }
        return true
    }
}

/// The engine's state: loading, failed, or the licence (with the evaluation banner).
private struct EngineStatus: View {
    @Environment(\.strings) private var s
    let state: DigidEngine.State
    let hasKey: Bool

    var body: some View {
        switch state {
        case .loading:
            HStack(spacing: FlowSpace.md) {
                ProgressView().controlSize(.small)
                Text(s("engine_loading")).font(FlowType.bodySmall).foregroundColor(FlowColors.textMuted)
            }
        case .failed(let message):
            Notice(tone: .danger, title: nil, message: s("engine_failed", message))
        case .ready(let licence, _, _):
            switch licence.state {
            case .licensed:
                Text(s("licensed_to", licence.orgName ?? licence.licenceId ?? ""))
                    .font(FlowType.bodySmall)
                    .foregroundColor(FlowColors.textMuted)
            case .grace:
                Notice(tone: .warning, title: s("licence_grace_title"), message: s("licence_grace_body"))
            default:
                // Evaluation mode: results are watermarked and never better than "to review".
                Notice(tone: .warning, title: s("evaluation_title"),
                       message: hasKey ? s("evaluation_key_rejected", licence.reason ?? "") : s("evaluation_no_key"))
            }
        }
    }
}

/// The evaluation banner, and other notices.
private struct Notice: View {
    let tone: Tone
    let title: String?
    let message: String

    var body: some View {
        Panel(fill: tone.background, padding: EdgeInsets(top: FlowSpace.lg, leading: FlowSpace.xl, bottom: FlowSpace.lg, trailing: FlowSpace.xl)) {
            VStack(alignment: .leading, spacing: FlowSpace.xs) {
                if let title { Text(title).font(FlowType.labelLarge).foregroundColor(tone.color) }
                Text(message).font(FlowType.bodySmall).foregroundColor(FlowColors.textBody).lineSpacing(3)
            }
        }
    }
}

/// One flow: a flat panel, lavender when picked, with a small accent radio mark.
private struct FlowOption: View {
    let title: String
    let hint: String
    let selected: Bool
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Panel(fill: selected ? FlowColors.tint : FlowColors.surface,
                  padding: EdgeInsets(top: FlowSpace.lg + 2, leading: FlowSpace.xl, bottom: FlowSpace.lg + 2, trailing: FlowSpace.lg + 4)) {
                HStack(spacing: FlowSpace.md) {
                    VStack(alignment: .leading, spacing: FlowSpace.xs) {
                        Text(title).font(FlowType.titleLarge).foregroundColor(FlowColors.text)
                        Text(hint).font(FlowType.bodySmall).foregroundColor(FlowColors.textMuted)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .multilineTextAlignment(.leading)
                    RadioMark(on: selected)
                }
            }
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct RadioMark: View {
    let on: Bool

    var body: some View {
        Circle()
            .strokeBorder(on ? FlowColors.accent : FlowColors.textMuted, lineWidth: 1.5)
            .frame(width: 22, height: 22)
            .overlay(Circle().fill(FlowColors.accent).frame(width: 12, height: 12).opacity(on ? 1 : 0))
    }
}

/// "Scan the back too": a soft-tinted panel with the accent switch.
private struct ScanBackSwitch: View {
    @Environment(\.strings) private var s
    @Binding var on: Bool

    var body: some View {
        Panel(fill: FlowColors.tintSoft, padding: EdgeInsets(top: FlowSpace.md, leading: FlowSpace.xl, bottom: FlowSpace.md, trailing: FlowSpace.lg)) {
            Toggle(isOn: $on) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(s("scan_back")).font(.system(size: 16)).foregroundColor(FlowColors.text)
                    Text(s("scan_back_hint")).font(FlowType.bodySmall).foregroundColor(FlowColors.textMuted)
                }
            }
            .tint(FlowColors.accent)
        }
    }
}
