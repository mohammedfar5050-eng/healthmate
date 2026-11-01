package net.orbitratech.healthmate

import android.app.Application
import net.orbitratech.healthmate.di.AppContainer
import net.orbitratech.healthmate.di.DefaultAppContainer

/** Application class: owns the [AppContainer] for the lifetime of the process. */
class HealthMateApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
