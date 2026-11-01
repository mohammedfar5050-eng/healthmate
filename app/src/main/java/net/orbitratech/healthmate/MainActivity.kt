package net.orbitratech.healthmate

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.orbitratech.healthmate.feature.settings.data.ThemeMode
import net.orbitratech.healthmate.navigation.HealthMateApp

/** Single activity hosting the Compose UI (navigation is handled in Compose). */
class MainActivity : ComponentActivity() {

    private var appliedDark: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HealthMateApplication).container
        setContent {
            HealthMateApp(container = container, onThemeModeChange = ::applySystemBarStyle)
        }
    }

    /** Keeps status/navigation bar icons readable when the user overrides the theme. */
    private fun applySystemBarStyle(mode: ThemeMode) {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = when (mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        if (dark == appliedDark) return
        appliedDark = dark
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
