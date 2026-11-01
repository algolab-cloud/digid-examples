import Digid
import SwiftUI
import UIKit

// The card and selfie camera, as in the drop-in flow: full-bleed preview, rounded corner
// brackets around an ID-1 frame (or an oval for the selfie) that turn green once the card sits
// inside, a soft shade under the top bar, and the instruction in a navy box at the foot with the
// step's progress. A check pops in when a side is accepted.

/// Guidance that means the card is not framed yet (the brackets stay white).
private let notFramed: Set<Guidance> = [.noCard, .moveCloser, .tooClose, .flipToBack]

struct CameraScreen: View {
    @Environment(\.strings) private var s
    @ObservedObject var model: KycModel

    var body: some View {
        Group {
            switch model.cameraState {
            case .denied:
                // iOS asks once; after a "Don't Allow", only the Settings app can allow the camera.
                MessageScreen(
                    heading: s("error_camera_permission_heading"),
                    message: s("error_camera_permission_message"),
                    systemName: "camera",
                    actionLabel: s("error_camera_permission_action"),
                    onAction: openSettings,
                    onBack: model.cancel
                )
            case .failed:
                MessageScreen(heading: s("error_camera_heading"), message: s("error_camera_message"), systemName: "camera", onBack: model.cancel)
            default:
                if model.capture.evaluationCapReached {
                    MessageScreen(heading: s("error_evaluation_cap_heading"), message: s("error_evaluation_cap_message"), onBack: model.cancel)
                } else {
                    viewfinder
                }
            }
        }
        // Back from the Settings app.
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            model.recheckCamera()
        }
    }

    private var viewfinder: some View {
        let capture = model.capture
        let accepted = capture.accepted
        let skipped = accepted == .scanBack && capture.backSkipped
        return ZStack {
            Color.black.ignoresSafeArea()
            CameraPreview(session: model.camera.session)
                .ignoresSafeArea()
                .opacity(model.cameraState == .running ? 1 : 0)
                .animation(.easeOut(duration: 0.3), value: model.cameraState)
            GuideFrame(capture: capture).ignoresSafeArea()
            LinearGradient(colors: [Color.black.opacity(0.42), .clear], startPoint: .top, endPoint: .bottom)
                .frame(height: 160)
                .frame(maxHeight: .infinity, alignment: .top)
                .ignoresSafeArea()
                .allowsHitTesting(false)

            VStack(spacing: 0) {
                CameraTopBar(
                    step: accepted ?? capture.step,
                    torchAvailable: model.torchAvailable && capture.step != .selfie,
                    torchOn: $model.torchOn,
                    onBack: model.cancel
                )
                Spacer()
                if let accepted, !skipped {
                    DoneCheck().id(accepted).padding(.bottom, FlowSpace.lg)
                } else if capture.shutter && !capture.photographing && capture.step != .selfie {
                    // The automatic photo has not happened: let the person take it.
                    ShutterButton(label: s("common_take_photo"), action: model.takePhoto).padding(.bottom, FlowSpace.lg)
                }
                InstructionBox(
                    message: message(capture),
                    progress: capture.running && accepted == nil ? capture.progress : nil,
                    onFinishNow: finishNow
                )
                .padding(.horizontal, FlowSpace.lg)
                .padding(.bottom, FlowSpace.xl)
            }
        }
        .preferredColorScheme(.dark) // light status bar over the camera
        .onChange(of: accepted) { step in
            if step != nil && !skipped { UINotificationFeedbackGenerator().notificationOccurred(.success) }
        }
    }

    /// What the instruction box says.
    private func message(_ capture: Capture) -> String {
        let g = capture.guidance
        switch capture.accepted {
        case .selfie?: return s("capture_selfie_taken")
        case .scanBack?: return s(capture.backSkipped ? "capture_back_skipped" : "capture_back_read")
        case .some: return s("capture_card_read")
        case nil: break
        }
        if !capture.running || model.cameraState != .running { return s("common_starting_camera") }
        if capture.photographing { return s(capture.step == .scanBack ? "capture_reading_back" : "capture_reading_card") }
        if capture.step == .scanFront && g == .noCard { return s("capture_place_card") }
        // The flow's own words for every code; the engine's for a code newer than this app.
        return g.labelKey.map { s($0) } ?? NidaKyc.text(for: g, lang: s.lang)
    }

    /// Debug builds only: end the attempt with the best result so far (`session.finishJSON()`),
    /// to see the result screen without a card.
    private var finishNow: (() -> Void)? {
        #if DEBUG
        return model.finishNow
        #else
        return nil
        #endif
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }
}

/// Over the camera: a white back arrow, the step's title in white, and the torch.
private struct CameraTopBar: View {
    @Environment(\.strings) private var s
    let step: StepKind
    let torchAvailable: Bool
    @Binding var torchOn: Bool
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            IconAction(systemName: "arrow.left", description: s("common_back"), tint: FlowColors.onInstruction, action: onBack)
            Text(s(step.labelKey))
                .font(FlowType.titleSmall)
                .foregroundColor(FlowColors.onInstruction)
                .shadow(color: .black.opacity(0.4), radius: 4)
                .lineLimit(1)
                .id(step.labelKey)
                .transition(.opacity)
            Spacer(minLength: 0)
            if torchAvailable {
                Button { torchOn.toggle() } label: {
                    Image(systemName: torchOn ? "bolt.fill" : "bolt")
                        .font(.system(size: 17))
                        .foregroundColor(torchOn ? Color(argb: 0xFF010334) : FlowColors.onInstruction)
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(torchOn ? FlowColors.onInstruction : FlowColors.chromeButton))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(s("torch"))
                .accessibilityValue(torchOn ? "on" : "off")
            }
        }
        .padding(.leading, FlowSpace.xs)
        .padding(.trailing, FlowSpace.lg)
        .padding(.top, FlowSpace.xs)
        .frame(height: 56)
        .animation(.easeInOut(duration: 0.2), value: step)
    }
}

/// The navy rounded instruction box at the foot of the camera, with a thin progress bar.
private struct InstructionBox: View {
    @Environment(\.strings) private var s
    let message: String
    let progress: Float?
    let onFinishNow: (() -> Void)?

    var body: some View {
        VStack(spacing: 0) {
            Text(message)
                .font(.system(size: 17, weight: .light))
                .foregroundColor(FlowColors.onInstruction)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .id(message)
                .transition(.opacity)
                .accessibilityAddTraits(.updatesFrequently)
            if let progress {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(FlowColors.onInstruction.opacity(0.2))
                        Capsule().fill(FlowColors.onInstruction)
                            .frame(width: geo.size.width * CGFloat(min(max(progress, 0), 1)))
                    }
                }
                .frame(height: 2)
                .padding(.top, FlowSpace.md)
                .animation(.easeOut(duration: 0.25), value: progress)
            }
            if let onFinishNow {
                ChevronLink(title: s("finish_now_debug"), color: FlowColors.onInstruction.opacity(0.7), action: onFinishNow)
                    .padding(.top, FlowSpace.xs)
            }
        }
        .padding(.horizontal, FlowSpace.xl)
        .padding(.vertical, FlowSpace.lg + 2)
        .background(RoundedRectangle(cornerRadius: FlowShape.card, style: .continuous).fill(FlowColors.instruction))
        .animation(.easeInOut(duration: 0.2), value: message)
    }
}

/// The manual shutter: a white ring around a white disc.
private struct ShutterButton: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Circle()
                .strokeBorder(FlowColors.onInstruction, lineWidth: 3)
                .frame(width: 64, height: 64)
                .overlay(Circle().fill(FlowColors.onInstruction).padding(7))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .popIn()
    }
}

/// The check over the camera when a capture is accepted, in the frame's ready colour.
private struct DoneCheck: View {
    var body: some View {
        Circle()
            .fill(FlowColors.frameReady)
            .frame(width: 48, height: 48)
            .overlay(Image(systemName: "checkmark").font(.system(size: 22, weight: .semibold)).foregroundColor(FlowColors.onInstruction))
            .popIn()
            .accessibilityHidden(true)
    }
}

/// The capture frame: rounded corner brackets around an ID-1 area, or an oval for the selfie,
/// with no shade so the whole scene stays visible. White while searching, green once the card
/// sits inside the frame; a thin full outline joins the brackets when a side is accepted.
private struct GuideFrame: View {
    let capture: Capture

    var body: some View {
        let step = capture.accepted ?? capture.step
        let cardStep = step == .scanFront || step == .scanBack || step == .holdCard
        let framed = cardStep && (capture.ready || capture.photographing || (capture.overlay.cardQuad != nil && !notFramed.contains(capture.guidance)))
        let accepted = capture.accepted != nil
        let green = accepted || framed
        let color = green ? FlowColors.frameReady : FlowColors.frame
        return GeometryReader { geo in
            let size = geo.size
            ZStack {
                if cardStep {
                    let r = cardRect(size)
                    if accepted {
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .stroke(FlowColors.frameReady, lineWidth: 2)
                            .frame(width: r.width, height: r.height)
                            .position(x: r.midX, y: r.midY)
                    }
                    Brackets(rect: r)
                        .stroke(color, style: StrokeStyle(lineWidth: green ? 5 : 4, lineCap: .round, lineJoin: .round))
                } else if step == .selfie {
                    let o = ovalRect(size)
                    Ellipse()
                        .stroke(color, lineWidth: 3)
                        .frame(width: o.width, height: o.height)
                        .position(x: o.midX, y: o.midY)
                }
            }
            .animation(.easeOut(duration: 0.25), value: green)
        }
        .allowsHitTesting(false)
    }

    /// An ID-1 card: 85.60 x 53.98 mm.
    private static let id1: CGFloat = 85.60 / 53.98

    /// The centred ID-1 frame.
    private func cardRect(_ size: CGSize) -> CGRect {
        let c = CGPoint(x: size.width / 2, y: size.height / 2 - size.height * 0.04)
        let w = min(size.width * 0.84, size.height * 0.6 * Self.id1)
        let h = w / Self.id1
        return CGRect(x: c.x - w / 2, y: c.y - h / 2, width: w, height: h)
    }

    /// The selfie oval.
    private func ovalRect(_ size: CGSize) -> CGRect {
        let c = CGPoint(x: size.width / 2, y: size.height / 2 - size.height * 0.08)
        let w = min(size.width * 0.66, size.height * 0.5)
        let h = w * 0.86 / 0.66
        return CGRect(x: c.x - w / 2, y: c.y - h / 2, width: w, height: h)
    }
}

/// Four rounded corner brackets on `rect`, 34 pt long with a 10 pt radius.
private struct Brackets: Shape {
    let rect: CGRect

    func path(in _: CGRect) -> Path {
        let l: CGFloat = 34, rad: CGFloat = 10
        var path = Path()
        let corners: [(CGPoint, CGFloat, CGFloat)] = [
            (CGPoint(x: rect.minX, y: rect.minY), 1, 1),
            (CGPoint(x: rect.maxX, y: rect.minY), -1, 1),
            (CGPoint(x: rect.minX, y: rect.maxY), 1, -1),
            (CGPoint(x: rect.maxX, y: rect.maxY), -1, -1),
        ]
        for (p, sx, sy) in corners {
            path.move(to: CGPoint(x: p.x, y: p.y + l * sy))
            path.addLine(to: CGPoint(x: p.x, y: p.y + rad * sy))
            path.addQuadCurve(to: CGPoint(x: p.x + rad * sx, y: p.y), control: p)
            path.addLine(to: CGPoint(x: p.x + l * sx, y: p.y))
        }
        return path
    }
}
