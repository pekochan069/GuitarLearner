package com.pekochan069.guitarlearner.domain

import arrow.core.Either
import java.util.Collections
import kotlinx.coroutines.flow.StateFlow

enum class NoteValue(val ticks: Int, val denominator: Int) {
    Whole(64, 1), Half(32, 2), Quarter(16, 4), Eighth(8, 8), Sixteenth(4, 16), ThirtySecond(2, 32),
}

data class NoteDuration(val value: NoteValue = NoteValue.Quarter, val dotted: Boolean = false) {
    val ticks: Int get() = if (dotted) value.ticks * 3 / 2 else value.ticks
}

sealed interface ProgressionStep {
    val duration: NoteDuration
    data class Chord(val name: String, val shape: ChordShape, override val duration: NoteDuration = NoteDuration(),
        val tieToNext: Boolean = false) : ProgressionStep {
        init { require(shape.hasSound); require(name.length <= 80) }
    }
    data class Rest(override val duration: NoteDuration = NoteDuration()) : ProgressionStep
}

class ProgressionContent(
    val context: GuitarContext = GuitarContext(),
    val timing: MetronomeConfig = MetronomeConfig(),
    val metronomeEnabled: Boolean = true,
    val loop: Boolean = false,
    steps: List<ProgressionStep> = emptyList(),
) {
    val steps: List<ProgressionStep> = Collections.unmodifiableList(steps.toList())
    init { require(this.steps.indices.all { index -> (this.steps[index] as? ProgressionStep.Chord)?.tieToNext != true || canTie(index) }) }
    fun canTie(index: Int): Boolean {
        val chord = steps.getOrNull(index) as? ProgressionStep.Chord ?: return false
        return (steps.getOrNull(index + 1) as? ProgressionStep.Chord)?.shape == chord.shape
    }
    fun copy(context: GuitarContext = this.context, timing: MetronomeConfig = this.timing,
        metronomeEnabled: Boolean = this.metronomeEnabled, loop: Boolean = this.loop,
        steps: List<ProgressionStep> = this.steps): ProgressionContent =
        ProgressionContent(context, timing, metronomeEnabled, loop, steps)
    fun withEditedSteps(steps: List<ProgressionStep>): ProgressionContent = copy(steps = steps.mapIndexed { index, step ->
        if (step is ProgressionStep.Chord && step.tieToNext &&
            (steps.getOrNull(index + 1) as? ProgressionStep.Chord)?.shape != step.shape) step.copy(tieToNext = false) else step
    })
    override fun equals(other: Any?): Boolean = other is ProgressionContent && context == other.context && timing == other.timing &&
        metronomeEnabled == other.metronomeEnabled && loop == other.loop && steps == other.steps
    override fun hashCode(): Int = listOf(context, timing, metronomeEnabled, loop, steps).hashCode()
}

data class ProgressionDraft(val name: String = "", val content: ProgressionContent = ProgressionContent(), val targetId: String? = null)
data class SavedProgression(val id: String, val name: String, val content: ProgressionContent) {
    init { require(id.isNotBlank() && id.length <= 80); require(name == name.trim() && name.length in 1..80); require(content.steps.isNotEmpty()) }
}
data class ProgressionPosition(val stepIndex: Int = -1, val countInBeat: Int? = null, val bpm: Int = 90, val metronomeEnabled: Boolean = true)
sealed interface ProgressionPlayback {
    data class Stopped(val reason: StopReason? = null) : ProgressionPlayback
    data object Preparing : ProgressionPlayback
    data class Playing(val position: ProgressionPosition) : ProgressionPlayback
    data class Paused(val position: ProgressionPosition) : ProgressionPlayback
    data class Failed(val failure: ProgressionFailure) : ProgressionPlayback
}
enum class ProgressionFailure { InvalidInput, InvalidName, EmptyShape, InvalidTie, EmptyProgression, RecordMissing,
    ReadFailed, WriteFailed, FocusDenied, ServiceUnavailable, AudioUnavailable }
data class ProgressionWorkspace(val draft: ProgressionDraft = ProgressionDraft(), val records: List<SavedProgression> = emptyList(),
    val selectedIndex: Int = -1, val playback: ProgressionPlayback = ProgressionPlayback.Stopped(),
    val persistence: DraftPersistence = DraftPersistence.Synced, val readFailure: ProgressionFailure? = null,
    val actionFailure: ProgressionFailure? = null)

sealed interface ProgressionCommand {
    data class Insert(val step: ProgressionStep) : ProgressionCommand
    data class ReplaceChord(val index: Int, val name: String, val shape: ChordShape) : ProgressionCommand
    data class SetDuration(val index: Int, val duration: NoteDuration) : ProgressionCommand
    data class Move(val index: Int, val destination: Int) : ProgressionCommand
    data class Remove(val index: Int) : ProgressionCommand
    data class SetTie(val index: Int, val enabled: Boolean) : ProgressionCommand
    data class Select(val index: Int) : ProgressionCommand
    data class SetContext(val context: GuitarContext) : ProgressionCommand
    data class SetSignature(val denominator: BeatUnit, val numerator: Int) : ProgressionCommand
    data class SetTempo(val bpm: Int) : ProgressionCommand
    data class SetMetronome(val enabled: Boolean) : ProgressionCommand
    data class SetLoop(val enabled: Boolean) : ProgressionCommand
    data class Play(val startIndex: Int = 0) : ProgressionCommand
    data object Pause : ProgressionCommand
    data object Resume : ProgressionCommand
    data object Stop : ProgressionCommand
    data class SetName(val name: String) : ProgressionCommand
    data object NewDraft : ProgressionCommand
    data object Save : ProgressionCommand
    data class Load(val id: String) : ProgressionCommand
    data class Delete(val id: String) : ProgressionCommand
    data object RetryDraftWrite : ProgressionCommand
    data object RetryStorageRead : ProgressionCommand
}
interface Progressions {
    val current: StateFlow<ProgressionWorkspace>
    suspend fun execute(command: ProgressionCommand): Either<ProgressionFailure, Unit>
}
