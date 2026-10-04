package com.pekochan069.guitarlearner

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.presentation.contract.FeatureId
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent

internal class MetronomeLaunchInput {
    var pending by mutableStateOf(false)
        private set

    fun restore(savedState: Bundle?, intent: Intent) {
        pending = savedState?.getBoolean(PENDING_OPEN) == true
        if (savedState == null) {
            receive(intent)
        } else if (intent.action == AndroidMetronomeHost.ACTION_OPEN_METRONOME) {
            intent.action = null
        }
    }

    fun receive(intent: Intent) {
        if (intent.action == AndroidMetronomeHost.ACTION_OPEN_METRONOME) {
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) pending = true
            intent.action = null
        }
    }

    fun save(outState: Bundle) {
        if (pending) outState.putBoolean(PENDING_OPEN, true)
    }

    fun dispatch(eventSink: (FoundationEvent) -> Unit) {
        if (!pending) return
        eventSink(FoundationEvent.OpenFeature(FeatureId.Metronome))
        pending = false
    }

    private companion object {
        const val PENDING_OPEN = "pending_metronome_open"
    }
}
