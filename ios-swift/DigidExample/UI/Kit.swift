import SwiftUI

// The drop-in flow's building blocks in SwiftUI (the same as the Android example's ui/Kit.kt):
// a page, flat panels with no borders or shadows, one black pill per screen and "›" text links
// for everything secondary. Copy these into your app and restyle them as you like. Icons are SF
// Symbols.

/// The one primary action: a solid black pill, 56 pt tall.
struct PrimaryButton: View {
    let title: String
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(FlowType.titleMedium)
                .foregroundColor(enabled ? FlowColors.onPrimary : FlowColors.textMuted)
                .frame(maxWidth: .infinity)
                .frame(height: FlowSize.buttonHeight)
                .contentShape(Capsule())
        }
        .buttonStyle(PillStyle(enabled: enabled))
        .disabled(!enabled)
    }

    private struct PillStyle: ButtonStyle {
        let enabled: Bool
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.background(
                Capsule().fill(!enabled ? FlowColors.surfaceMuted : FlowColors.primaryFill.opacity(configuration.isPressed ? 0.85 : 1))
            )
        }
    }
}

/// A secondary action: plain text and a "›".
struct ChevronLink: View {
    let title: String
    var color: Color = FlowColors.text
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(title).font(.system(size: 16))
                Text("›").font(.system(size: 19))
            }
            .foregroundColor(color)
            .padding(FlowSpace.md)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// A flat rounded panel: a white card or a lavender tint. No border, no shadow.
struct Panel<Content: View>: View {
    var fill: Color = FlowColors.surface
    var padding = EdgeInsets(top: FlowSpace.xl, leading: FlowSpace.xl, bottom: FlowSpace.xl, trailing: FlowSpace.xl)
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: FlowShape.card, style: .continuous).fill(fill))
    }
}

/// A small tag: ube text on lavender, or a status tone ("Evaluation").
struct Tag: View {
    let label: String
    var tone: Tone?

    var body: some View {
        Text(label)
            .font(.system(size: 13))
            .foregroundColor(tone?.color ?? FlowColors.accentDeep)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(RoundedRectangle(cornerRadius: FlowShape.tag, style: .continuous).fill(tone?.background ?? FlowColors.tint))
    }
}

/// A pill track with one white chip for the picked option (the EN / SW switch).
struct SegmentedChoice<T: Hashable>: View {
    let options: [(T, String)]
    @Binding var value: T
    var label: String?

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options, id: \.0) { option, text in
                let on = option == value
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { value = option }
                } label: {
                    Text(text)
                        .font(FlowType.labelMedium)
                        .foregroundColor(on ? FlowColors.text : FlowColors.textMuted)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(on ? FlowColors.surface : Color.clear))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(3)
        .background(Capsule().fill(FlowColors.surfaceMuted))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(label ?? "")
    }
}

/// A 48 pt touch target with a 22 pt line icon.
struct IconAction: View {
    let systemName: String
    let description: String
    var tint: Color = FlowColors.text
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 20, weight: .regular))
                .foregroundColor(tint)
                .frame(width: 48, height: 48)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(description)
    }
}

/// Back arrow and title on the left; a quiet step number ("1 / 3") on the right.
struct AppTopBar: View {
    @Environment(\.strings) private var s
    var title = ""
    let onBack: (() -> Void)?
    var step: String?

    var body: some View {
        HStack(spacing: 0) {
            if let onBack {
                IconAction(systemName: "arrow.left", description: s("common_back"), action: onBack)
            } else {
                Spacer().frame(width: FlowSpace.lg)
            }
            Text(title)
                .font(FlowType.titleSmall)
                .foregroundColor(FlowColors.text)
                .lineLimit(1)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            if let step {
                Text(step)
                    .font(FlowType.labelSmall)
                    .foregroundColor(FlowColors.textSubtle)
                    .padding(.horizontal, FlowSpace.md)
            }
        }
        .padding(.leading, FlowSpace.xs)
        .padding(.trailing, FlowSpace.sm)
        .padding(.top, FlowSpace.xs)
        .frame(height: 56)
    }
}

/// "Powered by" and the digid wordmark.
struct PoweredBy: View {
    @Environment(\.strings) private var s

    var body: some View {
        HStack(spacing: 6) {
            Text(s("common_powered_by"))
                .font(.system(size: 12))
                .foregroundColor(FlowColors.textMuted)
            DigidWordmark().frame(height: 14).padding(.top, 2)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(s("common_powered_by")) digid")
    }
}

/// The minimal layout's page: a slim top bar (the back arrow and the step number), one centred
/// column at most 420 pt wide (vertically centred when it fits), and `PoweredBy` at the foot.
/// Pages put their pill and links in `content`.
struct MinimalPage<Content: View>: View {
    let onBack: (() -> Void)?
    var step: String?
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            AppTopBar(onBack: onBack, step: step)
            GeometryReader { geo in
                ScrollView {
                    VStack(spacing: 0, content: content)
                        .frame(maxWidth: 420)
                        .padding(.horizontal, FlowSpace.page)
                        .padding(.top, FlowSpace.sm)
                        .padding(.bottom, FlowSpace.xl)
                        .frame(maxWidth: .infinity, minHeight: geo.size.height)
                }
            }
            PoweredBy().padding(.bottom, FlowSpace.lg)
        }
        .background(FlowColors.canvas.ignoresSafeArea())
    }
}

/// The minimal layout's short title, centred.
struct MinimalTitle: View {
    let text: String
    var size: CGFloat = 20

    var body: some View {
        Text(text)
            .font(FlowType.minimalTitle(size))
            .foregroundColor(FlowColors.text)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .accessibilityAddTraits(.isHeader)
    }
}

/// The one muted line under a title, centred.
struct MinimalLead: View {
    let text: String

    var body: some View {
        Text(text)
            .font(FlowType.minimalLead)
            .foregroundColor(FlowColors.textMuted)
            .lineSpacing(4)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }
}

/// A small status mark: a tone-coloured disc with a white icon.
struct StatusMark: View {
    let tone: Tone
    let systemName: String
    var size: CGFloat = 32

    var body: some View {
        Circle()
            .fill(tone.color)
            .frame(width: size, height: size)
            .overlay(Image(systemName: systemName).font(.system(size: size * 0.45, weight: .semibold)).foregroundColor(FlowColors.onPrimary))
            .accessibilityHidden(true)
    }
}

/// A calm full-screen message for errors and blocked states: a status mark, the headline, one
/// plain sentence (left out when empty), one pill (default "Go back").
struct MessageScreen: View {
    @Environment(\.strings) private var s
    let heading: String
    let message: String
    var systemName = "xmark"
    var tone: Tone = .danger
    var actionLabel: String?
    var onAction: (() -> Void)?
    let onBack: () -> Void

    var body: some View {
        MinimalPage(onBack: onBack) {
            StatusMark(tone: tone, systemName: systemName, size: 44).popIn()
            Spacer().frame(height: FlowSpace.lg)
            MinimalTitle(text: heading)
            if !message.isEmpty {
                Spacer().frame(height: FlowSpace.sm)
                MinimalLead(text: message)
            }
            Spacer().frame(height: FlowSpace.xl)
            PrimaryButton(title: actionLabel ?? s("common_go_back"), action: onAction ?? onBack)
        }
    }
}

// MARK: - Result pieces

/// The result's status: a shield in the outcome's colour on a soft tinted circle with a fainter
/// halo, scaling in.
struct StatusShield: View {
    let outcome: Outcome

    var body: some View {
        ZStack {
            Circle().fill(outcome.tone.color.opacity(0.06)).frame(width: 100, height: 100)
            Circle().fill(outcome.tone.color.opacity(0.14)).frame(width: 76, height: 76)
            Image(systemName: outcome.shield)
                .font(.system(size: 34, weight: .regular))
                .foregroundColor(outcome.tone.color)
        }
        .popIn()
        .accessibilityHidden(true)
    }
}

/// One label / value line of a `DetailCard`; a nil value shows as a dash.
struct DetailRow: Identifiable {
    let label: String
    let value: String?
    var id: String { label }
}

/// Label / value rows in one white card, with hairlines between them.
struct DetailCard: View {
    let rows: [DetailRow]

    var body: some View {
        Panel(padding: EdgeInsets(top: FlowSpace.xs, leading: FlowSpace.xl, bottom: FlowSpace.xs, trailing: FlowSpace.xl)) {
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.offset) { i, row in
                    if i > 0 { Rectangle().fill(FlowColors.line).frame(height: 1) }
                    HStack(alignment: .firstTextBaseline, spacing: FlowSpace.lg) {
                        Text(row.label)
                            .font(FlowType.bodySmall)
                            .foregroundColor(FlowColors.textMuted)
                        Spacer(minLength: 0)
                        Text(row.value ?? "—")
                            .font(FlowType.bodyMedium.monospacedDigit())
                            .foregroundColor(FlowColors.text)
                            .multilineTextAlignment(.trailing)
                    }
                    .padding(.vertical, FlowSpace.lg)
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }
}

/// One line of a `ChecksCard`: what was checked and how it went.
struct CheckItem {
    let label: String
    let outcome: Outcome
}

/// What passed and what did not: a white card with a header (title and counts, "4 passed · 1 to
/// review") and one line per check with a filled status icon. `collapsed`: behind a "See
/// details" toggle. Outcomes only.
struct ChecksCard: View {
    @Environment(\.strings) private var s
    let items: [CheckItem]
    var collapsed = true
    @State private var open = false

    var body: some View {
        let count = Outcome.allCases.compactMap { o -> String? in
            let n = items.filter { $0.outcome == o }.count
            return n == 0 ? nil : s(o.countKey, n)
        }.joined(separator: " · ")
        Panel(padding: EdgeInsets(top: 0, leading: FlowSpace.lg + 4, bottom: 0, trailing: FlowSpace.lg + 4)) {
            VStack(spacing: 0) {
                Button {
                    if collapsed { withAnimation(.easeInOut(duration: 0.2)) { open.toggle() } }
                } label: {
                    HStack(spacing: FlowSpace.md) {
                        Text(s(collapsed ? "details_toggle" : "details_title"))
                            .font(FlowType.titleMedium)
                            .foregroundColor(FlowColors.text)
                        Spacer(minLength: 0)
                        Text(count)
                            .font(FlowType.bodySmall)
                            .foregroundColor(FlowColors.textMuted)
                            .multilineTextAlignment(.trailing)
                        if collapsed {
                            Text("›")
                                .font(.system(size: 19))
                                .foregroundColor(FlowColors.textMuted)
                                .rotationEffect(.degrees(open ? 90 : 0))
                        }
                    }
                    .frame(minHeight: 52)
                    .padding(.vertical, FlowSpace.md)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if !collapsed || open {
                    VStack(spacing: 0) {
                        ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                            HStack(spacing: FlowSpace.md) {
                                Image(systemName: item.outcome.dot)
                                    .font(.system(size: 20))
                                    .foregroundColor(item.outcome.tone.color)
                                Text(item.label)
                                    .font(FlowType.bodyMedium)
                                    .foregroundColor(FlowColors.text)
                                Spacer(minLength: 0)
                                Text(s(item.outcome.labelKey))
                                    .font(FlowType.bodySmall)
                                    .foregroundColor(item.outcome == .passed ? FlowColors.textMuted : item.outcome.tone.color)
                            }
                            .padding(.vertical, 9)
                            .accessibilityElement(children: .combine)
                        }
                    }
                    .padding(.bottom, FlowSpace.sm)
                }
            }
        }
    }
}

// MARK: - Motion

private struct FadeSlideIn: ViewModifier {
    let delay: Double
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 16)
            .onAppear { withAnimation(.easeOut(duration: 0.45).delay(delay)) { shown = true } }
    }
}

private struct PopIn: ViewModifier {
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .scaleEffect(shown ? 1 : 0.8)
            .onAppear { withAnimation(.spring(response: 0.42, dampingFraction: 0.62)) { shown = true } }
    }
}

extension View {
    /// Fade and a 16 pt rise when first shown, once.
    func fadeSlideIn(delay ms: Int = 0) -> some View { modifier(FadeSlideIn(delay: Double(ms) / 1000)) }

    /// Scale up from 80 % with a soft overshoot, once (the status marks, the camera check).
    func popIn() -> some View { modifier(PopIn()) }
}
