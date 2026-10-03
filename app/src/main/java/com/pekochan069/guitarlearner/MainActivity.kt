package com.pekochan069.guitarlearner

import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.presentation.contract.FoundationScreen
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent

class MainActivity : AppCompatActivity() {
    private val graph: AppGraph get() = (application as GuitarLearnerApplication).graph

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        graph.appearanceHost.prepareActivityTheme().fold(::reportAppearanceFailure, {})
        super.onCreate(savedInstanceState)
        graph.appearanceHost.refreshPlatformLanguage().fold(::reportAppearanceFailure, {})
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
                CircuitCompositionLocals(graph.circuit) {
                    CircuitContent(FoundationScreen)
                }
            }
        }
    }

    override fun onResume(): Unit {
        super.onResume()
        graph.appearanceHost.refreshPlatformLanguage().fold(::reportAppearanceFailure, {})
    }

    private fun reportAppearanceFailure(failure: AppearanceFailure): Unit {
        Log.e("Appearance", "Native appearance initialization failed: $failure")
    }
}
