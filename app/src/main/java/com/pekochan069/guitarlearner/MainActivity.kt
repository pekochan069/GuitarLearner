package com.pekochan069.guitarlearner

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme

class MainActivity : AppCompatActivity() {
    private val preferences by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private var themeMode by mutableStateOf(ThemeMode.System)

    override fun onCreate(savedInstanceState: Bundle?) {
        themeMode = ThemeMode.read(preferences)
        AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)
        super.onCreate(savedInstanceState)
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
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(Modifier.padding(padding)) {
                        Text(stringResource(R.string.app_name))
                        ThemeMode.entries.forEach { mode ->
                            TextButton(onClick = { changeThemeMode(mode) }) {
                                Text(stringResource(mode.label))
                            }
                        }
                        TextButton(onClick = {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ko"))
                        }) { Text(stringResource(R.string.language_korean)) }
                        TextButton(onClick = {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                        }) { Text(stringResource(R.string.language_english)) }
                    }
                }
            }
        }
    }

    private fun changeThemeMode(mode: ThemeMode) {
        preferences.edit().putString("theme_mode", mode.name).apply()
        themeMode = mode
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }
}
