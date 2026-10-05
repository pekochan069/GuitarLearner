package com.pekochan069.guitarlearner.adapters

import android.media.AudioManager
import com.pekochan069.guitarlearner.domain.ProgressionContent
import com.pekochan069.guitarlearner.domain.ProgressionPosition
import kotlinx.coroutines.CoroutineScope

interface ProgressionOutput {
    val routedDeviceId: Int?
    val diagnostics: MetronomeAudioDiagnostics
    fun start(scope: CoroutineScope)
    suspend fun pause(): ProgressionPosition
    suspend fun resume()
    fun stop()
    fun setTempo(bpm: Int)
    fun setMetronome(enabled: Boolean)
}
fun interface ProgressionOutputFactory {
    fun create(manager: AudioManager, content: ProgressionContent, startIndex: Int,
        onPosition: (ProgressionPosition) -> Unit, onComplete: () -> Unit,
        onOutputDisconnected: () -> Unit, onFailure: () -> Unit): ProgressionOutput
}
