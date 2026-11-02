package tz.co.digid.example

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tz.co.digid.example.DigidEngine.Companion.TAG
import tz.co.digid.sdk.ConnectionReason
import tz.co.digid.sdk.ConnectionRequiredException
import tz.co.digid.sdk.FeatureNotLicensedException
import tz.co.digid.sdk.Flow
import tz.co.digid.sdk.FrameCheck
import tz.co.digid.sdk.Guidance
import tz.co.digid.sdk.KycResult
import tz.co.digid.sdk.Lang
import tz.co.digid.sdk.NidaFrameAnalyzer
import tz.co.digid.sdk.NidaImage
import tz.co.digid.sdk.NidaKycSession
import tz.co.digid.sdk.Overlay
import tz.co.digid.sdk.QuotaReachedException
import tz.co.digid.sdk.StepKind
import tz.co.digid.sdk.StepUpdate
import tz.co.digid.sdk.connectionReason
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** What the start screen lets the person choose. The language belongs to the app, not the SDK. */
data class Settings(
    val withSelfie: Boolean = true,
    val scanBack: Boolean = false,
    val lang: Lang = if (Locale.getDefault().language == "sw") Lang.SW else Lang.EN,
) {
    val flow: Flow get() = if (withSelfie) Flow.CardThenSelfie(requireBack = scanBack) else Flow.CardOnly(requireBack = scanBack)

    /** The steps this flow goes through, in order (for "step 1 of 3"). */
    val steps: List<StepKind>
        get() = buildList {
            add(StepKind.SCAN_FRONT)
            if (scanBack) add(StepKind.SCAN_BACK)
            if (withSelfie) add(StepKind.SELFIE)
        }
}

/** The live state of the camera screen, from the latest [StepUpdate]. */
data class Capture(
    val step: StepKind = StepKind.SCAN_FRONT,
    val guidance: Guidance = Guidance.NO_CARD,
    val progress: Float = 0f,
    val overlay: Overlay = Overlay(),
    /** Frames are reaching the engine (the camera is running). */
    val running: Boolean = false,
    /**
     * The step whose capture was just accepted: the frame turns green and a check shows for a
     * moment (the next step, or the result, follows).
     */
    val accepted: StepKind? = null,
    /** The engine gave up on the back of the card (its step timed out) and moved on. */
    val backSkipped: Boolean = false,
    /** Front of the card: the viewfinder check says the card is framed well enough for the photo. */
    val ready: Boolean = false,
    /** A photo is being taken and read ("Reading the card…"). */
    val photographing: Boolean = false,
    /** The manual shutter: offered when the automatic photo has not happened after a while. */
    val shutter: Boolean = false,
    val evaluationCapReached: Boolean = false,
)

/** A blocking problem, shown on a message screen. */
enum class Problem {
    NOT_LICENSED,
    EVALUATION_CAP,
    START_FAILED,

    /** This month's checks are used up (free plan): verifying starts again on the 1st. */
    QUOTA_REACHED,

    /** Verifying waits until the phone reaches digid once (Try again runs `online()`). */
    CONNECTION_REQUIRED,

    /** The free plan: this check could not get digid's permission to start (Try again asks again). */
    CONNECTION_CHECK,

    /** digid refused to renew the app's licence: connecting again will not help. */
    RENEWAL_REFUSED,
}

/** Start (the app's own) → Intro → Camera → Result: the screens of the drop-in flow. */
sealed interface Screen {
    data object Start : Screen
    data object Intro : Screen
    data class Camera(val capture: Capture = Capture()) : Screen

    /** [resultJson] is what the app sends to its backend; [settings] are those the attempt ran with. */
    data class Result(val result: KycResult, val resultJson: String, val settings: Settings) : Screen

    /**
     * [resumeMonth] (1–12): for [Problem.QUOTA_REACHED], the month whose 1st verifying starts
     * again. [busy]: Try again is running (connecting to digid).
     */
    data class Message(val problem: Problem, val resumeMonth: Int? = null, val busy: Boolean = false) : Screen
}

data class UiState(
    val screen: Screen = Screen.Start,
    val settings: Settings = Settings(),
)

class KycViewModel(private val engine: DigidEngine) : ViewModel() {

    val engineState: StateFlow<DigidEngine.State> = engine.state

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /**
     * Every call into a session (push a frame, finish, close) runs on this one thread, in order:
     * a session must not be used from two threads at once, and must not be closed while a frame
     * is being processed.
     */
    private val sessionThread: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "digid-session") }
    val frameExecutor: Executor get() = sessionThread

    /** The session frames go to; null when none is running. Read and cleared on [sessionThread]. */
    @Volatile
    private var activeSession: NidaKycSession? = null

    /** Counts attempts, so a delayed step of one attempt never lands on the next. */
    private var attempt = 0

    /** When the back-of-card step began (to tell a read back from a skipped one). */
    private var backSince = 0L

    /** The back camera's photo use case, bound by the camera screen (null on the front camera). */
    @Volatile
    var imageCapture: ImageCapture? = null

    /** Viewfinder checks in a row that said ready; at [READY_FRAMES] the photo is taken. */
    private var readyStreak = 0

    /** A photo is being taken or read: frames are dropped meanwhile. */
    @Volatile
    private var photographing = false

    /** After a photo that was not accepted, frames resume at this time (elapsedRealtime). */
    @Volatile
    private var pausedUntil = 0L

    fun setWithSelfie(on: Boolean) = updateSettings { copy(withSelfie = on) }
    fun setScanBack(on: Boolean) = updateSettings { copy(scanBack = on) }
    fun setLang(lang: Lang) = updateSettings { copy(lang = lang) }

    private fun updateSettings(change: Settings.() -> Settings) = _ui.update { it.copy(settings = it.settings.change()) }

    /** Start → the flow's intro. */
    fun openIntro() = _ui.update { it.copy(screen = Screen.Intro) }

    /** The intro's back arrow, and Done on the result. */
    fun backToStart() = _ui.update { it.copy(screen = Screen.Start) }

    /** Open the camera: a new session for one attempt (the intro's pill, and Try again). */
    fun start() {
        val settings = _ui.value.settings
        val id = ++attempt
        viewModelScope.launch {
            // Before the camera opens: the licence may already say this attempt cannot run.
            licenceProblem()?.let { problem ->
                if (id == attempt) _ui.update { it.copy(screen = problem) }
                return@launch
            }
            val session = try {
                engine.newSession(settings.flow)
            } catch (e: FeatureNotLicensedException) {
                Log.w(TAG, "not licensed: ${e.feature}")
                _ui.update { it.copy(screen = Screen.Message(Problem.NOT_LICENSED)) }
                return@launch
            } catch (e: QuotaReachedException) {
                Log.w(TAG, "this month's checks are used up")
                _ui.update { it.copy(screen = quotaProblem()) }
                return@launch
            } catch (e: ConnectionRequiredException) {
                Log.w(TAG, "connection required: ${e.reason}")
                _ui.update { it.copy(screen = connectionProblem(e.connectionReason)) }
                return@launch
            } catch (e: Exception) {
                Log.e(TAG, "could not start a session", e)
                _ui.update { it.copy(screen = Screen.Message(Problem.START_FAILED)) }
                return@launch
            }
            if (id != attempt) {
                session.close()
                return@launch
            }
            if (session.evaluationCapReached) {
                session.close()
                _ui.update { it.copy(screen = Screen.Message(Problem.EVALUATION_CAP)) }
                return@launch
            }
            Log.i(TAG, "session started: ${settings.flow.toJson()}")
            activeSession = session
            readyStreak = 0
            photographing = false
            _ui.update { it.copy(screen = Screen.Camera()) }
            armShutter(StepKind.SCAN_FRONT)
        }
    }

    /** The problem screen the licence status calls for before a session starts, if any. */
    private suspend fun licenceProblem(): Screen.Message? {
        val licence = engine.licence()
        return when {
            licence.connectionRequired != null -> connectionProblem()
            licence.quotaReached -> quotaProblem()
            else -> null
        }
    }

    /**
     * "Connect to the internet": for this check's permission ([ConnectionReason.CHECK]), or to
     * renew and report; "could not be renewed" once digid has refused a renewal.
     */
    private suspend fun connectionProblem(reason: ConnectionReason? = null): Screen.Message = Screen.Message(
        when {
            reason == ConnectionReason.CHECK -> Problem.CONNECTION_CHECK
            engine.renewalRefusal() != null -> Problem.RENEWAL_REFUSED
            else -> Problem.CONNECTION_REQUIRED
        },
    )

    /** "This month's checks are used up", with the month verifying starts again. */
    private suspend fun quotaProblem(): Screen.Message =
        Screen.Message(Problem.QUOTA_REACHED, resumeMonth = monthAfter(engine.licence().quota?.period))

    /**
     * The connection screen's Try again: send the usage and renew the licence off the main
     * thread, then try to start again (which shows the screen again if verifying still waits).
     * For a check's permission, starting again is enough: it asks for the same check.
     */
    fun retryOnline() {
        val message = _ui.value.screen as? Screen.Message ?: return
        if (message.busy) return
        _ui.update { it.copy(screen = message.copy(busy = true)) }
        viewModelScope.launch {
            if (message.problem != Problem.CONNECTION_CHECK) engine.online()
            if (_ui.value.screen == message.copy(busy = true)) start()
        }
    }

    /**
     * The analyzer for the camera that is bound now (the back camera for the card, the front
     * one for the selfie), feeding the same session. Camera frames are never mirrored, even from
     * the front camera (only its preview is drawn mirrored), so `mirrored` stays false: a
     * mirrored selfie would not match the card's portrait as well.
     *
     * The front of the card is read from a photo, as in the Flutter and web flows: during that
     * step frames only run the cheap viewfinder check ([onCheck]), and once the card is framed
     * one full-resolution photo is taken ([takePhoto]). The back and the selfie stay live.
     */
    @Suppress("UNUSED_PARAMETER")
    fun analyzer(front: Boolean): ImageAnalysis.Analyzer? {
        val session = activeSession ?: return null
        val sdkAnalyzer = NidaFrameAnalyzer(
            session = session,
            onError = { Log.e(TAG, "frame failed", it) },
            onUpdate = { update -> onUpdate(session, update) },
        ).apply { onFrameCheck = { check -> onCheck(session, check) } }
        return ImageAnalysis.Analyzer { image: ImageProxy ->
            if (activeSession !== session || photographing || SystemClock.elapsedRealtime() < pausedUntil) {
                image.close() // after the attempt ended, or while a photo is taken and read
                return@Analyzer
            }
            sdkAnalyzer.analyze(image) // closes the image
        }
    }

    /** On [sessionThread]: the viewfinder check of one frame on the front-of-card step. */
    private fun onCheck(session: NidaKycSession, check: FrameCheck) {
        readyStreak = if (check.ready) readyStreak + 1 else 0
        updateCapture { copy(guidance = check.guidance, overlay = check.overlay, ready = check.ready, progress = 0f, running = true) }
        if (readyStreak >= READY_FRAMES) takePhoto(session)
    }

    /** The manual shutter (and the automatic one): one full-resolution photo of the card. */
    fun takePhoto() {
        activeSession?.let { takePhoto(it) }
    }

    private fun takePhoto(session: NidaKycSession) {
        val camera = imageCapture ?: return
        if (photographing) return
        photographing = true
        updateCapture { copy(photographing = true) }
        camera.takePicture(
            sessionThread,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // A JPEG; rotationDegrees is the turn that makes it upright.
                    val jpeg = image.planes[0].buffer.let { b -> ByteArray(b.remaining()).also { b.get(it) } }
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()
                    if (activeSession !== session) return photoDone()
                    val update = try {
                        session.pushStill(NidaImage.fromJpeg(jpeg, rotation))
                    } catch (e: Exception) {
                        if (e is QuotaReachedException || e is ConnectionRequiredException) {
                            Log.w(TAG, "photo refused by the licence: ${e.javaClass.simpleName}")
                            photoDone()
                            return stopForLicence(session, e)
                        }
                        Log.e(TAG, "photo not read", e)
                        return photoDone()
                    }
                    Log.i(TAG, "photo ${if (update.stepDone) "accepted" else "not read"}: step=${update.step.wire} guidance=${update.guidance.wire}")
                    photoDone()
                    onUpdate(session, update)
                }

                override fun onError(e: ImageCaptureException) {
                    Log.e(TAG, "photo failed", e)
                    photoDone()
                }
            },
        )
    }

    /**
     * On [sessionThread]: the licence refused the photo (checks used up, or verifying waits for
     * the licence server). End the attempt and show the problem screen.
     */
    private fun stopForLicence(session: NidaKycSession, e: Exception) {
        if (activeSession !== session) return
        activeSession = null
        session.close()
        val id = ++attempt
        viewModelScope.launch {
            val problem = when (e) {
                is QuotaReachedException -> quotaProblem()
                is ConnectionRequiredException -> connectionProblem(e.connectionReason)
                else -> connectionProblem()
            }
            _ui.update { if (id == attempt && it.screen is Screen.Camera) it.copy(screen = problem) else it }
        }
    }

    /** On [sessionThread]: a photo was read (or failed). Frames resume after a short pause. */
    private fun photoDone() {
        readyStreak = 0
        pausedUntil = SystemClock.elapsedRealtime() + PHOTO_PAUSE_MS
        photographing = false
        updateCapture { copy(photographing = false, ready = false) }
    }

    /** Offer the manual shutter if [step] is still waiting for a photo after [SHUTTER_AFTER_MS]. */
    private fun armShutter(step: StepKind) {
        val id = attempt
        viewModelScope.launch {
            delay(SHUTTER_AFTER_MS)
            if (id == attempt) updateCapture { if (this.step == step && accepted == null) copy(shutter = true) else this }
        }
    }

    /** On [sessionThread], after each frame. */
    private fun onUpdate(session: NidaKycSession, update: StepUpdate) {
        val previous = (_ui.value.screen as? Screen.Camera)?.capture
        if (previous == null || previous.step != update.step || previous.guidance != update.guidance) {
            Log.i(TAG, "step=${update.step.wire} guidance=${update.guidance.wire} progress=${"%.2f".format(update.progress)}")
        }
        val result = update.result
        if (result != null) {
            finishAttempt(session, result, previous?.step)
            return
        }
        val moved = previous != null && previous.step != update.step && update.step != StepKind.DONE && update.step != StepKind.UNKNOWN
        val now = SystemClock.elapsedRealtime()
        // The back of the card is live: when the engine moves on before reading it, its step timed out.
        val skipped = moved && previous?.step == StepKind.SCAN_BACK && now - backSince > (stepTimeoutS() - 5f).coerceAtLeast(1f) * 1000
        if (moved && update.step == StepKind.SCAN_BACK) backSince = now
        updateCapture {
            copy(
                step = update.step,
                guidance = update.guidance,
                progress = update.progress,
                overlay = update.overlay,
                running = true,
                accepted = if (moved) previous?.step else accepted,
                backSkipped = if (moved) skipped else backSkipped,
                shutter = if (moved) false else shutter,
                ready = false,
                evaluationCapReached = update.guidance == Guidance.LICENCE_REQUIRED || session.evaluationCapReached,
            )
        }
        if (moved && update.step == StepKind.SCAN_BACK) armShutter(StepKind.SCAN_BACK)
        if (moved) {
            // A side was accepted: the check shows for a moment, then the next step's guidance.
            val id = attempt
            viewModelScope.launch {
                delay(ACCEPTED_MS)
                if (id == attempt) updateCapture { copy(accepted = null) }
            }
        }
    }

    private fun stepTimeoutS(): Float = (engine.state.value as? DigidEngine.State.Ready)?.stepTimeoutS ?: 30f

    /** Changes the camera screen's state; a no-op once the screen has moved on. */
    private fun updateCapture(change: Capture.() -> Capture) = _ui.update { ui ->
        val camera = ui.screen as? Screen.Camera ?: return@update ui
        ui.copy(screen = camera.copy(capture = camera.capture.change()))
    }

    /**
     * On [sessionThread]: the flow is done. Keep the JSON for the backend and close the session;
     * the frame turns green and the check shows for a moment, then the result.
     */
    private fun finishAttempt(session: NidaKycSession, result: KycResult, lastStep: StepKind?) {
        // finishJson() is the same result as JSON text, identical on every platform: send it to
        // your backend as it is. Do not log it: it holds personal data.
        val json = session.finishJson()
        activeSession = null
        session.close()
        // Outcomes only: no field values.
        Log.i(
            TAG,
            "result overall=${result.overall.wire} evaluation=${result.evaluation} " +
                "document=${if (result.drivingLicence != null) "licence" else if (result.card != null) "nida" else "none"} " +
                "checks=${result.checks.joinToString { "${it.id.wire}:${it.status.wire}" }} " +
                "face=${result.face?.verdict?.wire} liveness=${result.liveness?.passed} back=${result.cardBack != null}",
        )
        // TODO(integrator): POST `json` to your backend over your own authenticated connection.
        val step = lastStep?.takeIf { it != StepKind.DONE && it != StepKind.UNKNOWN } ?: StepKind.SCAN_FRONT
        updateCapture { copy(accepted = step, backSkipped = step == StepKind.SCAN_BACK && result.cardBack == null) }
        val id = attempt
        viewModelScope.launch {
            delay(ACCEPTED_MS)
            // Only if the person is still on this attempt's camera screen (they may have left meanwhile).
            _ui.update { if (id == attempt && it.screen is Screen.Camera) it.copy(screen = Screen.Result(result, json, it.settings)) else it }
        }
    }

    /**
     * End the attempt now with the best result so far (`finish()` works at any step). The
     * example offers it in debug builds, to see the result screen without a card.
     */
    fun finishNow() {
        val session = activeSession ?: return
        sessionThread.execute {
            if (activeSession !== session) return@execute
            finishAttempt(session, session.finish(), (_ui.value.screen as? Screen.Camera)?.capture?.step)
        }
    }

    /** The camera's back arrow: close the session, back to the intro. */
    fun cancel() {
        attempt++
        endSession()
        _ui.update { it.copy(screen = Screen.Intro) }
    }

    /** A message screen's action: back to the intro. */
    fun dismissProblem() = _ui.update { it.copy(screen = Screen.Intro) }

    private fun endSession() {
        val session = activeSession ?: return
        // Queued behind any frame in progress, so the session is never closed mid-frame.
        sessionThread.execute {
            if (activeSession === session) activeSession = null
            session.close()
            Log.i(TAG, "session closed")
        }
    }

    override fun onCleared() {
        endSession()
        sessionThread.shutdown()
    }

    companion object {
        /** How long the green frame and the check stay after a capture is accepted. */
        const val ACCEPTED_MS = 750L

        /** Viewfinder checks in a row that must say ready before the photo is taken. */
        const val READY_FRAMES = 3

        /** When the manual shutter appears, if no photo was taken automatically. */
        const val SHUTTER_AFTER_MS = 6_000L

        /** After a photo that was not accepted, a pause before the viewfinder resumes. */
        const val PHOTO_PAUSE_MS = 900L

        /**
         * The month (1–12) after [period] (`YYYY-MM`, the licence's quota month); without one,
         * the month after the current one in East Africa Time, the licence's calendar.
         */
        fun monthAfter(period: String?): Int {
            val month = period?.substringAfter('-', "")?.toIntOrNull()?.takeIf { it in 1..12 }
                ?: (Calendar.getInstance(TimeZone.getTimeZone("GMT+03:00")).get(Calendar.MONTH) + 1)
            return month % 12 + 1
        }

        val Factory = viewModelFactory {
            initializer { KycViewModel((this[APPLICATION_KEY] as ExampleApp).engine) }
        }
    }
}
