package tz.co.digid.example

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import tz.co.digid.sdk.Digid
import tz.co.digid.sdk.Flow
import tz.co.digid.sdk.LicenceStatus
import tz.co.digid.sdk.NidaKycSession

/**
 * The one [Digid] engine of the process. Loading the models takes a second or more, so it
 * starts on a background thread as soon as the app starts and the screens observe [state].
 */
class DigidEngine(context: Context, private val licenceToken: String) {

    sealed interface State {
        data object Loading : State
        /** [stepTimeoutS]: how long a step runs before the engine gives up on it (the back of the card is then skipped). */
        data class Ready(val licence: LicenceStatus, val hasFaceModels: Boolean, val stepTimeoutS: Float) : State
        data class Failed(val message: String) : State
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val digid: Deferred<Digid> = scope.async {
        val t0 = SystemClock.elapsedRealtime()
        try {
            val engine = Digid.create(appContext)
            // applyLicence never throws: a bad token leaves the engine in evaluation mode, with a reason.
            val licence = if (licenceToken.isBlank()) engine.licence else engine.applyLicence(licenceToken)
            Log.i(
                TAG,
                "engine ${Digid.version} loaded in ${SystemClock.elapsedRealtime() - t0} ms; " +
                    "licence=${licence.state.wire} reason=${licence.reason} faceModels=${engine.hasFaceModels}",
            )
            // The engine's settings are a JSON text; only the step timeout matters to the screens.
            val stepTimeoutS = runCatching { JSONObject(engine.config).optDouble("step_timeout_s", 30.0).toFloat() }.getOrDefault(30f)
            _state.value = State.Ready(licence, engine.hasFaceModels, stepTimeoutS)
            engine
        } catch (t: Throwable) {
            Log.e(TAG, "engine failed to load", t)
            _state.value = State.Failed(t.message ?: t.javaClass.simpleName)
            throw t
        }
    }

    /** A new session for one verification attempt. Waits for the models if they are still loading. */
    suspend fun newSession(flow: Flow): NidaKycSession = digid.await().newSession(flow)

    companion object {
        const val TAG = "DigidExample"
    }
}
