package com.pekochan069.guitarlearner.adapters

import android.media.AudioManager
import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.BeatUnit
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.ScheduledBeat
import kotlinx.coroutines.CoroutineScope

interface MetronomeOutput {
    val routedDeviceId: Int?
    val diagnostics: MetronomeAudioDiagnostics
    fun start(scope: CoroutineScope)
    fun stop(): Boolean
    fun setTempo(bpm: Int)
    fun setPattern(denominator: BeatUnit, beats: List<BeatAccent>)
    fun load(config: MetronomeConfig)
}

fun interface MetronomeOutputFactory {
    fun create(
        manager: AudioManager,
        config: MetronomeConfig,
        onBeat: (ScheduledBeat) -> Unit,
        onOutputDisconnected: () -> Unit,
        onFailure: () -> Unit,
    ): MetronomeOutput
}
