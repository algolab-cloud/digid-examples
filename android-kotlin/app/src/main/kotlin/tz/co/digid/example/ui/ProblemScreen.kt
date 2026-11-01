package tz.co.digid.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tz.co.digid.example.Problem
import tz.co.digid.example.R

/** A blocking problem before the camera opens, on the flow's message screen. */
@Composable
fun ProblemScreen(problem: Problem, onBack: () -> Unit) {
    val (heading, message) = when (problem) {
        Problem.NOT_LICENSED -> R.string.error_not_licensed_heading to R.string.error_not_licensed_message
        Problem.EVALUATION_CAP -> R.string.error_evaluation_cap_heading to R.string.error_evaluation_cap_message
        Problem.START_FAILED -> R.string.error_session_heading to R.string.error_camera_message
    }
    MessageScreen(heading = stringResource(heading), message = stringResource(message), onBack = onBack)
}
