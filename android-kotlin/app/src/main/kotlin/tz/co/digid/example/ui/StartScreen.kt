package tz.co.digid.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tz.co.digid.example.BuildConfig
import tz.co.digid.example.DigidEngine
import tz.co.digid.example.R
import tz.co.digid.example.Settings
import tz.co.digid.example.ui.theme.FlowColors
import tz.co.digid.example.ui.theme.FlowSpace
import tz.co.digid.example.ui.theme.FlowType
import tz.co.digid.example.ui.theme.Tone
import tz.co.digid.sdk.Lang
import tz.co.digid.sdk.LicenceState

/**
 * The app's own start screen, in the flow's look (like the Flutter example's home): the logo and
 * the app's EN / SW switch, a big light headline, the flow choice as tinted panels, the
 * back-of-card switch, then one black pill that opens the flow.
 */
@Composable
fun StartScreen(
    settings: Settings,
    engine: DigidEngine.State,
    onWithSelfie: (Boolean) -> Unit,
    onScanBack: (Boolean) -> Unit,
    onLang: (Lang) -> Unit,
    onStart: () -> Unit,
) {
    StatusBarIcons(dark = true)
    Column(Modifier.fillMaxSize().background(FlowColors.canvas).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = FlowSpace.page, top = FlowSpace.md, end = FlowSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val logo = remember { digidWordmark(FlowColors.text) }
            Image(logo, contentDescription = "digid", modifier = Modifier.height(27.dp).width(27.dp * 78 / 31))
            Spacer(Modifier.weight(1f))
            SegmentedChoice(listOf(Lang.EN to "EN", Lang.SW to "SW"), settings.lang, onLang, label = stringResource(R.string.language))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = FlowSpace.page, top = FlowSpace.xxxl, end = FlowSpace.page, bottom = FlowSpace.lg),
        ) {
            FadeSlideIn { Text(stringResource(R.string.start_title), style = FlowType.displaySmall) }
            Spacer(Modifier.height(FlowSpace.md))
            FadeSlideIn(60) { Text(stringResource(R.string.start_subtitle), style = FlowType.bodyLarge) }

            Spacer(Modifier.height(FlowSpace.xl))
            FadeSlideIn(120) { EngineStatus(engine) }

            Spacer(Modifier.height(FlowSpace.xl))
            Text(stringResource(R.string.flow_heading), style = FlowType.bodySmall, modifier = Modifier.padding(start = FlowSpace.xs, bottom = FlowSpace.sm))
            val faceModels = (engine as? DigidEngine.State.Ready)?.hasFaceModels ?: true
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(FlowSpace.sm)) {
                FadeSlideIn(180) {
                    FlowOption(R.string.flow_card_only, R.string.flow_card_only_hint, selected = !settings.withSelfie) { onWithSelfie(false) }
                }
                FadeSlideIn(240) {
                    FlowOption(R.string.flow_card_selfie, R.string.flow_card_selfie_hint, selected = settings.withSelfie, enabled = faceModels) { onWithSelfie(true) }
                }
            }

            Spacer(Modifier.height(FlowSpace.md))
            FadeSlideIn(300) { ScanBackSwitch(settings.scanBack, onScanBack) }
        }

        // The foot: the one black pill.
        Column(Modifier.padding(start = FlowSpace.page, top = FlowSpace.sm, end = FlowSpace.page, bottom = FlowSpace.lg)) {
            PrimaryButton(stringResource(R.string.start_verification), onStart, enabled = engine is DigidEngine.State.Ready)
        }
    }
}

@Composable
private fun EngineStatus(engine: DigidEngine.State) {
    when (engine) {
        DigidEngine.State.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = FlowColors.text, trackColor = FlowColors.line, strokeWidth = 1.5.dp)
            Spacer(Modifier.width(FlowSpace.md))
            Text(stringResource(R.string.engine_loading), style = FlowType.bodySmall)
        }

        is DigidEngine.State.Failed -> Notice(Tone.DANGER, null, stringResource(R.string.engine_failed, engine.message))

        is DigidEngine.State.Ready -> when (engine.licence.state) {
            LicenceState.LICENSED -> Text(
                stringResource(R.string.licensed_to, engine.licence.orgName ?: engine.licence.licenceId.orEmpty()),
                style = FlowType.bodySmall,
            )
            LicenceState.GRACE -> Notice(Tone.WARNING, stringResource(R.string.licence_grace_title), stringResource(R.string.licence_grace_body))
            else -> Notice(
                Tone.WARNING,
                stringResource(R.string.evaluation_title),
                if (BuildConfig.DIGID_LICENCE.isBlank()) {
                    stringResource(R.string.evaluation_no_key)
                } else {
                    stringResource(R.string.evaluation_key_rejected, engine.licence.reason.orEmpty())
                },
            )
        }
    }
}

/** The evaluation banner (results are watermarked and never better than "to review"), and other notices. */
@Composable
private fun Notice(tone: Tone, title: String?, body: String) {
    Panel(fill = tone.background, padding = PaddingValues(horizontal = FlowSpace.xl, vertical = FlowSpace.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(FlowSpace.xs)) {
            title?.let { Text(it, style = FlowType.labelLarge, color = tone.color) }
            Text(body, style = FlowType.bodySmall, color = FlowColors.textBody)
        }
    }
}

/** One flow: a flat panel, lavender when picked, with a small accent radio mark. */
@Composable
private fun FlowOption(title: Int, hint: Int, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Panel(
        fill = if (selected) FlowColors.tint else FlowColors.surface,
        padding = PaddingValues(start = FlowSpace.xl, top = FlowSpace.lg + 2.dp, end = FlowSpace.lg + 4.dp, bottom = FlowSpace.lg + 2.dp),
        onClick = if (enabled) onClick else null,
        role = Role.RadioButton,
        modifier = Modifier.alpha(if (enabled) 1f else .45f).semantics { this.selected = selected },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FlowSpace.xs)) {
                Text(stringResource(title), style = FlowType.titleLarge)
                Text(stringResource(hint), style = FlowType.bodySmall)
            }
            Spacer(Modifier.width(FlowSpace.md))
            RadioMark(selected)
        }
    }
}

@Composable
private fun RadioMark(on: Boolean) {
    Box(
        Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, if (on) FlowColors.accent else FlowColors.textMuted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Box(Modifier.size(12.dp).clip(CircleShape).background(FlowColors.accent))
    }
}

/** "Scan the back too": a soft-tinted panel with the accent switch, as on the flow's card intro. */
@Composable
private fun ScanBackSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val label = stringResource(R.string.scan_back)
    Panel(
        fill = FlowColors.tintSoft,
        padding = PaddingValues(start = FlowSpace.xl, top = FlowSpace.md, end = FlowSpace.md, bottom = FlowSpace.md),
        onClick = { onChange(!on) },
        role = Role.Switch,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = FlowType.titleMedium.copy(fontWeight = FontWeight.Normal))
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.scan_back_hint), style = FlowType.bodySmall)
            }
            Switch(
                checked = on,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FlowColors.onPrimary,
                    checkedTrackColor = FlowColors.accent,
                    checkedBorderColor = FlowColors.accent,
                    uncheckedThumbColor = FlowColors.textMuted,
                    uncheckedTrackColor = FlowColors.surfaceMuted,
                    uncheckedBorderColor = FlowColors.line,
                ),
            )
        }
    }
}
