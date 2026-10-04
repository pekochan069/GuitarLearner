package com.pekochan069.guitarlearner

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.presentation.contract.FoundationScreen
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.FoundationState
import com.pekochan069.guitarlearner.ui.FoundationUiFactory
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.runtime.ui.ui

class MainActivity : AppCompatActivity() {
    private val graph: AppGraph get() = (application as GuitarLearnerApplication).graph
    private val metronomeLaunchInput = MetronomeLaunchInput()

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        graph.appearanceHost.prepareActivityTheme().fold(::reportAppearanceFailure, {})
        super.onCreate(savedInstanceState)
        metronomeLaunchInput.restore(savedInstanceState, intent)
        setIntent(intent)
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
                    val presenter = remember(graph) { graph.presenterFactory.create() }
                    CircuitContent(FoundationScreen, presenter, ui<FoundationState> { state, modifier ->
                        BackHandler(enabled = state.canNavigateBack) {
                            state.eventSink(FoundationEvent.NavigateBack)
                        }
                        if (metronomeLaunchInput.pending) {
                            SideEffect { metronomeLaunchInput.dispatch(state.eventSink) }
                        }
                        FoundationUiFactory.foundationUi.Content(state, modifier)
                    })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent): Unit {
        super.onNewIntent(intent)
        metronomeLaunchInput.receive(intent)
        setIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle): Unit {
        metronomeLaunchInput.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume(): Unit {
        super.onResume()
        graph.appearanceHost.refreshPlatformLanguage().fold(::reportAppearanceFailure, {})
    }

    private fun reportAppearanceFailure(failure: AppearanceFailure): Unit {
        Log.e("Appearance", "Native appearance initialization failed: $failure")
    }
}
