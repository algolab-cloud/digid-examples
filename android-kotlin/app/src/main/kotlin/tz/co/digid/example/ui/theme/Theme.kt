package tz.co.digid.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import tz.co.digid.example.R

// The look of the digid drop-in flow (the Flutter SDK's lib/src/ui/theme.dart, the web SDK's
// src/ui/styles.ts), with its default colours:
// - A light grey page, flat white and lavender panels, no borders, no shadows; 1 dp hairlines
//   only divide rows of text.
// - Inter, light (300) and large for headlines; nothing heavier than medium (500).
// - One black pill per screen; secondary actions are "›" text links.
// - The accent appears as tints, small tags and toggles, never on a button.
// - The camera is full-bleed: corner brackets turn green when the card is framed, and the
//   instruction sits in a navy box.
// - Status colour stays small: green pass, amber review, red fail.

/** Colour roles. Screens say what a colour is for, never which colour it is. */
object FlowColors {
    /** The page behind everything. */
    val canvas = Color(0xFFF6F6F6)

    /** Cards on the page. */
    val surface = Color.White

    /** Segmented-control tracks and disabled fills. */
    val surfaceMuted = Color(0xFFECECEC)

    /** Headlines and strong labels; running copy; secondary copy; the quietest labels. */
    val text = Color.Black
    val textBody = Color(0xFF2B2B2B)
    val textMuted = Color(0xFF5A5A5A)
    val textSubtle = Color(0xFF787878)

    /** The one primary action, and its text. */
    val primaryFill = Color.Black
    val onPrimary = Color.White

    /** Hairlines between rows of text. Never around a card. */
    val line = Color(0xFFD4D4D4)

    /** Accent panel fills, lightest to strongest. */
    val tintSoft = Color(0xFFF4F5FF)
    val tint = Color(0xFFE9EAFF)
    val tintStrong = Color(0xFFD3D5FF)

    /** Tags, illustrations, toggles. Never a button fill. */
    val accent = Color(0xFF3F48FD)

    /** Tag text on [tint]. */
    val accentDeep = Color(0xFF020CD0)

    /** The instruction box over the camera (navy at 90 %), and its text. */
    val instruction = Color(0xE6010334)
    val onInstruction = Color.White

    /** The camera brackets / oval, and their ready colour. */
    val frame = Color.White
    val frameReady = Color(0xFF22C55E)

    val success = Color(0xFF15803D)
    val warning = Color(0xFFB45309)
    val danger = Color(0xFFB42318)
    val successBg = Color(0xFFEAF7EE)
    val warningBg = Color(0xFFFEF3C7)
    val dangerBg = Color(0xFFFEECEC)
}

/** Status tones: pass, review, fail, plus neutral. */
enum class Tone(val color: Color, val background: Color) {
    NEUTRAL(FlowColors.textMuted, FlowColors.surfaceMuted),
    SUCCESS(FlowColors.success, FlowColors.successBg),
    WARNING(FlowColors.warning, FlowColors.warningBg),
    DANGER(FlowColors.danger, FlowColors.dangerBg),
}

/** Corner radii (the flow's default `theme.radius` of 24). */
object FlowShape {
    /** Cards, panels and the instruction box. */
    val card = 24.dp

    /** Small tags. */
    val tag = 8.dp
}

/** 4-dp spacing grid. */
object FlowSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val page = xl
}

object FlowSize {
    /** Primary buttons: 56 dp tall. */
    val buttonHeight = 56.dp
    val icon = 20.dp
}

/** Inter (SIL OFL, see OFL-Inter.txt), weights 300 / 400 / 500, as bundled with the Flutter SDK. */
val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)

/**
 * The type roles (sentence case everywhere; nothing heavier than 500):
 * - [displaySmall] the one big headline per screen (38, light, tight)
 * - [titleLarge] step names (22, light)
 * - [titleMedium] buttons and links (16, medium); [titleSmall] top-bar titles (15, medium)
 * - [bodyLarge] the lead under a headline (18, light); [bodyMedium] running text (15);
 *   [bodySmall] secondary text and detail labels (14, muted)
 * - [labelLarge] group titles (15, medium); [labelMedium] tags and segmented controls (13,
 *   medium); [labelSmall] step numbers (13, subtle)
 * - [minimalTitle] / [minimalLead]: the minimal layout's short title (20, medium) and its one
 *   muted line.
 */
object FlowType {
    private fun light(size: Int, height: Float, em: Float) =
        TextStyle(fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = size.sp, lineHeight = (size * height).sp, letterSpacing = em.em, color = FlowColors.text)

    private val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, color = FlowColors.textBody)
    private val medium = body.copy(fontWeight = FontWeight.Medium, color = FlowColors.text)

    val displaySmall = light(38, 1.05f, -0.012f)
    val titleLarge = light(22, 1.2f, -0.006f)
    val titleMedium = medium.copy(fontSize = 16.sp, lineHeight = 20.sp)
    val titleSmall = medium.copy(fontSize = 15.sp, lineHeight = 19.5.sp)
    val labelLarge = medium.copy(fontSize = 15.sp)
    val labelMedium = medium.copy(fontSize = 13.sp, lineHeight = 17.sp)
    val labelSmall = body.copy(fontSize = 13.sp, lineHeight = 17.sp, color = FlowColors.textSubtle)
    val bodyLarge = body.copy(fontSize = 18.sp, lineHeight = 24.3.sp, fontWeight = FontWeight.Light)
    val bodyMedium = body.copy(fontSize = 15.sp, lineHeight = 22.5.sp)
    val bodySmall = body.copy(fontSize = 14.sp, lineHeight = 20.3.sp, color = FlowColors.textMuted)

    fun minimalTitle(size: Int = 20) =
        titleLarge.copy(fontSize = size.sp, lineHeight = (size * 1.3f).sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01f).em)

    val minimalLead = bodyMedium.copy(color = FlowColors.textMuted)
}

private val colors = lightColorScheme(
    primary = FlowColors.primaryFill,
    onPrimary = FlowColors.onPrimary,
    secondary = FlowColors.accent,
    onSecondary = FlowColors.onPrimary,
    background = FlowColors.canvas,
    surface = FlowColors.surface,
    onSurface = FlowColors.text,
    onSurfaceVariant = FlowColors.textMuted,
    surfaceContainerLow = FlowColors.canvas,
    surfaceContainer = FlowColors.surfaceMuted,
    error = FlowColors.danger,
    outline = FlowColors.line,
    outlineVariant = FlowColors.line,
)

@Composable
fun DigidExampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            displaySmall = FlowType.displaySmall,
            titleLarge = FlowType.titleLarge,
            titleMedium = FlowType.titleMedium,
            titleSmall = FlowType.titleSmall,
            bodyLarge = FlowType.bodyLarge,
            bodyMedium = FlowType.bodyMedium,
            bodySmall = FlowType.bodySmall,
            labelLarge = FlowType.labelLarge,
            labelMedium = FlowType.labelMedium,
            labelSmall = FlowType.labelSmall,
        ),
        content = content,
    )
}
