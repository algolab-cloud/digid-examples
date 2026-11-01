package tz.co.digid.example.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tz.co.digid.example.R
import tz.co.digid.example.Settings
import tz.co.digid.example.ui.theme.FlowSpace

/**
 * Before the camera, in the flow's minimal layout: a short title, one muted line, the pill, in
 * one centred column, with the step number ("1 / 2") in the top bar and "Powered by digid" at
 * the foot. (The app's start screen already chose the flow and the back of the card.)
 */
@Composable
fun IntroScreen(settings: Settings, onBack: () -> Unit, onOpenCamera: () -> Unit) {
    // The flow's steps are the card and the selfie; the back of the card is part of the card step.
    val step = stringResource(R.string.common_step, 1, if (settings.withSelfie) 2 else 1)
    val document = stringResource(R.string.document_either)
    MinimalPage(onBack = onBack, step = step) {
        FadeSlideIn(60) { MinimalTitle(stringResource(R.string.card_intro_heading)) }
        Spacer(Modifier.height(FlowSpace.sm))
        FadeSlideIn(60) {
            MinimalLead(
                stringResource(if (settings.withSelfie) R.string.card_intro_body_card_selfie else R.string.card_intro_body_card_only, document),
            )
        }
        Spacer(Modifier.height(FlowSpace.xl))
        PrimaryButton(stringResource(R.string.common_open_camera), onOpenCamera)
    }
}
