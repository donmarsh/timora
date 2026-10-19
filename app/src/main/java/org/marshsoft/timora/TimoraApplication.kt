package org.marshsoft.timora

import android.app.Application
import org.marshsoft.timora.data.AppContainer

class TimoraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.seedSamples()
    }
}
