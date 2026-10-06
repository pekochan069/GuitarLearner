package com.pekochan069.guitarlearner

import android.Manifest
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pekochan069.guitarlearner.adapters.TunerPermissionOutcome
import com.pekochan069.guitarlearner.adapters.TunerVisibility
import com.pekochan069.guitarlearner.adapters.TrainingVisibility
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.TunerFailure
import com.pekochan069.guitarlearner.domain.TunerRecovery
import com.pekochan069.guitarlearner.presentation.contract.FoundationScreen
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.FoundationState
import com.pekochan069.guitarlearner.ui.FoundationUiFactory
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.runtime.ui.ui
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val graph: AppGraph get() = (application as GuitarLearnerApplication).graph
    private val metronomeLaunchInput = PlaybackLaunchInput()
    private val tunerOwner: TunerSessionOwner by lazy {
        ViewModelProvider(this, TunerSessionOwner.Factory(graph.tunerHostFactory))[TunerSessionOwner::class.java]
    }
    private val trainingOwner: TrainingSessionOwner by lazy {
        ViewModelProvider(this, TrainingSessionOwner.Factory(graph.trainingHostFactory))[TrainingSessionOwner::class.java]
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val launched = tunerOwner.launchedPermission
        tunerOwner.launchedPermission = null
        if (launched != null) {
            val outcome = if (granted) TunerPermissionOutcome.Granted else TunerPermissionOutcome.Denied(
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) TunerRecovery.RetryStart
                else TunerRecovery.AppSettings,
            )
            tunerOwner.host.permissionResult(launched, outcome)
        }
        pumpPermissionRequest()
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                tunerOwner.host.visibilityChanged(TunerVisibility.Locked)
                trainingOwner.host.visibilityChanged(TrainingVisibility.Locked)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        graph.appearanceHost.prepareActivityTheme().fold(::reportAppearanceFailure, {})
        super.onCreate(savedInstanceState)
        metronomeLaunchInput.restore(savedInstanceState, intent)
        setIntent(intent)
        graph.appearanceHost.refreshPlatformLanguage().fold(::reportAppearanceFailure, {})
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                tunerOwner.host.permissionRequest.collect { pumpPermissionRequest() }
            }
        }
        setContent {
            val presenterFactory = remember(graph, tunerOwner, trainingOwner) { graph.createPresenterFactory(tunerOwner.host, trainingOwner.host) }
            val circuit = remember(graph, presenterFactory) { graph.createCircuit(presenterFactory) }
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
                CircuitCompositionLocals(circuit) {
                    val presenter = remember(presenterFactory) { presenterFactory.create() }
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
        updateTunerVisibility()
        graph.appearanceHost.refreshPlatformLanguage().fold(::reportAppearanceFailure, {})
    }

    override fun onStart() {
        super.onStart()
        updateTunerVisibility()
    }

    override fun onStop() {
        tunerOwner.host.visibilityChanged(if (isChangingConfigurations) TunerVisibility.ConfigurationContinuation else TunerVisibility.Background)
        trainingOwner.host.visibilityChanged(if (isChangingConfigurations) TrainingVisibility.ConfigurationContinuation else TrainingVisibility.Background)
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        super.onDestroy()
    }

    private fun updateTunerVisibility() {
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive
        tunerOwner.host.visibilityChanged(if (locked) TunerVisibility.Locked else TunerVisibility.Foreground)
        trainingOwner.host.visibilityChanged(if (locked) TrainingVisibility.Locked else TrainingVisibility.Foreground)
    }

    private fun pumpPermissionRequest() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || tunerOwner.launchedPermission != null) return
        val request = tunerOwner.host.permissionRequest.value ?: return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            tunerOwner.host.permissionResult(request.id, TunerPermissionOutcome.Granted)
            return
        }
        tunerOwner.launchedPermission = request.id
        try {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } catch (_: IllegalStateException) {
            tunerOwner.launchedPermission = null
            tunerOwner.host.permissionResult(request.id, TunerPermissionOutcome.Failed(TunerFailure.InputUnavailable))
        }
    }

    private fun reportAppearanceFailure(failure: AppearanceFailure): Unit {
        Log.e("Appearance", "Native appearance initialization failed: $failure")
    }
}
