package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pekochan069.guitarlearner.domain.ChordAnalysis
import com.pekochan069.guitarlearner.domain.ChordCommand
import com.pekochan069.guitarlearner.domain.ChordDraft
import com.pekochan069.guitarlearner.domain.ChordFailure
import com.pekochan069.guitarlearner.domain.ChordIdentity
import com.pekochan069.guitarlearner.domain.ChordLookup
import com.pekochan069.guitarlearner.domain.ChordQuality
import com.pekochan069.guitarlearner.domain.ChordTheory
import com.pekochan069.guitarlearner.domain.Chords
import com.pekochan069.guitarlearner.domain.DraftPersistence
import com.pekochan069.guitarlearner.domain.GuitarPitch
import com.pekochan069.guitarlearner.domain.PitchClass
import com.pekochan069.guitarlearner.domain.StringStop
import com.pekochan069.guitarlearner.domain.TuningPreset
import com.pekochan069.guitarlearner.presentation.contract.ChordAnalysisUi
import com.pekochan069.guitarlearner.presentation.contract.ChordCandidateUi
import com.pekochan069.guitarlearner.presentation.contract.ChordEvent
import com.pekochan069.guitarlearner.presentation.contract.ChordLookupUi
import com.pekochan069.guitarlearner.presentation.contract.ChordNotice
import com.pekochan069.guitarlearner.presentation.contract.ChordQualityUi
import com.pekochan069.guitarlearner.presentation.contract.ChordSection
import com.pekochan069.guitarlearner.presentation.contract.ChordStopUi
import com.pekochan069.guitarlearner.presentation.contract.ChordStringUi
import com.pekochan069.guitarlearner.presentation.contract.ChordUiState
import com.pekochan069.guitarlearner.presentation.contract.SavedChordUi
import com.pekochan069.guitarlearner.presentation.contract.TuningPresetUi
import kotlinx.coroutines.launch

internal data class ChordPresentation(val state: ChordUiState, val eventSink: (ChordEvent) -> Unit)

@Composable
internal fun presentChords(chords: Chords): ChordPresentation {
    val workspace by chords.current.collectAsState()
    var section by rememberSaveable { mutableStateOf(ChordSection.Lookup.name) }
    var tuningExpanded by rememberSaveable { mutableStateOf(false) }
    var root by rememberSaveable { mutableStateOf(0) }
    var quality by rememberSaveable { mutableStateOf(ChordQualityUi.Major.name) }
    val inputsSaver = remember(chords) { chordInputsSaver(chords.current.value.draft) }
    var inputs by rememberSaveable(stateSaver = inputsSaver) { mutableStateOf(ChordInputs()) }
    var notice by remember { mutableStateOf<ChordNotice?>(null) }
    var busy by remember { mutableStateOf(false) }
    var commandGeneration by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    fun execute(command: ChordCommand, recordOperation: Boolean = false) {
        if (recordOperation && busy) return
        if (recordOperation) busy = true
        val generation = ++commandGeneration
        notice = null
        scope.launch {
            try {
                chords.execute(command).fold(
                    { if (generation == commandGeneration) notice = it.toNotice() },
                    { if (generation == commandGeneration) notice = null },
                )
            } finally {
                if (recordOperation) busy = false
            }
        }
    }

    fun resetInputs() {
        inputs = ChordInputs()
    }

    fun setPitch(index: Int) {
        val pitch = chords.current.value.draft.context.tuning.pitches[index]
        parseGuitarPitch(inputs.notes[index] ?: pitch.note.symbol, inputs.octaves[index] ?: pitch.octave.toString())?.let {
            execute(ChordCommand.SetStringPitch(index, it))
        }
    }

    fun search() { execute(ChordCommand.Search(ChordIdentity(PitchClass.entries[root], ChordQuality.valueOf(quality)))) }

    val draft = workspace.draft
    val analysis = workspace.analysis
    val selected = (analysis as? ChordAnalysis.Recognized)?.candidates?.firstOrNull { it.identity == draft.selected }
    val capoInput = inputs.capo ?: draft.context.capo.toString()
    val capoError = capoInput.toIntOrNull()?.let { it !in 0..12 } ?: true
    val name = inputs.name ?: draft.name
    val nameError = name.isNotEmpty() && name.trim().length !in 1..80
    val strings = draft.strings(inputs.notes, inputs.octaves, inputs.frets)
    val ready = workspace.lookup as? ChordLookup.Ready
    val query = when (val lookup = workspace.lookup) {
        is ChordLookup.Searching -> lookup.query
        is ChordLookup.Ready -> lookup.query
        ChordLookup.Idle -> null
    }
    val lookupDraft = ready?.shapes?.getOrNull(ready.selectedIndex)?.let { shape ->
        ChordDraft(context = ready.query.context, shape = shape, selected = ready.query.identity)
    }
    val lookupCandidate = lookupDraft?.let(ChordTheory::selectedCandidate)
    val lookupStrings = lookupDraft?.strings().orEmpty()
    val state = ChordUiState(
        section = ChordSection.valueOf(section), tuningExpanded = tuningExpanded,
        preset = TuningPreset.entries.firstOrNull { it.tuning == draft.context.tuning }?.let { TuningPresetUi.valueOf(it.name) },
        capoInput = capoInput, capo = draft.context.capo, capoError = capoError, strings = strings,
        name = name, nameError = nameError, targetName = workspace.records.firstOrNull { it.id == draft.targetId }?.content?.name,
        analysis = analysis.toUi(), soundingSymbol = selected?.let(ChordTheory::symbol) ?: (analysis as? ChordAnalysis.Note)?.let {
            ChordTheory.pitchName(null, it.midi)
        },
        shapeSymbol = selected?.let { ChordTheory.symbol(ChordTheory.shapeCandidate(it, draft.context.capo)) },
        notes = strings.mapNotNull { it.note }.distinct().joinToString(" · "),
        candidates = (analysis as? ChordAnalysis.Recognized)?.candidates.orEmpty().map { candidate ->
            ChordCandidateUi(candidate.identity.root.ordinal, ChordQualityUi.valueOf(candidate.identity.quality.name),
                ChordTheory.symbol(candidate), candidate.omitted.joinToString(", ") { it.symbol }, candidate.identity == draft.selected)
        },
        roots = PitchClass.entries.map { it.symbol }, root = root, quality = ChordQualityUi.valueOf(quality),
        lookup = when (val lookup = workspace.lookup) {
            ChordLookup.Idle -> ChordLookupUi.Idle
            is ChordLookup.Searching -> ChordLookupUi.Searching
            is ChordLookup.Ready -> if (lookup.shapes.isEmpty()) ChordLookupUi.NoShapes else ChordLookupUi.Ready
        },
        lookupSymbol = lookupCandidate?.let(ChordTheory::symbol) ?: query?.let { it.identity.root.symbol + it.identity.quality.symbol },
        lookupShapeSymbol = lookupCandidate?.let { ChordTheory.symbol(ChordTheory.shapeCandidate(it, query?.context?.capo ?: 0)) },
        lookupStrings = lookupStrings, lookupNotes = lookupStrings.mapNotNull { it.note }.distinct().joinToString(" · "),
        lookupOmitted = lookupCandidate?.omitted?.joinToString(", ") { it.symbol }.orEmpty(),
        representativeIndex = ready?.selectedIndex ?: 0, representativeCount = ready?.shapes?.size ?: 0,
        records = workspace.records.map { record ->
            val recordAnalysis = ChordTheory.analyze(record.content.context, record.content.shape)
            SavedChordUi(record.id, record.content.name, ChordTheory.selectedCandidate(record.content)?.let(ChordTheory::symbol)
                ?: (recordAnalysis as? ChordAnalysis.Note)?.let { ChordTheory.pitchName(null, it.midi) }, recordAnalysis.toUi(),
                record.content.strings().mapNotNull { it.note }.distinct().joinToString(" · "),
                record.content.context.tuning.pitches.joinToString(" · ") { it.note.symbol + it.octave }, record.content.context.capo)
        },
        canSave = draft.shape.hasSound && name.trim().length in 1..80 && !capoError && strings.none { it.noteError || it.octaveError || it.fretError }
            && inputs.matches(draft) && !busy && workspace.readFailure == null,
        busy = busy, unsynced = workspace.persistence == DraftPersistence.Unsynced, readFailed = workspace.readFailure != null,
        notice = notice ?: workspace.actionFailure?.toNotice(),
    )
    return ChordPresentation(state) { event ->
        when (event) {
            is ChordEvent.SetSection -> { section = event.value.name; if (event.value == ChordSection.Lookup && workspace.lookup == ChordLookup.Idle) search() }
            is ChordEvent.SetTuningExpanded -> tuningExpanded = event.value
            is ChordEvent.SetPreset -> {
                inputs = inputs.copy(notes = List(6) { null }, octaves = List(6) { null })
                execute(ChordCommand.SetTuning(TuningPreset.valueOf(event.value.name).tuning))
            }
            is ChordEvent.SetCapo -> {
                inputs = inputs.copy(capo = event.value)
                event.value.toIntOrNull()?.takeIf { it in 0..12 }?.let { execute(ChordCommand.SetCapo(it)) }
            }
            is ChordEvent.SetNote -> if (event.index in 0..5) {
                inputs = inputs.copy(notes = inputs.notes.updated(event.index, event.value),
                    octaves = inputs.octaves.updated(event.index, inputs.octaves[event.index] ?: draft.context.tuning.pitches[event.index].octave.toString()))
                setPitch(event.index)
            }
            is ChordEvent.SetOctave -> if (event.index in 0..5) {
                inputs = inputs.copy(octaves = inputs.octaves.updated(event.index, event.value),
                    notes = inputs.notes.updated(event.index, inputs.notes[event.index] ?: draft.context.tuning.pitches[event.index].note.symbol))
                setPitch(event.index)
            }
            is ChordEvent.SetStop -> if (event.index in 0..5) {
                inputs = inputs.copy(frets = inputs.frets.updated(event.index, null))
                val stop = when (event.value) {
                    ChordStopUi.Muted -> StringStop.Muted
                    ChordStopUi.Open -> StringStop.Open
                    ChordStopUi.Fretted -> StringStop.Fretted(state.strings[event.index].fret.coerceAtLeast(1))
                }
                execute(ChordCommand.SetStop(event.index, stop))
            }
            is ChordEvent.SetFret -> if (event.index in 0..5) {
                inputs = inputs.copy(frets = inputs.frets.updated(event.index, event.value))
                event.value.toIntOrNull()?.takeIf { it in 0..12 }?.let { value ->
                    execute(ChordCommand.SetStop(event.index, if (value == 0) StringStop.Open else StringStop.Fretted(value)))
                }
            }
            is ChordEvent.SetName -> { inputs = inputs.copy(name = event.value); execute(ChordCommand.SetName(event.value)) }
            is ChordEvent.SelectCandidate -> if (event.root in 0..11) {
                execute(ChordCommand.SelectCandidate(ChordIdentity(PitchClass.entries[event.root], ChordQuality.valueOf(event.quality.name))))
            }
            is ChordEvent.SetRoot -> if (event.value in 0..11) { root = event.value; search() }
            is ChordEvent.SetQuality -> { quality = event.value.name; search() }
            ChordEvent.Search -> search()
            is ChordEvent.SelectRepresentative -> execute(ChordCommand.SelectRepresentative(event.index))
            ChordEvent.CopyRepresentative -> { resetInputs(); section = ChordSection.Edit.name; execute(ChordCommand.CopyRepresentative) }
            ChordEvent.NewDraft -> { resetInputs(); execute(ChordCommand.NewDraft) }
            ChordEvent.Save -> if (inputs.matches(chords.current.value.draft)) execute(ChordCommand.SaveDraft, recordOperation = true)
            is ChordEvent.Load -> if (!busy) { resetInputs(); section = ChordSection.Edit.name; execute(ChordCommand.LoadRecord(event.id), recordOperation = true) }
            is ChordEvent.Delete -> execute(ChordCommand.DeleteRecord(event.id), recordOperation = true)
            ChordEvent.RetryDraftWrite -> execute(ChordCommand.RetryDraftWrite)
            ChordEvent.RetryStorageRead -> execute(ChordCommand.RetryStorageRead)
        }
    }
}

private data class ChordInputs(
    val capo: String? = null,
    val name: String? = null,
    val notes: List<String?> = List(6) { null },
    val octaves: List<String?> = List(6) { null },
    val frets: List<String?> = List(6) { null },
) {
    fun matches(draft: ChordDraft): Boolean =
        (capo == null || capo.toIntOrNull() == draft.context.capo) &&
            (name == null || name.trim() == draft.name.trim()) &&
            draft.context.tuning.pitches.indices.all { index ->
                pitch(index, draft) == draft.context.tuning.pitches[index] &&
                    (frets[index] == null || frets[index]?.toStop() == draft.shape.stops[index])
            }

    fun reconcile(draft: ChordDraft): ChordInputs {
        val validCapo = capo?.toIntOrNull()?.takeIf { it in 0..12 }
        val changedPitches = draft.context.tuning.pitches.indices.filter { index ->
            val restored = pitch(index, draft)
            restored != null && restored != draft.context.tuning.pitches[index]
        }
        return copy(
            capo = if (validCapo != null && validCapo != draft.context.capo) null else capo,
            name = if (name != null && name.trim().length in 1..80 && name.trim() != draft.name.trim()) null else name,
            notes = notes.mapIndexed { index, value -> if (index in changedPitches) null else value },
            octaves = octaves.mapIndexed { index, value -> if (index in changedPitches) null else value },
            frets = frets.mapIndexed { index, value ->
                if (value?.toStop()?.let { it != draft.shape.stops[index] } == true) null else value
            },
        )
    }

    private fun pitch(index: Int, draft: ChordDraft): GuitarPitch? {
        val accepted = draft.context.tuning.pitches[index]
        return parseGuitarPitch(notes[index] ?: accepted.note.symbol, octaves[index] ?: accepted.octave.toString())
    }
}

private fun chordInputsSaver(draft: ChordDraft): Saver<ChordInputs, Any> = Saver(
    save = { listOf(it.capo, it.name) + it.notes + it.octaves + it.frets },
    restore = { value ->
        (value as? List<*>)?.takeIf { it.size == 20 && it.all { field -> field == null || field is String } }?.let { fields ->
            val text = fields.map { it as? String }
            ChordInputs(text[0], text[1], text.subList(2, 8), text.subList(8, 14), text.subList(14, 20)).reconcile(draft)
        }
    },
)

private fun String.toStop(): StringStop? = toIntOrNull()?.takeIf { it in 0..12 }?.let {
    if (it == 0) StringStop.Open else StringStop.Fretted(it)
}

private fun <T> List<T>.updated(index: Int, value: T): List<T> = mapIndexed { current, previous -> if (current == index) value else previous }

internal fun ChordDraft.strings(
    rawNotes: List<String?> = List(6) { null }, rawOctaves: List<String?> = List(6) { null }, rawFrets: List<String?> = List(6) { null },
): List<ChordStringUi> {
    val tones = ChordTheory.tones(context, shape)
    return shape.stops.mapIndexed { index, stop ->
        val pitch = context.tuning.pitches[index]
        val noteInput = rawNotes[index] ?: pitch.note.symbol
        val octaveInput = rawOctaves[index] ?: pitch.octave.toString()
        val fret = (stop as? StringStop.Fretted)?.fret ?: 0
        val fretInput = rawFrets[index] ?: fret.toString()
        val sounding = tones[index]
        ChordStringUi(index, when (stop) { StringStop.Muted -> ChordStopUi.Muted; StringStop.Open -> ChordStopUi.Open; is StringStop.Fretted -> ChordStopUi.Fretted },
            fret, sounding?.let { ChordTheory.pitchName(selected, it) }, sounding?.let { selected?.let { identity ->
                ChordTheory.degree(identity, PitchClass.entries[it % 12])?.symbol
            } }, pitch.note.symbol + pitch.octave, noteInput, octaveInput, fretInput,
            parseNote(noteInput) == null, (octaveInput.toIntOrNull()?.let { it !in 0..6 } ?: true) ||
                (parseNote(noteInput) != null && parseGuitarPitch(noteInput, octaveInput) == null),
            rawFrets[index] != null && (fretInput.toIntOrNull()?.let { it !in 0..12 } ?: true))
    }
}

internal fun parseNote(value: String): PitchClass? {
    return writtenSemitone(value)?.let { PitchClass.entries[Math.floorMod(it, 12)] }
}

internal fun parseGuitarPitch(note: String, octave: String): GuitarPitch? {
    val semitone = writtenSemitone(note) ?: return null
    val inputOctave = octave.toIntOrNull()?.takeIf { it in 0..6 } ?: return null
    val midi = (inputOctave + 1) * 12 + semitone
    val canonicalOctave = Math.floorDiv(midi, 12) - 1
    if (canonicalOctave !in 0..6) return null
    return GuitarPitch(PitchClass.entries[Math.floorMod(midi, 12)], canonicalOctave)
}

private fun writtenSemitone(value: String): Int? {
    val text = value.trim().uppercase().replace('♯', '#').replace('♭', 'B')
    val base = when (text.firstOrNull()) { 'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11; else -> return null }
    val alteration = when (text.drop(1)) { "" -> 0; "#" -> 1; "B" -> -1; else -> return null }
    return base + alteration
}

private fun ChordAnalysis.toUi(): ChordAnalysisUi = when (this) {
    ChordAnalysis.Empty -> ChordAnalysisUi.Empty
    is ChordAnalysis.Note -> ChordAnalysisUi.Note
    is ChordAnalysis.Recognized -> ChordAnalysisUi.Recognized
    ChordAnalysis.Unrecognized -> ChordAnalysisUi.Unrecognized
}

private fun ChordFailure.toNotice(): ChordNotice = when (this) {
    ChordFailure.InvalidInput -> ChordNotice.InvalidInput
    ChordFailure.InvalidName -> ChordNotice.InvalidName
    ChordFailure.EmptyShape -> ChordNotice.EmptyShape
    ChordFailure.CandidateMissing -> ChordNotice.CandidateMissing
    ChordFailure.RecordMissing -> ChordNotice.RecordMissing
    ChordFailure.ReadFailed -> ChordNotice.ReadFailed
    ChordFailure.WriteFailed -> ChordNotice.WriteFailed
}
