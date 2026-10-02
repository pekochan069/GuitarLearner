package com.pekochan069.guitarlearner

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.pekochan069.guitarlearner.ui.demo.DesignFoundationApp
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme

class MainActivity : AppCompatActivity() {
    private val preferences by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private var themeMode by mutableStateOf(ThemeMode.System)
    private var languageTag by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        themeMode = ThemeMode.read(preferences)
        AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)
        super.onCreate(savedInstanceState)
        languageTag = AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                )
            }
            GuitarLearnerTheme(darkTheme = darkTheme) {
                DesignFoundationApp(
                    themeMode = themeMode,
                    languageTag = languageTag,
                    onThemeChange = { mode ->
                        preferences.edit { putString("theme_mode", mode.name) }
                        themeMode = mode
                        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
                    },
                    onLanguageChange = { tag ->
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                        languageTag = tag
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        languageTag = AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty()
    }
}
