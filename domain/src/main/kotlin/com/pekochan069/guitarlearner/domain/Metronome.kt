package com.pekochan069.guitarlearner.domain

import arrow.core.Either
import java.util.Collections
import kotlinx.coroutines.flow.StateFlow

enum class BeatUnit(val denominator: Int) {
    Half(2), Quarter(4), Eighth(8), Sixteenth(16),
}

enum class BeatAccent { Accent, Normal, Mute }

class MetronomeConfig(
    val bpm: Int = 90,
    val denominator: BeatUnit = BeatUnit.Quarter,
    beats: List<BeatAccent> = listOf(BeatAccent.Accent, BeatAccent.Normal, BeatAccent.Normal, BeatAccent.Normal),
) {
    val beats: List<BeatAccent> = Collections.unmodifiableList(beats.toList())
    val numerator: Int get() = beats.size

    init {
        require(bpm in 40..240)
        require(this.beats.size in 1..16)
    }

    fun copy(
        bpm: Int = this.bpm,
        denominator: BeatUnit = this.denominator,
        beats: List<BeatAccent> = this.beats,
    ): MetronomeConfig = MetronomeConfig(bpm, denominator, beats)

    override fun equals(other: Any?): Boolean = other is MetronomeConfig &&
        bpm == other.bpm && denominator == other.denominator && beats == other.beats

    override fun hashCode(): Int = 31 * (31 * bpm + denominator.hashCode()) + beats.hashCode()

    override fun toString(): String = "MetronomeConfig(bpm=$bpm, denominator=$denominator, beats=$beats)"
}

data class MetronomePreset(val name: String, val config: MetronomeConfig)

enum class StopReason { User, FocusLoss, OutputDisconnected, ServiceEnded }

sealed interface MetronomeFailure {
    data object InvalidConfiguration : MetronomeFailure
    data object InvalidName : MetronomeFailure
    data object PresetExists : MetronomeFailure
    data object PresetMissing : MetronomeFailure
    data object ReadFailed : MetronomeFailure
    data object WriteFailed : MetronomeFailure
    data object FocusDenied : MetronomeFailure
    data object ServiceUnavailable : MetronomeFailure
    data object AudioUnavailable : MetronomeFailure
}

sealed interface PlaybackState {
    data class Stopped(val reason: StopReason? = null) : PlaybackState
    data object Preparing : PlaybackState
    data class Playing(val config: MetronomeConfig, val beatIndex: Int) : PlaybackState
    data class Failed(val failure: MetronomeFailure) : PlaybackState
}

data class MetronomeSnapshot(
    val selected: MetronomeConfig = MetronomeConfig(),
    val playback: PlaybackState = PlaybackState.Stopped(),
    val presets: List<MetronomePreset> = emptyList(),
    val readFailure: MetronomeFailure? = null,
)

sealed interface MetronomeCommand {
    data object Start : MetronomeCommand
    data object Stop : MetronomeCommand
    data class SetTempo(val bpm: Int) : MetronomeCommand
    data class SetPattern(val denominator: BeatUnit, val beats: List<BeatAccent>) : MetronomeCommand
    data class SavePreset(val name: String, val overwrite: Boolean = false) : MetronomeCommand
    data class LoadPreset(val name: String) : MetronomeCommand
    data class DeletePreset(val name: String) : MetronomeCommand
}

interface Metronome {
    val current: StateFlow<MetronomeSnapshot>
    suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit>
}
