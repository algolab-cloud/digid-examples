import SwiftUI

/// Before the camera, in the flow's minimal layout: a short title, one muted line, the pill, in
/// one centred column, with the step number ("1 / 2") in the top bar and "Powered by digid" at
/// the foot. (The app's start screen already chose the flow and the back of the card.)
struct IntroScreen: View {
    @Environment(\.strings) private var s
    let settings: Settings
    let onBack: () -> Void
    let onOpenCamera: () -> Void

    var body: some View {
        // The flow's steps are the card and the selfie; the back of the card is part of the card step.
        MinimalPage(onBack: onBack, step: s("common_step", 1, settings.withSelfie ? 2 : 1)) {
            MinimalTitle(text: s("card_intro_heading")).fadeSlideIn(delay: 60)
            Spacer().frame(height: FlowSpace.sm)
            MinimalLead(text: s(settings.withSelfie ? "card_intro_body_card_selfie" : "card_intro_body_card_only", s("document_either")))
                .fadeSlideIn(delay: 60)
            Spacer().frame(height: FlowSpace.xl)
            PrimaryButton(title: s("common_open_camera"), action: onOpenCamera)
        }
    }
}
