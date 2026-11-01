package tz.co.digid.example.ui

import android.app.Activity
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import tz.co.digid.example.R
import tz.co.digid.example.ui.theme.FlowColors
import tz.co.digid.example.ui.theme.FlowShape
import tz.co.digid.example.ui.theme.FlowSize
import tz.co.digid.example.ui.theme.FlowSpace
import tz.co.digid.example.ui.theme.FlowType
import tz.co.digid.example.ui.theme.Tone

// The drop-in flow's widget kit (the Flutter SDK's lib/src/ui/kit.dart), in Compose: a page,
// flat panels with no borders or shadows, one black pill per screen and "›" text links for
// everything secondary. Copy these into your app and restyle them as you like.

/** Dark status-bar icons on the light pages, light ones over the camera. */
@Composable
fun StatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context.findActivity() as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = dark
    }
}

/** A tap with no ripple: the flow's controls have no splash. */
private fun Modifier.tap(enabled: Boolean = true, role: Role = Role.Button, interaction: MutableInteractionSource? = null, onClick: () -> Unit) =
    clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)

/** The one primary action: a solid black pill, 56 dp tall. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        !enabled -> FlowColors.surfaceMuted
        pressed -> FlowColors.primaryFill.copy(alpha = .85f)
        else -> FlowColors.primaryFill
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(FlowSize.buttonHeight)
            .clip(CircleShape)
            .background(fill)
            .tap(enabled = enabled, interaction = interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = FlowType.titleMedium, color = if (enabled) FlowColors.onPrimary else FlowColors.textMuted)
    }
}

/** A secondary action: plain text and a "›". */
@Composable
fun ChevronLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = FlowColors.text) {
    val style = FlowType.titleMedium.copy(fontWeight = FontWeight.Normal, color = color)
    Row(
        modifier.clip(CircleShape).tap(onClick = onClick).padding(FlowSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = style)
        Spacer(Modifier.width(6.dp))
        Text("›", style = style.copy(fontSize = 19.sp, lineHeight = 19.sp))
    }
}

/** A flat rounded panel: a white card or a lavender tint. No border, no shadow. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    fill: Color = FlowColors.surface,
    padding: PaddingValues = PaddingValues(FlowSpace.xl),
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FlowShape.card))
            .background(fill)
            .then(if (onClick != null) Modifier.tap(role = role, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** A small tag: ube text on lavender, or a status tone ("Evaluation"). */
@Composable
fun Tag(label: String, tone: Tone? = null) {
    Text(
        label,
        style = FlowType.labelMedium.copy(fontWeight = FontWeight.Normal),
        color = tone?.color ?: FlowColors.accentDeep,
        modifier = Modifier
            .clip(RoundedCornerShape(FlowShape.tag))
            .background(tone?.background ?: FlowColors.tint)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** A pill track with one white chip for the picked option (the EN / SW switch). */
@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, label: String? = null) {
    Row(
        Modifier
            .semantics { label?.let { contentDescription = it } }
            .clip(CircleShape)
            .background(FlowColors.surfaceMuted)
            .padding(3.dp),
    ) {
        for ((option, text) in options) {
            val on = option == value
            val chip by animateColorAsState(if (on) FlowColors.surface else FlowColors.surfaceMuted.copy(alpha = 0f), tween(200), label = "chip")
            Text(
                text,
                style = FlowType.labelMedium,
                color = if (on) FlowColors.text else FlowColors.textMuted,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(chip)
                    .semantics { selected = on }
                    .tap(role = Role.Tab) { onChange(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Back arrow and title on the left; a quiet step number ("1 / 3") and an optional action on the right. */
@Composable
fun AppTopBar(title: String = "", onBack: (() -> Unit)?, step: String? = null, tint: Color = FlowColors.text, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = FlowSpace.xs, top = FlowSpace.xs, end = FlowSpace.sm).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconAction(Tabler.arrowLeft, stringResource(R.string.common_back), tint, onBack) else Spacer(Modifier.width(FlowSpace.lg))
        Text(title, style = FlowType.titleSmall, color = tint, maxLines = 1, modifier = Modifier.weight(1f).semantics { heading() })
        if (step != null) Text(step, style = FlowType.labelSmall, modifier = Modifier.padding(horizontal = FlowSpace.md))
        trailing?.invoke()
    }
}

/** A 48 dp touch target with a 22 dp line icon. */
@Composable
fun IconAction(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).tap(onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** "Powered by" and the digid wordmark (the letters in the text colour, the mark in ube). */
@Composable
fun PoweredBy(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.common_powered_by)
    val wordmark = remember { digidWordmark(FlowColors.text) }
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$label digid" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = FlowType.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = FlowColors.textMuted)
        Spacer(Modifier.width(6.dp))
        Image(wordmark, contentDescription = null, modifier = Modifier.padding(top = 2.dp).height(14.dp).width(14.dp * 78 / 31))
    }
}

/**
 * The minimal layout's page: a slim top bar (the back arrow and the step number), one centred
 * column at most 420 dp wide (vertically centred when it fits), and [PoweredBy] at the foot.
 * Pages put their pill and links in [content]. [overlay] is painted over everything.
 */
@Composable
fun MinimalPage(
    onBack: (() -> Unit)?,
    step: String? = null,
    overlay: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    StatusBarIcons(dark = true)
    Box(Modifier.fillMaxSize().background(FlowColors.canvas)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            AppTopBar(onBack = onBack, step = step)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val pad = PaddingValues(start = FlowSpace.page, top = FlowSpace.sm, end = FlowSpace.page, bottom = FlowSpace.xl)
                val minHeight = maxHeight - FlowSpace.sm - FlowSpace.xl
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = minHeight), contentAlignment = Alignment.Center) {
                        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), content = content)
                    }
                }
            }
            PoweredBy(Modifier.padding(bottom = FlowSpace.lg))
        }
        overlay?.invoke()
    }
}

/** The minimal layout's short title, centred. */
@Composable
fun MinimalTitle(text: String, size: Int = 20) =
    Text(text, style = FlowType.minimalTitle(size), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })

/** The one muted line under a title, centred. */
@Composable
fun MinimalLead(text: String) = Text(text, style = FlowType.minimalLead, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

/** A small status mark: a tone-coloured disc with a white icon. */
@Composable
fun StatusMark(tone: Tone, icon: ImageVector, size: Dp = 32.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(tone.color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = FlowColors.onPrimary, modifier = Modifier.size(size * .56f))
    }
}

/**
 * A calm full-screen message for errors and blocked states: a status mark, the headline, one
 * plain sentence, one pill (default "Go back").
 */
@Composable
fun MessageScreen(
    heading: String,
    message: String,
    onBack: () -> Unit,
    icon: ImageVector = Tabler.x,
    tone: Tone = Tone.DANGER,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondary: @Composable (() -> Unit)? = null,
) {
    MinimalPage(onBack = onBack) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PopIn { StatusMark(tone, icon, 44.dp) } }
        Spacer(Modifier.height(FlowSpace.lg))
        MinimalTitle(heading)
        Spacer(Modifier.height(FlowSpace.sm))
        MinimalLead(message)
        Spacer(Modifier.height(FlowSpace.xl))
        PrimaryButton(actionLabel ?: stringResource(R.string.common_go_back), onAction ?: onBack)
        secondary?.let { Box(Modifier.fillMaxWidth().padding(top = FlowSpace.xs), contentAlignment = Alignment.Center) { it() } }
    }
}

// ------------------------------------------------------------------ result pieces

/**
 * The result's status: a shield (Tabler shield-check / -exclamation / -x) in the outcome's
 * colour on a soft tinted circle with a fainter halo, scaling in.
 */
@Composable
fun StatusShield(tone: Tone, icon: ImageVector) {
    PopIn {
        Box(Modifier.size(100.dp).clip(CircleShape).background(tone.color.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(lerp(FlowColors.surface, tone.color, .14f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tone.color, modifier = Modifier.size(38.dp))
            }
        }
    }
}

/** One label / value line of a [DetailCard]; a null value shows as a dash. */
data class DetailRow(val label: String, val value: String?)

/** Label / value rows in one white card, with hairlines between them. */
@Composable
fun DetailCard(rows: List<DetailRow>) {
    Panel(padding = PaddingValues(horizontal = FlowSpace.xl, vertical = FlowSpace.xs)) {
        Column {
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(thickness = 1.dp, color = FlowColors.line)
                Row(Modifier.fillMaxWidth().padding(vertical = FlowSpace.lg)) {
                    Text(row.label, style = FlowType.bodySmall, modifier = Modifier.alignByBaseline())
                    Spacer(Modifier.width(FlowSpace.lg))
                    Text(
                        row.value ?: "—",
                        style = FlowType.bodyMedium.copy(color = FlowColors.text, fontFeatureSettings = "tnum"),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f).alignByBaseline(),
                    )
                }
            }
        }
    }
}

/** One line of a [ChecksCard]: what was checked and how it went. */
data class CheckItem(val label: String, val outcome: Outcome)

/**
 * What passed and what did not: a white card with a header (title and counts, "4 passed · 1 to
 * review") and one line per check with a filled status icon. [collapsed]: behind a "See details"
 * toggle. Outcomes only.
 */
@Composable
fun ChecksCard(items: List<CheckItem>, collapsed: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    val count = Outcome.entries.mapNotNull { o ->
        val n = items.count { it.outcome == o }
        if (n == 0) null else stringResource(o.count, n)
    }.joinToString(" · ")
    val countStyle = FlowType.bodySmall.copy(lineHeight = 20.sp)
    Panel(padding = PaddingValues(horizontal = FlowSpace.lg + 4.dp), modifier = Modifier.animateContentSize(tween(200))) {
        Column {
            val turn by animateFloatAsState(if (open) 90f else 0f, tween(200), label = "chevron")
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .then(if (collapsed) Modifier.tap { open = !open } else Modifier)
                    .padding(vertical = FlowSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (collapsed) R.string.details_toggle else R.string.details_title),
                    style = FlowType.titleMedium.copy(lineHeight = 24.sp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(FlowSpace.md))
                Text(count, style = countStyle, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 180.dp))
                if (collapsed) {
                    Spacer(Modifier.width(FlowSpace.sm))
                    Text("›", style = FlowType.titleMedium.copy(fontSize = 19.sp, lineHeight = 19.sp, color = FlowColors.textMuted), modifier = Modifier.rotate(turn))
                }
            }
            if (!collapsed || open) {
                Column(Modifier.padding(bottom = FlowSpace.sm)) {
                    for (item in items) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(item.outcome.dot, contentDescription = null, tint = item.outcome.tone.color, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(FlowSpace.md))
                            Text(item.label, style = FlowType.bodyMedium.copy(color = FlowColors.text, lineHeight = 20.sp), modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(FlowSpace.md))
                            Text(
                                stringResource(item.outcome.label),
                                style = countStyle,
                                color = if (item.outcome == Outcome.PASSED) FlowColors.textMuted else item.outcome.tone.color,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ motion

/** Fade and a 16 dp rise when first shown, once. */
@Composable
fun FadeSlideIn(delayMs: Int = 0, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(450, easing = EaseOutCubic))
    }
    Box(Modifier.graphicsLayer { alpha = p.value; translationY = 16.dp.toPx() * (1 - p.value) }) { content() }
}

/** Scale up from 80 % with a soft overshoot, once (the status marks, the camera check). */
@Composable
fun PopIn(content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(420)) }
    Box(
        Modifier.graphicsLayer {
            alpha = (p.value * 1.6f).coerceAtMost(1f)
            val s = .8f + .2f * EaseOutBack.transform(p.value)
            scaleX = s
            scaleY = s
        },
    ) { content() }
}
