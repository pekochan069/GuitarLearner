package com.pekochan069.guitarlearner.presentation.contract

enum class NoteValueUi(val denominator: Int) { Whole(1), Half(2), Quarter(4), Eighth(8), Sixteenth(16), ThirtySecond(32) }
enum class ProgressionNotice { InvalidInput, InvalidName, EmptyShape, InvalidTie, EmptyProgression, RecordMissing,
    ReadFailed, WriteFailed, FocusDenied, ServiceUnavailable, AudioUnavailable, ShapeChanged }
enum class ProgressionTransportUi { Stopped, Preparing, Playing, Paused, Failed }
enum class ProgressionSheetUi { None, Step, Chord, Settings, Save, Collection }
enum class ProgressionChordSourceUi { Named, Saved, Manual }
sealed interface ProgressionReplacementUi {
    data object NewDraft : ProgressionReplacementUi
    data class Load(val id: String, val name: String) : ProgressionReplacementUi
}
data class ProgressionStepUi(val index: Int, val rest: Boolean, val name: String, val sounding: String?, val shape: String?,
    val strings: List<ChordStringUi>, val duration: NoteValueUi, val dotted: Boolean, val tied: Boolean, val canTie: Boolean)
data class SavedProgressionUi(val id: String, val name: String, val stepCount: Int)
data class ProgressionUiState(val name: String, val steps: List<ProgressionStepUi>, val selectedIndex: Int,
    val bpmInput: String, val bpm: Int, val bpmError: Boolean, val numeratorInput: String, val numerator: Int,
    val numeratorError: Boolean, val denominator: BeatUnitUi, val metronome: Boolean, val loop: Boolean,
    val transport: ProgressionTransportUi, val playingIndex: Int, val countInBeat: Int?, val stopReason: MetronomeStopUi?,
    val pendingChange: Boolean, val editor: ChordUiState, val sheet: ProgressionSheetUi, val editingIndex: Int?,
    val chordSource: ProgressionChordSourceUi, val replacement: ProgressionReplacementUi?, val hasUnsavedChanges: Boolean,
    val invalidSettings: Boolean, val canPlay: Boolean, val busy: Boolean, val unsynced: Boolean, val readFailed: Boolean,
    val canSave: Boolean, val records: List<SavedProgressionUi>, val notice: ProgressionNotice?)
sealed interface ProgressionEvent {
    data class Select(val index: Int) : ProgressionEvent
    data class OpenStep(val index: Int) : ProgressionEvent
    data class OpenSheet(val value: ProgressionSheetUi) : ProgressionEvent
    data class OpenEditor(val index: Int? = null) : ProgressionEvent
    data object CloseSheet : ProgressionEvent
    data class SetChordSource(val value: ProgressionChordSourceUi) : ProgressionEvent
    data object CommitEditor : ProgressionEvent
    data object CopyCurrentChord : ProgressionEvent
    data class CopyCustomChord(val id: String) : ProgressionEvent
    data class ChordInput(val event: ChordEvent) : ProgressionEvent
    data object AddRest : ProgressionEvent
    data class SetDuration(val index: Int, val value: NoteValueUi, val dotted: Boolean) : ProgressionEvent
    data class SetTie(val index: Int, val value: Boolean) : ProgressionEvent
    data class Move(val index: Int, val delta: Int) : ProgressionEvent
    data class Remove(val index: Int) : ProgressionEvent
    data class SetName(val value: String) : ProgressionEvent
    data class SetTempo(val value: String) : ProgressionEvent
    data class SetNumerator(val value: String) : ProgressionEvent
    data class SetDenominator(val value: BeatUnitUi) : ProgressionEvent
    data class SetMetronome(val value: Boolean) : ProgressionEvent
    data class SetLoop(val value: Boolean) : ProgressionEvent
    data class Play(val selected: Boolean = false) : ProgressionEvent
    data object Pause : ProgressionEvent
    data object Resume : ProgressionEvent
    data object Stop : ProgressionEvent
    data object NewDraft : ProgressionEvent
    data object ConfirmReplacement : ProgressionEvent
    data object CancelReplacement : ProgressionEvent
    data object Save : ProgressionEvent
    data class Load(val id: String) : ProgressionEvent
    data class Delete(val id: String) : ProgressionEvent
    data object RetryDraftWrite : ProgressionEvent
    data object RetryStorageRead : ProgressionEvent
}
