package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.pekochan069.guitarlearner.domain.*
import com.pekochan069.guitarlearner.presentation.contract.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ProgressionPresentation(val state: ProgressionUiState, val eventSink: (ProgressionEvent) -> Unit)

@Composable
internal fun presentProgressions(progressions: Progressions, chords: Chords): ProgressionPresentation {
    val workspace by progressions.current.collectAsState()
    val chordWorkspace by chords.current.collectAsState()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var contextOpen by rememberSaveable { mutableStateOf(false) }
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var stops by rememberSaveable { mutableStateOf(listOf(-1, 3, 2, 0, 1, 0)) }
    var editorName by rememberSaveable { mutableStateOf("") }
    var rawFrets by rememberSaveable { mutableStateOf(List<String?>(6) { null }) }
    var notes by rememberSaveable { mutableStateOf(List<String?>(6) { null }) }
    var octaves by rememberSaveable { mutableStateOf(List<String?>(6) { null }) }
    var capo by rememberSaveable { mutableStateOf<String?>(null) }
    var tuningExpanded by rememberSaveable { mutableStateOf(false) }
    var tempo by rememberSaveable { mutableStateOf<String?>(null) }
    var numerator by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<ProgressionNotice?>(null) }
    val commands = remember { Mutex() }
    val scope = rememberCoroutineScope()
    fun execute(record: Boolean = false, after: () -> Unit = {}, command: () -> ProgressionCommand) {
        if (record && busy) return
        if (record) busy = true
        scope.launch {
            try {
                commands.withLock {
                    progressions.execute(command()).fold(
                        { notice = ProgressionNotice.valueOf(it.name) }, { notice = null; after() })
                }
            } finally { if (record) busy = false }
        }
    }
    fun send(command: ProgressionCommand) { execute { command } }
    fun transport(command: ProgressionCommand) { scope.launch {
        progressions.execute(command).fold({ notice = ProgressionNotice.valueOf(it.name) }, { notice = null })
    } }
    fun copyShape(name: String, shape: ChordShape) {
        editorName = name
        stops = shape.stops.map { when (it) { StringStop.Muted -> -1; StringStop.Open -> 0; is StringStop.Fretted -> it.fret } }
        rawFrets = List(6) { null }
    }
    val content = workspace.draft.content
    val editorShape = ChordShape(stops.map { when (it) { -1 -> StringStop.Muted; 0 -> StringStop.Open; else -> StringStop.Fretted(it) } })
    val editorDraft = ChordTheory.normalize(ChordDraft(editorName, content.context, editorShape))
    val editor = progressionChordUi(editorDraft, chordWorkspace.records, notes, octaves, rawFrets, capo, tuningExpanded)
    val bpmInput = tempo ?: content.timing.bpm.toString()
    val numeratorInput = numerator ?: content.timing.numerator.toString()
    val bpmError = bpmInput.toIntOrNull()?.let { it !in 40..240 } ?: true
    val numeratorError = numeratorInput.toIntOrNull()?.let { it !in 1..16 } ?: true
    val playback = workspace.playback
    val position = when (playback) { is ProgressionPlayback.Playing -> playback.position; is ProgressionPlayback.Paused -> playback.position; else -> null }
    val state = ProgressionUiState(workspace.draft.name, content.steps.mapIndexed { index, step ->
        val chord = step as? ProgressionStep.Chord
        val ui = chord?.let { progressionChordUi(ChordTheory.normalize(ChordDraft(it.name, content.context, it.shape)), emptyList()) }
        ProgressionStepUi(index, chord == null, chord?.name.orEmpty(), ui?.soundingSymbol, ui?.shapeSymbol, ui?.strings.orEmpty(),
            NoteValueUi.valueOf(step.duration.value.name), step.duration.dotted, chord?.tieToNext == true, content.canTie(index))
    }, workspace.selectedIndex, bpmInput, content.timing.bpm, bpmError, numeratorInput, content.timing.numerator, numeratorError,
        BeatUnitUi.valueOf(content.timing.denominator.name), content.metronomeEnabled, content.loop,
        when (playback) { is ProgressionPlayback.Stopped -> ProgressionTransportUi.Stopped; ProgressionPlayback.Preparing -> ProgressionTransportUi.Preparing
            is ProgressionPlayback.Playing -> ProgressionTransportUi.Playing; is ProgressionPlayback.Paused -> ProgressionTransportUi.Paused
            is ProgressionPlayback.Failed -> ProgressionTransportUi.Failed }, position?.stepIndex ?: -1, position?.countInBeat,
        (playback as? ProgressionPlayback.Stopped)?.reason?.let { MetronomeStopUi.valueOf(it.name) },
        position?.let { it.bpm != content.timing.bpm || it.metronomeEnabled != content.metronomeEnabled } == true,
        editor, editorOpen, editingIndex, contextOpen, busy, workspace.persistence == DraftPersistence.Unsynced,
        workspace.readFailure != null, workspace.draft.name.trim().length in 1..80 && content.steps.isNotEmpty() && !busy && workspace.readFailure == null
            && !editor.capoError && editor.strings.none { it.noteError || it.octaveError }
            && !bpmError && !numeratorError,
        workspace.records.map { SavedProgressionUi(it.id, it.name, it.content.steps.size) },
        notice ?: workspace.actionFailure?.let { ProgressionNotice.valueOf(it.name) }
            ?: (playback as? ProgressionPlayback.Failed)?.failure?.let { ProgressionNotice.valueOf(it.name) })
    fun pitch(index: Int) {
        val accepted = content.context.tuning.pitches[index]
        if (parseGuitarPitch(notes[index] ?: accepted.note.symbol, octaves[index] ?: accepted.octave.toString()) == null) return
        execute {
        val current = progressions.current.value.draft.content.context
        val accepted = current.tuning.pitches[index]
        ProgressionCommand.SetContext(current.copy(tuning = current.tuning.withString(index,
            parseGuitarPitch(notes[index] ?: accepted.note.symbol, octaves[index] ?: accepted.octave.toString()) ?: accepted)))
        }
    }
    return ProgressionPresentation(state) { event ->
        when (event) {
            is ProgressionEvent.Select -> send(ProgressionCommand.Select(event.index))
            is ProgressionEvent.OpenEditor -> {
                editingIndex = event.index
                val chord = content.steps.getOrNull(event.index ?: -1) as? ProgressionStep.Chord
                if (chord != null) copyShape(chord.name, chord.shape)
                editorOpen = true
            }
            ProgressionEvent.CloseEditor -> editorOpen = false
            ProgressionEvent.CommitEditor -> if (editor.canSave) execute(record = true, after = { editorOpen = false }) {
                editingIndex?.let { ProgressionCommand.ReplaceChord(it, editorName, editorShape) }
                    ?: ProgressionCommand.Insert(ProgressionStep.Chord(editorName, editorShape))
            }
            ProgressionEvent.CopyCurrentChord -> copyShape(chordWorkspace.draft.name, chordWorkspace.draft.shape)
            is ProgressionEvent.CopyCustomChord -> chordWorkspace.records.firstOrNull { it.id == event.id }?.let { copyShape(it.content.name, it.content.shape) }
            is ProgressionEvent.SetContextOpen -> contextOpen = event.value
            is ProgressionEvent.ChordInput -> when (val input = event.event) {
                is ChordEvent.SetName -> editorName = input.value
                is ChordEvent.SetTuningExpanded -> tuningExpanded = input.value
                is ChordEvent.SetPreset -> {
                    notes = List(6) { null }; octaves = List(6) { null }
                    execute { ProgressionCommand.SetContext(progressions.current.value.draft.content.context.copy(tuning = TuningPreset.valueOf(input.value.name).tuning)) }
                }
                is ChordEvent.SetCapo -> {
                    capo = input.value
                    input.value.toIntOrNull()?.takeIf { it in 0..12 }?.let { value -> execute {
                        ProgressionCommand.SetContext(progressions.current.value.draft.content.context.copy(capo = value))
                    } }
                }
                is ChordEvent.SetNote -> if (input.index in 0..5) { notes = notes.updated(input.index, input.value); pitch(input.index) }
                is ChordEvent.SetOctave -> if (input.index in 0..5) { octaves = octaves.updated(input.index, input.value); pitch(input.index) }
                is ChordEvent.SetStop -> if (input.index in 0..5) {
                    stops = stops.updated(input.index, when (input.value) { ChordStopUi.Muted -> -1; ChordStopUi.Open -> 0; ChordStopUi.Fretted -> maxOf(1, stops[input.index]) })
                    rawFrets = rawFrets.updated(input.index, null)
                }
                is ChordEvent.SetFret -> if (input.index in 0..5) {
                    rawFrets = rawFrets.updated(input.index, input.value)
                    input.value.toIntOrNull()?.takeIf { it in 0..12 }?.let { stops = stops.updated(input.index, it) }
                }
                else -> Unit
            }
            ProgressionEvent.AddRest -> send(ProgressionCommand.Insert(ProgressionStep.Rest()))
            is ProgressionEvent.SetDuration -> send(ProgressionCommand.SetDuration(event.index, NoteDuration(NoteValue.valueOf(event.value.name), event.dotted)))
            is ProgressionEvent.SetTie -> send(ProgressionCommand.SetTie(event.index, event.value))
            is ProgressionEvent.Move -> send(ProgressionCommand.Move(event.index, event.index + event.delta))
            is ProgressionEvent.Remove -> send(ProgressionCommand.Remove(event.index))
            is ProgressionEvent.SetName -> send(ProgressionCommand.SetName(event.value))
            is ProgressionEvent.SetTempo -> { tempo = event.value; event.value.toIntOrNull()?.takeIf { it in 40..240 }?.let { send(ProgressionCommand.SetTempo(it)) } }
            is ProgressionEvent.SetNumerator -> { numerator = event.value; event.value.toIntOrNull()?.takeIf { it in 1..16 }?.let { value -> execute {
                ProgressionCommand.SetSignature(progressions.current.value.draft.content.timing.denominator, value)
            } } }
            is ProgressionEvent.SetDenominator -> execute { ProgressionCommand.SetSignature(BeatUnit.valueOf(event.value.name), progressions.current.value.draft.content.timing.numerator) }
            is ProgressionEvent.SetMetronome -> send(ProgressionCommand.SetMetronome(event.value))
            is ProgressionEvent.SetLoop -> send(ProgressionCommand.SetLoop(event.value))
            is ProgressionEvent.Play -> scope.launch {
                if (event.selected) {
                    val stopped = progressions.execute(ProgressionCommand.Stop).fold(
                        { notice = ProgressionNotice.valueOf(it.name); false }, { true })
                    if (!stopped) return@launch
                }
                progressions.execute(ProgressionCommand.Play(if (event.selected) workspace.selectedIndex else 0))
                    .fold({ notice = ProgressionNotice.valueOf(it.name) }, { notice = null })
            }
            ProgressionEvent.Pause -> transport(ProgressionCommand.Pause)
            ProgressionEvent.Resume -> transport(ProgressionCommand.Resume)
            ProgressionEvent.Stop -> transport(ProgressionCommand.Stop)
            ProgressionEvent.NewDraft -> execute(after = { tempo = null; numerator = null; capo = null; notes = List(6) { null }; octaves = List(6) { null } }) { ProgressionCommand.NewDraft }
            ProgressionEvent.Save -> execute(record = true) { ProgressionCommand.Save }
            is ProgressionEvent.Load -> execute(record = true, after = { tempo = null; numerator = null; capo = null; notes = List(6) { null }; octaves = List(6) { null } }) { ProgressionCommand.Load(event.id) }
            is ProgressionEvent.Delete -> execute(record = true) { ProgressionCommand.Delete(event.id) }
            ProgressionEvent.RetryDraftWrite -> execute(record = true) { ProgressionCommand.RetryDraftWrite }
            ProgressionEvent.RetryStorageRead -> execute(record = true) { ProgressionCommand.RetryStorageRead }
        }
    }
}

private fun <T> List<T>.updated(index: Int, value: T): List<T> = mapIndexed { i, old -> if (i == index) value else old }
internal fun progressionChordUi(draft: ChordDraft, records: List<SavedChord>, notes: List<String?> = List(6) { null },
    octaves: List<String?> = List(6) { null }, frets: List<String?> = List(6) { null }, capo: String? = null,
    expanded: Boolean = false): ChordUiState {
    val normalized = ChordTheory.normalize(draft)
    val analysis = ChordTheory.analyze(draft.context, draft.shape)
    val candidate = ChordTheory.selectedCandidate(normalized)
    val strings = normalized.strings(notes, octaves, frets)
    val capoInput = capo ?: draft.context.capo.toString()
    val capoError = capoInput.toIntOrNull()?.let { it !in 0..12 } ?: true
    val kind = when (analysis) { ChordAnalysis.Empty -> ChordAnalysisUi.Empty; is ChordAnalysis.Note -> ChordAnalysisUi.Note
        is ChordAnalysis.Recognized -> ChordAnalysisUi.Recognized; ChordAnalysis.Unrecognized -> ChordAnalysisUi.Unrecognized }
    return ChordUiState(ChordSection.Edit, expanded, TuningPreset.entries.firstOrNull { it.tuning == draft.context.tuning }?.let { TuningPresetUi.valueOf(it.name) },
        capoInput, draft.context.capo, capoError, strings, draft.name, draft.name.length > 80, null, kind,
        candidate?.let(ChordTheory::symbol), candidate?.let { ChordTheory.symbol(ChordTheory.shapeCandidate(it, draft.context.capo)) },
        strings.mapNotNull { it.note }.joinToString(" · "), emptyList(), PitchClass.entries.map { it.symbol }, 0, ChordQualityUi.Major,
        ChordLookupUi.Idle, null, null, emptyList(), "", "", 0, 0,
        records.map { SavedChordUi(it.id, it.content.name, null, ChordAnalysisUi.Unrecognized, "", "", 0) },
        draft.shape.hasSound && draft.name.length <= 80 && !capoError && strings.none { it.noteError || it.octaveError || it.fretError },
        false, false, false, null)
}
