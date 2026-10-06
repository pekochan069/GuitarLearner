package com.pekochan069.guitarlearner

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.adapters.AndroidProgressionsHost
import com.pekochan069.guitarlearner.presentation.contract.FeatureId
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent

internal class PlaybackLaunchInput {
    private var feature = FeatureId.Metronome
    var pending by mutableStateOf(false)
        private set

    fun restore(savedState: Bundle?, intent: Intent) {
        pending = savedState?.getBoolean(PENDING_OPEN) == true
        feature = if (savedState?.getString(PENDING_FEATURE) == FeatureId.Progressions.savedId) FeatureId.Progressions else FeatureId.Metronome
        if (savedState == null) {
            receive(intent)
        } else if (target(intent) != null) {
            intent.action = null
        }
    }

    fun receive(intent: Intent) {
        target(intent)?.let { target ->
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) { feature = target; pending = true }
            intent.action = null
        }
    }

    fun save(outState: Bundle) {
        if (pending) { outState.putBoolean(PENDING_OPEN, true); outState.putString(PENDING_FEATURE, feature.savedId) }
    }

    fun dispatch(eventSink: (FoundationEvent) -> Unit) {
        if (!pending) return
        eventSink(FoundationEvent.OpenFeature(feature))
        pending = false
    }

    private companion object {
        const val PENDING_OPEN = "pending_metronome_open"
        const val PENDING_FEATURE = "pending_playback_feature"
    }

    private fun target(intent: Intent): FeatureId? = when (intent.action) {
        AndroidMetronomeHost.ACTION_OPEN_METRONOME -> FeatureId.Metronome
        AndroidProgressionsHost.ACTION_OPEN_PROGRESSION -> FeatureId.Progressions
        else -> null
    }
}
