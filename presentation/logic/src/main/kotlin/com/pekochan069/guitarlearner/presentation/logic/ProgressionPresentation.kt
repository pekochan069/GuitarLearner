package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.pekochan069.guitarlearner.domain.*
import com.pekochan069.guitarlearner.presentation.contract.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class ProgressionPresentation(val state: ProgressionUiState, val eventSink: (ProgressionEvent) -> Unit)

@Composable
internal fun presentProgressions(progressions: Progressions, chords: Chords): ProgressionPresentation {
    val workspace by progressions.current.collectAsState()
    val chordWorkspace by chords.current.collectAsState()
    var sheetName by rememberSaveable { mutableStateOf(ProgressionSheetUi.None.name) }
    var sheetGeneration by remember { mutableIntStateOf(0) }
    var sourceName by rememberSaveable { mutableStateOf(ProgressionChordSourceUi.Named.name) }
    var root by rememberSaveable { mutableStateOf(0) }
    var qualityName by rememberSaveable { mutableStateOf(ChordQualityUi.Major.name) }
    var lookupGeneration by rememberSaveable { mutableIntStateOf(0) }
    var lookup by remember { mutableStateOf<ChordLookup>(ChordLookup.Idle) }
    var lookupContext by remember { mutableStateOf<GuitarContext?>(null) }
    var replacementNew by rememberSaveable { mutableStateOf(false) }
    var replacementId by rememberSaveable { mutableStateOf<String?>(null) }
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
    var removedStep by remember { mutableStateOf<ProgressionStepUi?>(null) }
    var removalGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(removalGeneration) {
        if (removedStep != null) { delay(4_000); removedStep = null }
    }
    val commands = remember { Mutex() }
    val scope = rememberCoroutineScope()
    fun execute(record: Boolean = false, after: () -> Unit = {}, validate: () -> ProgressionNotice? = { null }, command: () -> ProgressionCommand) {
        if (record && busy) return
        if (record) busy = true
        scope.launch {
            try {
                commands.withLock {
                    val rejected = validate()
                    if (rejected != null) notice = rejected
                    else progressions.execute(command()).fold(
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
        sourceName = ProgressionChordSourceUi.Manual.name
    }
    fun resetContextInputs() {
        tempo = null; numerator = null; capo = null
        notes = List(6) { null }; octaves = List(6) { null }
    }
    val content = workspace.draft.content
    val sheet = ProgressionSheetUi.valueOf(sheetName)
    val source = progressionChordSource(sourceName)
    val quality = ChordQualityUi.valueOf(qualityName)
    val query = if (sheet == ProgressionSheetUi.Chord && source == ProgressionChordSourceUi.Named)
        ChordQuery(content.context.copy(capo = 0), ChordIdentity(PitchClass.entries[root], ChordQuality.valueOf(quality.name))) else null
    LaunchedEffect(query, content.context, lookupGeneration) {
        if (query != null) {
            lookupContext = null
            lookup = ChordLookup.Searching(query)
            val context = content.context
            val shapes = withContext(Dispatchers.Default) { ChordTheory.representatives(query) }
            lookupContext = context
            lookup = ChordLookup.Ready(query, shapes)
        }
    }
    fun closeSheet(request: Int = sheetGeneration) {
        if (request != sheetGeneration) return
        sheetGeneration++
        lookup = ChordLookup.Idle; lookupContext = null
        sheetName = ProgressionSheetUi.None.name
        replacementNew = false; replacementId = null
    }
    fun openSheet(value: ProgressionSheetUi) {
        sheetGeneration++
        sheetName = value.name
        replacementNew = false; replacementId = null
    }
    val ready = (lookup as? ChordLookup.Ready)?.takeIf { it.query == query && lookupContext == content.context }
    val namedShape = ready?.shapes?.getOrNull(ready.selectedIndex)
    val manualShape = stops.chordShape()
    val editorShape = if (source == ProgressionChordSourceUi.Named && sheet == ProgressionSheetUi.Chord) namedShape ?: ChordShape() else manualShape
    val selectedIdentity = query?.identity?.let { it.copy(root = it.root.transpose(content.context.capo)) }
    val editorDraft = ChordTheory.normalize(ChordDraft(editorName, content.context, editorShape, selectedIdentity))
    val candidate = ChordTheory.selectedCandidate(editorDraft)
    val editor = progressionChordUi(editorDraft, chordWorkspace.records, notes, octaves,
        if (source == ProgressionChordSourceUi.Named) List(6) { null } else rawFrets, capo, tuningExpanded).copy(
        root = root, quality = quality,
        lookup = when { query == null -> ChordLookupUi.Idle; ready == null -> ChordLookupUi.Searching
            ready.shapes.isEmpty() -> ChordLookupUi.NoShapes; else -> ChordLookupUi.Ready },
        lookupSymbol = candidate?.let(ChordTheory::symbol),
        lookupShapeSymbol = query?.identity?.let { it.root.symbol + it.quality.symbol },
        lookupStrings = if (namedShape == null) emptyList() else editorDraft.strings(),
        lookupNotes = editorDraft.strings().mapNotNull { it.note }.distinct().joinToString(" · "),
        lookupOmitted = candidate?.omitted?.joinToString(", ") { it.symbol }.orEmpty(),
        representativeIndex = ready?.selectedIndex ?: 0, representativeCount = ready?.shapes?.size ?: 0)
    val bpmInput = tempo ?: content.timing.bpm.toString()
    val numeratorInput = numerator ?: content.timing.numerator.toString()
    val bpmError = bpmInput.toIntOrNull()?.let { it !in 40..240 } ?: true
    val numeratorError = numeratorInput.toIntOrNull()?.let { it !in 1..16 } ?: true
    val invalidSettings = bpmError || numeratorError || editor.capoError || editor.strings.any { it.noteError || it.octaveError }
    val saved = workspace.records.firstOrNull { it.id == workspace.draft.targetId }
    val hasUnsavedChanges = invalidSettings || workspace.persistence == DraftPersistence.Unsynced ||
        if (saved != null) saved.content != content || saved.name != workspace.draft.name
        else content.steps.isNotEmpty() || workspace.draft.name.isNotBlank()
    val replacement = when {
        replacementNew -> ProgressionReplacementUi.NewDraft
        replacementId != null -> workspace.records.firstOrNull { it.id == replacementId }?.let { ProgressionReplacementUi.Load(it.id, it.name) }
        else -> null
    }
    fun replaceDraft(command: ProgressionCommand) {
        closeSheet()
        resetContextInputs()
        editingIndex = null
        execute(record = true) { command }
    }
    val playback = workspace.playback
    val position = when (playback) { is ProgressionPlayback.Playing -> playback.position; is ProgressionPlayback.Paused -> playback.position; else -> null }
    val state = ProgressionUiState(workspace.draft.name, content.steps.mapIndexed { index, step -> step.toUi(index, content) },
        workspace.selectedIndex, bpmInput, content.timing.bpm, bpmError, numeratorInput, content.timing.numerator, numeratorError,
        BeatUnitUi.valueOf(content.timing.denominator.name), content.metronomeEnabled, content.loop,
        when (playback) { is ProgressionPlayback.Stopped -> ProgressionTransportUi.Stopped; ProgressionPlayback.Preparing -> ProgressionTransportUi.Preparing
            is ProgressionPlayback.Playing -> ProgressionTransportUi.Playing; is ProgressionPlayback.Paused -> ProgressionTransportUi.Paused
            is ProgressionPlayback.Failed -> ProgressionTransportUi.Failed }, position?.stepIndex ?: -1, position?.countInBeat,
        (playback as? ProgressionPlayback.Stopped)?.reason?.let { MetronomeStopUi.valueOf(it.name) },
        position?.let { it.bpm != content.timing.bpm || it.metronomeEnabled != content.metronomeEnabled } == true,
        editor, sheet, editingIndex, source, replacement, hasUnsavedChanges, invalidSettings,
        content.steps.isNotEmpty() && !invalidSettings && !busy && workspace.readFailure == null,
        busy, workspace.persistence == DraftPersistence.Unsynced,
        workspace.readFailure != null, workspace.draft.name.trim().length in 1..80 && content.steps.isNotEmpty() && !busy && workspace.readFailure == null
            && !invalidSettings,
        workspace.records.map { SavedProgressionUi(it.id, it.name, it.content.steps.size) },
        notice ?: workspace.actionFailure?.let { ProgressionNotice.valueOf(it.name) }
            ?: (playback as? ProgressionPlayback.Failed)?.failure?.let { ProgressionNotice.valueOf(it.name) }, removedStep)
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
            is ProgressionEvent.OpenStep -> if (event.index in content.steps.indices) {
                val request = ++sheetGeneration
                execute(after = { if (sheetGeneration == request) sheetName = ProgressionSheetUi.Step.name }) { ProgressionCommand.Select(event.index) }
            }
            is ProgressionEvent.OpenSheet -> openSheet(event.value)
            is ProgressionEvent.OpenEditor -> {
                val index = event.index
                if (index != null && content.steps.getOrNull(index) !is ProgressionStep.Chord) return@ProgressionPresentation
                if (index != null) transport(ProgressionCommand.Stop)
                editingIndex = index
                val chord = content.steps.getOrNull(index ?: -1) as? ProgressionStep.Chord
                if (chord != null) copyShape(chord.name, chord.shape)
                else { editorName = ""; root = 0; qualityName = ChordQualityUi.Major.name; sourceName = ProgressionChordSourceUi.Named.name }
                lookupGeneration++
                openSheet(ProgressionSheetUi.Chord)
            }
            ProgressionEvent.CloseSheet -> closeSheet()
            is ProgressionEvent.SetChordSource -> if (sourceName != event.value.name) {
                lookup = ChordLookup.Idle; lookupContext = null
                sourceName = event.value.name
                if (event.value == ProgressionChordSourceUi.Named && editingIndex == null) editorName = ""
            }
            ProgressionEvent.CommitEditor -> {
                val currentContext = progressions.current.value.draft.content.context
                val currentReady = (lookup as? ChordLookup.Ready)?.takeIf {
                    it.query == ChordQuery(currentContext.copy(capo = 0), ChordIdentity(PitchClass.entries[root], ChordQuality.valueOf(qualityName))) &&
                        lookupContext == currentContext
                }
                val accepted = when (progressionChordSource(sourceName)) {
                    ProgressionChordSourceUi.Named -> currentReady?.shapes?.getOrNull(currentReady.selectedIndex)
                    ProgressionChordSourceUi.Manual -> stops.chordShape()
                }
                if (sheetName == ProgressionSheetUi.Chord.name && accepted != null && editor.canSave && !invalidSettings) {
                    val request = sheetGeneration
                    val named = sourceName == ProgressionChordSourceUi.Named.name
                    val index = editingIndex
                    val name = editorName
                    execute(record = true, after = { closeSheet(request) }, validate = {
                        if (named && (currentReady?.query?.context != progressions.current.value.draft.content.context.copy(capo = 0) ||
                            currentContext != progressions.current.value.draft.content.context)) ProgressionNotice.ShapeChanged else null
                    }) {
                        index?.let { ProgressionCommand.ReplaceChord(it, name, accepted) }
                            ?: ProgressionCommand.Insert(ProgressionStep.Chord(name, accepted))
                    }
                }
            }
            ProgressionEvent.CopyCurrentChord -> copyShape(chordWorkspace.draft.name, chordWorkspace.draft.shape)
            is ProgressionEvent.CopyCustomChord -> chordWorkspace.records.firstOrNull { it.id == event.id }?.let { copyShape(it.content.name, it.content.shape) }
            is ProgressionEvent.ChordInput -> when (val input = event.event) {
                is ChordEvent.SetRoot -> if (input.value in PitchClass.entries.indices) root = input.value
                is ChordEvent.SetQuality -> qualityName = input.value.name
                is ChordEvent.SelectRepresentative -> if (ready != null && input.index in ready.shapes.indices) lookup = ready.copy(selectedIndex = input.index)
                ChordEvent.CopyRepresentative -> namedShape?.let { copyShape("", it) }
                ChordEvent.Search -> lookupGeneration++
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
            is ProgressionEvent.Remove -> {
                val request = sheetGeneration
                var removed: ProgressionStepUi? = null
                execute(after = { closeSheet(request); removedStep = removed; removalGeneration++ }) {
                    val current = progressions.current.value.draft.content
                    removed = current.steps.getOrNull(event.index)?.toUi(event.index, current)
                    ProgressionCommand.Remove(event.index)
                }
            }
            ProgressionEvent.DismissRemoval -> removedStep = null
            is ProgressionEvent.SetName -> send(ProgressionCommand.SetName(event.value))
            is ProgressionEvent.SetTempo -> { tempo = event.value; event.value.toIntOrNull()?.takeIf { it in 40..240 }?.let { send(ProgressionCommand.SetTempo(it)) } }
            is ProgressionEvent.SetNumerator -> { numerator = event.value; event.value.toIntOrNull()?.takeIf { it in 1..16 }?.let { value -> execute {
                ProgressionCommand.SetSignature(progressions.current.value.draft.content.timing.denominator, value)
            } } }
            is ProgressionEvent.SetDenominator -> execute { ProgressionCommand.SetSignature(BeatUnit.valueOf(event.value.name), progressions.current.value.draft.content.timing.numerator) }
            is ProgressionEvent.SetMetronome -> send(ProgressionCommand.SetMetronome(event.value))
            is ProgressionEvent.SetLoop -> send(ProgressionCommand.SetLoop(event.value))
            is ProgressionEvent.Play -> if (state.canPlay) {
                val request = sheetGeneration
                scope.launch {
                if (event.selected) {
                    val stopped = progressions.execute(ProgressionCommand.Stop).fold(
                        { notice = ProgressionNotice.valueOf(it.name); false }, { true })
                    if (!stopped) return@launch
                }
                progressions.execute(ProgressionCommand.Play(if (event.selected) workspace.selectedIndex else 0))
                    .fold({ notice = ProgressionNotice.valueOf(it.name) }, {
                        notice = null
                        if (event.selected) closeSheet(request)
                    })
                }
            }
            ProgressionEvent.Pause -> transport(ProgressionCommand.Pause)
            ProgressionEvent.Resume -> if (state.canPlay) transport(ProgressionCommand.Resume)
            ProgressionEvent.Stop -> transport(ProgressionCommand.Stop)
            ProgressionEvent.NewDraft -> if (!busy && !state.readFailed) {
                if (hasUnsavedChanges) { sheetGeneration++; replacementNew = true; replacementId = null }
                else replaceDraft(ProgressionCommand.NewDraft)
            }
            ProgressionEvent.ConfirmReplacement -> if (!busy) {
                val id = replacementId
                when {
                    replacementNew -> replaceDraft(ProgressionCommand.NewDraft)
                    id != null -> replaceDraft(ProgressionCommand.Load(id))
                }
            }
            ProgressionEvent.CancelReplacement -> { replacementNew = false; replacementId = null }
            ProgressionEvent.Save -> if (state.canSave) {
                val request = sheetGeneration
                execute(record = true, after = { closeSheet(request) }) { ProgressionCommand.Save }
            }
            is ProgressionEvent.Load -> if (!busy && !state.readFailed) {
                val record = workspace.records.firstOrNull { it.id == event.id }
                if (record == null) notice = ProgressionNotice.RecordMissing
                else if (hasUnsavedChanges) { sheetGeneration++; replacementNew = false; replacementId = record.id }
                else replaceDraft(ProgressionCommand.Load(record.id))
            }
            is ProgressionEvent.Delete -> execute(record = true) { ProgressionCommand.Delete(event.id) }
            ProgressionEvent.RetryDraftWrite -> execute(record = true) { ProgressionCommand.RetryDraftWrite }
            ProgressionEvent.RetryStorageRead -> execute(record = true) { ProgressionCommand.RetryStorageRead }
        }
    }
}

private fun <T> List<T>.updated(index: Int, value: T): List<T> = mapIndexed { i, old -> if (i == index) value else old }
private fun ProgressionStep.toUi(index: Int, content: ProgressionContent): ProgressionStepUi {
    val chord = this as? ProgressionStep.Chord
    val ui = chord?.let { progressionChordUi(ChordTheory.normalize(ChordDraft(it.name, content.context, it.shape)), emptyList()) }
    return ProgressionStepUi(index, chord == null, chord?.name.orEmpty(), ui?.soundingSymbol, ui?.shapeSymbol, ui?.strings.orEmpty(),
        NoteValueUi.valueOf(duration.value.name), duration.dotted, chord?.tieToNext == true, content.canTie(index))
}
private fun progressionChordSource(name: String): ProgressionChordSourceUi =
    if (name == "Saved") ProgressionChordSourceUi.Manual else ProgressionChordSourceUi.valueOf(name)
private fun List<Int>.chordShape(): ChordShape = ChordShape(map { when (it) {
    -1 -> StringStop.Muted; 0 -> StringStop.Open; else -> StringStop.Fretted(it)
} })
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
