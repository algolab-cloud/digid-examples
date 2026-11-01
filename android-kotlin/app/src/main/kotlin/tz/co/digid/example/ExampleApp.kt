package tz.co.digid.example

import android.app.Application

class ExampleApp : Application() {
    /** Created once per process; the models start loading right away, off the main thread. */
    lateinit var engine: DigidEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = DigidEngine(this, BuildConfig.DIGID_LICENCE)
    }
}
