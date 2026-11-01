package tz.co.digid.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import tz.co.digid.example.Capture
import tz.co.digid.example.DigidEngine.Companion.TAG
import tz.co.digid.example.R
import tz.co.digid.example.ui.theme.FlowColors
import tz.co.digid.example.ui.theme.FlowShape
import tz.co.digid.example.ui.theme.FlowSpace
import tz.co.digid.example.ui.theme.FlowType
import tz.co.digid.sdk.Digid
import tz.co.digid.sdk.Guidance
import tz.co.digid.sdk.NidaFrameAnalyzer
import tz.co.digid.sdk.Lang
import tz.co.digid.sdk.StepKind
import java.util.concurrent.Executor
import kotlin.math.min

// The card and selfie camera, as in the drop-in flow (the Flutter SDK's capture_page.dart):
// full-bleed preview, rounded corner brackets around an ID-1 frame (or an oval for the selfie)
// that turn green once the card sits inside, a soft scrim under the top bar, and the instruction
// in a navy box at the foot with the step's progress. A check pops in when a side is accepted.

/** The navy of the camera chrome at 45 %: the round buttons over the picture. */
private val ChromeButton = Color(0x73010334)

/** Guidance that means the card is not framed yet (the brackets stay white). */
private val NotFramed = setOf(Guidance.NO_CARD, Guidance.MOVE_CLOSER, Guidance.TOO_CLOSE, Guidance.FLIP_TO_BACK)

@Composable
fun CameraScreen(
    capture: Capture,
    lang: Lang,
    analyzerFor: (front: Boolean) -> ImageAnalysis.Analyzer?,
    frameExecutor: Executor,
    onImageCapture: (ImageCapture?) -> Unit,
    onShutter: () -> Unit,
    onBack: () -> Unit,
    onFinishNow: (() -> Unit)?,
) {
    CameraPermissionGate(onBack) {
        // The card steps use the back camera; the selfie step switches to the front one.
        val front = capture.step == StepKind.SELFIE
        val analyzer = remember(front) { analyzerFor(front) }
        var camera by remember { mutableStateOf<Camera?>(null) }
        var cameraFailed by remember { mutableStateOf(false) }
        var torchOn by remember(front) { mutableStateOf(false) }

        when {
            cameraFailed -> MessageScreen(
                heading = stringResource(R.string.error_camera_heading),
                message = stringResource(R.string.error_camera_message),
                icon = Tabler.camera,
                onBack = onBack,
            )
            capture.evaluationCapReached -> MessageScreen(
                heading = stringResource(R.string.error_evaluation_cap_heading),
                message = stringResource(R.string.error_evaluation_cap_message),
                onBack = onBack,
            )
            else -> {
                StatusBarIcons(dark = false)
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    // The preview fades in from black when a camera opens (or switches lens).
                    val shown = remember(front) { Animatable(0f) }
                    LaunchedEffect(camera, front) { if (camera != null) shown.animateTo(1f, tween(300)) }
                    CameraPreview(
                        front = front,
                        analyzer = analyzer,
                        frameExecutor = frameExecutor,
                        onImageCapture = onImageCapture,
                        onCamera = { camera = it },
                        onError = { cameraFailed = true },
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown.value },
                    )
                    LaunchedEffect(camera, torchOn) { camera?.cameraControl?.enableTorch(torchOn) }

                    GuideFrame(capture, Modifier.fillMaxSize())
                    CameraScrim()

                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        CameraTopBar(
                            step = capture.accepted ?: capture.step,
                            onBack = onBack,
                            torchAvailable = !front && camera?.cameraInfo?.hasFlashUnit() == true,
                            torchOn = torchOn,
                            onTorch = { torchOn = !torchOn },
                        )
                        Spacer(Modifier.weight(1f))
                        val accepted = capture.accepted
                        val skipped = accepted == StepKind.SCAN_BACK && capture.backSkipped
                        val haptics = LocalHapticFeedback.current
                        LaunchedEffect(accepted) { if (accepted != null && !skipped) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
                        if (accepted != null && !skipped) {
                            key(accepted) {
                                Box(Modifier.fillMaxWidth().padding(bottom = FlowSpace.lg), contentAlignment = Alignment.Center) { PopIn { DoneCheck() } }
                            }
                        } else if (capture.shutter && !capture.photographing && !front) {
                            // The automatic photo has not happened: let the person take it.
                            Box(Modifier.fillMaxWidth().padding(bottom = FlowSpace.lg), contentAlignment = Alignment.Center) { ShutterButton(onShutter) }
                        }
                        InstructionBox(
                            message = cameraMessage(capture, lang),
                            progress = if (capture.running && accepted == null) capture.progress else null,
                            onFinishNow = onFinishNow,
                            modifier = Modifier.padding(start = FlowSpace.lg, end = FlowSpace.lg, bottom = FlowSpace.xl),
                        )
                    }
                }
            }
        }
    }
}

/** What the instruction box says. */
@Composable
private fun cameraMessage(capture: Capture, lang: Lang): String {
    val accepted = capture.accepted
    val g = capture.guidance
    return when {
        accepted == StepKind.SELFIE -> stringResource(R.string.capture_selfie_taken)
        accepted == StepKind.SCAN_BACK -> stringResource(if (capture.backSkipped) R.string.capture_back_skipped else R.string.capture_back_read)
        accepted != null -> stringResource(R.string.capture_card_read)
        !capture.running -> stringResource(R.string.common_starting_camera)
        capture.photographing -> stringResource(if (capture.step == StepKind.SCAN_BACK) R.string.capture_reading_back else R.string.capture_reading_card)
        capture.step == StepKind.SCAN_FRONT && g == Guidance.NO_CARD -> stringResource(R.string.capture_place_card)
        // The flow's own words for every code; the engine's for a code newer than this app.
        else -> g.label()?.let { stringResource(it) } ?: remember(g, lang) { Digid.guidanceText(g, lang) }
    }
}

/** Over the camera: a white back arrow, the step's title in white, and the torch. */
@Composable
private fun CameraTopBar(step: StepKind, onBack: () -> Unit, torchAvailable: Boolean, torchOn: Boolean, onTorch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = FlowSpace.xs, top = FlowSpace.xs, end = FlowSpace.lg).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(Tabler.arrowLeft, stringResource(R.string.common_back), FlowColors.onInstruction, onBack)
        AnimatedContent(step.label(), Modifier.weight(1f), transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "title") { title ->
            Text(
                stringResource(title),
                style = FlowType.titleSmall.copy(color = FlowColors.onInstruction, shadow = Shadow(Color.Black.copy(alpha = .4f), blurRadius = 8f)),
                maxLines = 1,
            )
        }
        if (torchAvailable) TorchButton(torchOn, onTorch)
    }
}

/** The torch toggle: a round navy button, white when the torch is on. */
@Composable
private fun TorchButton(on: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.torch)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (on) FlowColors.onInstruction else ChromeButton)
            .clickable(role = Role.Switch, onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = if (on) "on" else "off"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Tabler.bolt, contentDescription = null, tint = if (on) Color(0xFF010334) else FlowColors.onInstruction, modifier = Modifier.size(20.dp))
    }
}

/** A soft shade under the status bar and the top bar, so the white title stays readable. */
@Composable
private fun CameraScrim() {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        Modifier
            .fillMaxWidth()
            .height(top + 120.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .42f), Color.Transparent))),
    )
}

/** The navy rounded instruction box at the foot of the camera, with a thin progress bar. */
@Composable
private fun InstructionBox(message: String, progress: Float?, onFinishNow: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FlowShape.card))
            .background(FlowColors.instruction)
            .animateContentSize(tween(200))
            .padding(horizontal = FlowSpace.xl, vertical = FlowSpace.lg + 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(message, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "guidance") { text ->
            Text(
                text,
                style = FlowType.bodyLarge.copy(fontSize = 17.sp, lineHeight = 23.sp, color = FlowColors.onInstruction),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (progress != null) {
            val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(250), label = "progress")
            Spacer(Modifier.height(FlowSpace.md))
            LinearProgressIndicator(
                progress = { shown },
                modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)),
                color = FlowColors.onInstruction,
                trackColor = FlowColors.onInstruction.copy(alpha = .2f),
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        if (onFinishNow != null) {
            // Debug builds only: end the attempt with the best result so far (session.finish()).
            ChevronLink(stringResource(R.string.finish_now_debug), onFinishNow, Modifier.padding(top = FlowSpace.xs), color = FlowColors.onInstruction.copy(alpha = .7f))
        }
    }
}

/** The manual shutter: a white ring around a white disc, as in the Flutter flow. */
@Composable
private fun ShutterButton(onClick: () -> Unit) {
    val label = stringResource(R.string.common_take_photo)
    PopIn {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(3.dp, FlowColors.onInstruction, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label }
                .padding(7.dp)
                .clip(CircleShape)
                .background(FlowColors.onInstruction),
        )
    }
}

/** The check over the camera when a capture is accepted, in the frame's ready colour. */
@Composable
private fun DoneCheck() {
    Box(Modifier.size(48.dp).clip(CircleShape).background(FlowColors.frameReady), contentAlignment = Alignment.Center) {
        Icon(Tabler.check, contentDescription = null, tint = FlowColors.onInstruction, modifier = Modifier.size(27.dp))
    }
}

/**
 * The capture frame: rounded corner brackets around an ID-1 area, or an oval for the selfie,
 * with no scrim so the whole scene stays visible. White while searching, green once the card
 * sits inside the frame; a thin full outline joins the brackets when a side is accepted.
 */
@Composable
private fun GuideFrame(capture: Capture, modifier: Modifier = Modifier) {
    val step = capture.accepted ?: capture.step
    val cardStep = step == StepKind.SCAN_FRONT || step == StepKind.SCAN_BACK || step == StepKind.HOLD_CARD
    val framed = cardStep && (capture.ready || capture.photographing || (capture.overlay.cardQuad != null && capture.guidance !in NotFramed))
    val accepted = capture.accepted != null
    val green by animateFloatAsState(if (accepted || framed) 1f else 0f, tween(250), label = "frame")
    Canvas(modifier) {
        val color = lerp(FlowColors.frame, FlowColors.frameReady, green)
        when {
            cardStep -> {
                val r = cardRect(size)
                if (accepted) drawRoundRect(FlowColors.frameReady, r.topLeft, r.size, CornerRadius(10.dp.toPx()), style = Stroke(2.dp.toPx()))
                drawBrackets(r, color, if (green > 0f) 5.dp.toPx() else 4.dp.toPx())
            }
            step == StepKind.SELFIE -> {
                val o = ovalRect(size)
                drawOval(color, o.topLeft, o.size, style = Stroke(3.dp.toPx()))
            }
        }
    }
}

/** An ID-1 card: 85.60 x 53.98 mm. */
private const val ID1 = 85.60f / 53.98f

/** The centred ID-1 frame. */
private fun cardRect(size: Size): Rect {
    val c = Offset(size.width / 2, size.height / 2 - size.height * .04f)
    val w = min(size.width * .84f, size.height * .6f * ID1)
    val h = w / ID1
    return Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)
}

/** The selfie oval. */
private fun ovalRect(size: Size): Rect {
    val c = Offset(size.width / 2, size.height / 2 - size.height * .08f)
    val w = min(size.width * .66f, size.height * .5f)
    val h = w * .86f / .66f
    return Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)
}

/** Four rounded corner brackets on [r], 34 dp long with a 10 dp radius. */
private fun DrawScope.drawBrackets(r: Rect, color: Color, width: Float) {
    val l = 34.dp.toPx()
    val rad = 10.dp.toPx()
    val stroke = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    for ((p, sx, sy) in listOf(Triple(r.topLeft, 1f, 1f), Triple(r.topRight, -1f, 1f), Triple(r.bottomLeft, 1f, -1f), Triple(r.bottomRight, -1f, -1f))) {
        val centre = Offset(p.x + rad * sx, p.y + rad * sy)
        val path = Path().apply {
            moveTo(p.x, p.y + l * sy)
            lineTo(p.x, p.y + rad * sy)
            arcTo(Rect(centre, rad), if (sx > 0) 180f else 0f, sx * sy * 90f, false)
            lineTo(p.x + l * sx, p.y)
        }
        drawPath(path, color, style = stroke)
    }
}

/** A CameraX preview with the analyzer bound next to it. Rebinds when [front] or [analyzer] changes. */
@Composable
private fun CameraPreview(
    front: Boolean,
    analyzer: ImageAnalysis.Analyzer?,
    frameExecutor: Executor,
    onImageCapture: (ImageCapture?) -> Unit,
    onCamera: (Camera?) -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(front, analyzer, lifecycleOwner) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = future.get().also { provider = it }
                // Preview and analysis share one aspect ratio, so the overlay lines up with the preview.
                val aspect = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                val preview = Preview.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                // 1080p frames: card text and the printed portrait need the pixels (720p is too small).
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(NidaFrameAnalyzer.resolutionSelector())
                    // The analyzer runs the engine synchronously: drop frames while one is processed.
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                analyzer?.let { analysis.setAnalyzer(frameExecutor, it) }
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                // The back camera also takes the full-resolution photo of the card.
                val photo = if (front) null else ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
                    .build()
                cameraProvider.unbindAll()
                val useCases = listOfNotNull(preview, analysis, photo).toTypedArray()
                onCamera(cameraProvider.bindToLifecycle(lifecycleOwner, selector, *useCases))
                onImageCapture(photo)
                Log.i(TAG, "camera bound (${if (front) "front" else "back"})")
            } catch (e: Exception) {
                Log.e(TAG, "camera failed to start", e)
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            onImageCapture(null)
            onCamera(null)
        }
    }
}

/**
 * Asks for the camera when the screen opens. If the person declines, explains why it is needed
 * and asks again; once Android stops showing the prompt, links to the app's settings instead.
 */
@Composable
private fun CameraPermissionGate(onBack: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted && !asked) launcher.launch(Manifest.permission.CAMERA) }
    // Coming back from the settings screen.
    LifecycleResumeEffect(Unit) {
        granted = context.hasCameraPermission()
        onPauseOrDispose { }
    }

    if (granted) {
        content()
        return
    }
    if (!asked) {
        // The system prompt is up: keep the screen black behind it.
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val canAskAgain = context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
    MessageScreen(
        heading = stringResource(R.string.error_camera_permission_heading),
        message = stringResource(if (canAskAgain) R.string.error_camera_permission_rationale else R.string.error_camera_permission_message),
        icon = Tabler.camera,
        onBack = onBack,
        actionLabel = stringResource(if (canAskAgain) R.string.error_camera_permission_allow else R.string.error_camera_permission_action),
        onAction = {
            if (canAskAgain) {
                launcher.launch(Manifest.permission.CAMERA)
            } else {
                context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }
        },
    )
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
