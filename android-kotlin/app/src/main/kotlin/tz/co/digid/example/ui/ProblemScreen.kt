package tz.co.digid.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import tz.co.digid.example.KycViewModel
import tz.co.digid.example.Problem
import tz.co.digid.example.R
import tz.co.digid.example.Screen
import tz.co.digid.example.ui.theme.Tone

/**
 * A blocking problem before the camera opens, on the flow's message screen. The connection
 * screen's Try again ([onRetry]) sends the usage and renews the licence, then starts again.
 */
@Composable
fun ProblemScreen(message: Screen.Message, onBack: () -> Unit, onRetry: () -> Unit) {
    when (message.problem) {
        Problem.QUOTA_REACHED -> {
            val month = stringArrayResource(R.array.months)[(message.resumeMonth ?: KycViewModel.monthAfter(null)) - 1]
            MessageScreen(
                heading = stringResource(R.string.error_quota_heading),
                message = stringResource(R.string.error_quota_message, month),
                icon = Tabler.shieldExclamation,
                tone = Tone.WARNING,
                onBack = onBack,
            )
        }
        Problem.CONNECTION_CHECK -> MessageScreen(
            heading = stringResource(R.string.error_connection_check_heading),
            message = "",
            icon = Tabler.shieldExclamation,
            tone = Tone.WARNING,
            onBack = onBack,
            actionLabel = stringResource(if (message.busy) R.string.error_connection_retrying else R.string.common_try_again),
            onAction = onRetry,
        )
        Problem.CONNECTION_REQUIRED -> MessageScreen(
            heading = stringResource(R.string.error_connection_heading),
            message = stringResource(R.string.error_connection_message),
            icon = Tabler.shieldExclamation,
            tone = Tone.WARNING,
            onBack = onBack,
            actionLabel = stringResource(if (message.busy) R.string.error_connection_retrying else R.string.common_try_again),
            onAction = onRetry,
        )
        else -> {
            val (heading, body) = when (message.problem) {
                Problem.NOT_LICENSED -> R.string.error_not_licensed_heading to R.string.error_not_licensed_message
                Problem.EVALUATION_CAP -> R.string.error_evaluation_cap_heading to R.string.error_evaluation_cap_message
                Problem.RENEWAL_REFUSED -> R.string.error_renewal_refused_heading to R.string.error_renewal_refused_message
                else -> R.string.error_session_heading to R.string.error_camera_message
            }
            MessageScreen(heading = stringResource(heading), message = stringResource(body), onBack = onBack)
        }
    }
}
