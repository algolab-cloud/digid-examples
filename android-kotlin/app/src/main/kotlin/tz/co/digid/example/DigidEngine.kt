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
import kotlinx.coroutines.withContext
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

    /**
     * A new session for one verification attempt. Waits for the models if they are still loading.
     * On the free plan each check asks digid for permission before it starts: [NidaKycSession.prepare]
     * fetches it here, off the main thread, so the camera's first frame never waits on the network
     * (it throws what the first push would: `ConnectionRequiredException` with reason `check`
     * offline, `QuotaReachedException` once the month's checks are used up). Paid plans return at once.
     */
    suspend fun newSession(flow: Flow): NidaKycSession {
        val session = digid.await().newSession(flow)
        try {
            withContext(Dispatchers.IO) { session.prepare() }
        } catch (t: Throwable) {
            session.close()
            throw t
        }
        return session
    }

    /**
     * The licence status now: the monthly checks left (free plan) and whether verifying waits
     * until the phone reaches digid. Checked before the camera opens.
     */
    suspend fun licence(): LicenceStatus = digid.await().licence

    /** digid refused to renew the licence (connecting again will not help); null otherwise. */
    suspend fun renewalRefusal(): String? = digid.await().lastRenewalRefusal?.code

    /**
     * The phone is online: send the usage counts and renew the licence if it needs it. Network
     * work, so it runs off the main thread. The SDK also does this by itself in the background;
     * the "connect to the internet" screen calls it from its Try again button.
     */
    suspend fun online() {
        val engine = digid.await()
        withContext(Dispatchers.IO) { engine.online() }
    }

    companion object {
        const val TAG = "DigidExample"
    }
}
