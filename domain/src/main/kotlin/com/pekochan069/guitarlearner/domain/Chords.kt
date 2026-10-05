package com.pekochan069.guitarlearner.domain

import arrow.core.Either
import java.util.Collections
import kotlinx.coroutines.flow.StateFlow

enum class PitchClass(val symbol: String) {
    C("C"), Cs("C♯"), D("D"), Eb("E♭"), E("E"), F("F"),
    Fs("F♯"), G("G"), Ab("A♭"), A("A"), Bb("B♭"), B("B");

    fun transpose(semitones: Int): PitchClass = entries[Math.floorMod(ordinal + semitones, 12)]
}

data class GuitarPitch(val note: PitchClass, val octave: Int) {
    init { require(octave in 0..6) }
    val midi: Int get() = (octave + 1) * 12 + note.ordinal
}

class GuitarTuning(pitches: List<GuitarPitch>) {
    val pitches: List<GuitarPitch> = Collections.unmodifiableList(pitches.toList())
    init { require(this.pitches.size == 6) }

    fun withString(index: Int, pitch: GuitarPitch): GuitarTuning = GuitarTuning(
        pitches.mapIndexed { current, value -> if (current == index) pitch else value },
    )

    override fun equals(other: Any?): Boolean = other is GuitarTuning && pitches == other.pitches
    override fun hashCode(): Int = pitches.hashCode()
}

enum class TuningPreset(val tuning: GuitarTuning) {
    Standard(tuning("E2 A2 D3 G3 B3 E4")),
    DropD(tuning("D2 A2 D3 G3 B3 E4")),
    Dadgad(tuning("D2 A2 D3 G3 A3 D4")),
    OpenG(tuning("D2 G2 D3 G3 B3 D4")),
    OpenD(tuning("D2 A2 D3 F#3 A3 D4")),
}

private fun tuning(source: String): GuitarTuning = GuitarTuning(source.split(' ').map { value ->
    val note = when (value.dropLast(1)) {
        "D" -> PitchClass.D
        "E" -> PitchClass.E
        "F#" -> PitchClass.Fs
        "G" -> PitchClass.G
        "A" -> PitchClass.A
        "B" -> PitchClass.B
        else -> error("Unknown preset note")
    }
    GuitarPitch(note, value.last().digitToInt())
})

data class GuitarContext(val tuning: GuitarTuning = TuningPreset.Standard.tuning, val capo: Int = 0) {
    init { require(capo in 0..12) }
}

sealed interface StringStop {
    data object Muted : StringStop
    data object Open : StringStop
    data class Fretted(val fret: Int) : StringStop { init { require(fret in 1..12) } }
}

class ChordShape(stops: List<StringStop> = List(6) { StringStop.Muted }) {
    val stops: List<StringStop> = Collections.unmodifiableList(stops.toList())
    init { require(this.stops.size == 6) }
    val hasSound: Boolean get() = stops.any { it != StringStop.Muted }

    fun withString(index: Int, stop: StringStop): ChordShape = ChordShape(
        stops.mapIndexed { current, value -> if (current == index) stop else value },
    )

    override fun equals(other: Any?): Boolean = other is ChordShape && stops == other.stops
    override fun hashCode(): Int = stops.hashCode()
}

enum class ChordQuality(val symbol: String) {
    Major(""), Minor("m"), Power("5"), Diminished("dim"), Augmented("aug"), Sus2("sus2"), Sus4("sus4"),
    Sixth("6"), MinorSixth("m6"), SixNine("6/9"), MinorSixNine("m6/9"), Add9("add9"), MinorAdd9("madd9"),
    Seventh("7"), MajorSeventh("maj7"), MinorSeventh("m7"), MinorMajorSeventh("mMaj7"),
    DiminishedSeventh("dim7"), HalfDiminished("m7♭5"), SeventhSus2("7sus2"), SeventhSus4("7sus4"),
    Ninth("9"), MajorNinth("maj9"), MinorNinth("m9"), Eleventh("11"), MajorEleventh("maj11"),
    MinorEleventh("m11"), Thirteenth("13"), MajorThirteenth("maj13"), MinorThirteenth("m13"),
    SeventhFlat5("7♭5"), SeventhSharp5("7♯5"), SeventhFlat9("7♭9"), SeventhSharp9("7♯9"),
    NinthSharp11("9♯11"), ThirteenthFlat9("13♭9"),
}

data class ChordIdentity(val root: PitchClass, val quality: ChordQuality)

data class ChordDegree(val number: Int, val alteration: Int, val semitones: Int) {
    val symbol: String get() = when (alteration) {
        -2 -> "♭♭"
        -1 -> "♭"
        0 -> ""
        1 -> "♯"
        else -> error("Unsupported degree alteration")
    } + number
}

class ChordFormula(degrees: List<ChordDegree>, optional: Set<Int> = emptySet()) {
    val degrees: List<ChordDegree> = Collections.unmodifiableList(degrees.toList())
    val optional: Set<Int> = Collections.unmodifiableSet(optional.toSet())
}

data class ChordCandidate(val identity: ChordIdentity, val bass: PitchClass, val omitted: List<ChordDegree>)

sealed interface ChordAnalysis {
    data object Empty : ChordAnalysis
    data class Note(val midi: Int) : ChordAnalysis
    data class Recognized(val candidates: List<ChordCandidate>) : ChordAnalysis
    data object Unrecognized : ChordAnalysis
}

data class ChordDraft(
    val name: String = "",
    val context: GuitarContext = GuitarContext(),
    val shape: ChordShape = ChordShape(),
    val selected: ChordIdentity? = null,
    val targetId: String? = null,
)

data class SavedChord(val id: String, val content: ChordDraft) {
    init {
        require(id.isNotBlank() && id.length <= 80)
        require(content.targetId == null)
        require(content.name == content.name.trim() && content.name.length in 1..80)
        require(content.shape.hasSound)
    }
}

data class ChordQuery(val context: GuitarContext, val identity: ChordIdentity)

sealed interface ChordLookup {
    data object Idle : ChordLookup
    data class Searching(val query: ChordQuery) : ChordLookup
    data class Ready(val query: ChordQuery, val shapes: List<ChordShape>, val selectedIndex: Int = 0) : ChordLookup
}

enum class DraftPersistence { Synced, Unsynced }

sealed interface ChordFailure {
    data object InvalidInput : ChordFailure
    data object InvalidName : ChordFailure
    data object EmptyShape : ChordFailure
    data object CandidateMissing : ChordFailure
    data object RecordMissing : ChordFailure
    data object ReadFailed : ChordFailure
    data object WriteFailed : ChordFailure
}

data class ChordWorkspace(
    val draft: ChordDraft = ChordDraft(),
    val records: List<SavedChord> = emptyList(),
    val analysis: ChordAnalysis = ChordAnalysis.Empty,
    val lookup: ChordLookup = ChordLookup.Idle,
    val persistence: DraftPersistence = DraftPersistence.Synced,
    val readFailure: ChordFailure? = null,
    val actionFailure: ChordFailure? = null,
)

sealed interface ChordCommand {
    data class SetName(val name: String) : ChordCommand
    data class SetTuning(val tuning: GuitarTuning) : ChordCommand
    data class SetStringPitch(val index: Int, val pitch: GuitarPitch) : ChordCommand
    data class SetCapo(val capo: Int) : ChordCommand
    data class SetStop(val index: Int, val stop: StringStop) : ChordCommand
    data class SelectCandidate(val identity: ChordIdentity) : ChordCommand
    data class Search(val identity: ChordIdentity) : ChordCommand
    data class SelectRepresentative(val index: Int) : ChordCommand
    data object CopyRepresentative : ChordCommand
    data object NewDraft : ChordCommand
    data object SaveDraft : ChordCommand
    data class LoadRecord(val id: String) : ChordCommand
    data class DeleteRecord(val id: String) : ChordCommand
    data object RetryDraftWrite : ChordCommand
    data object RetryStorageRead : ChordCommand
}

interface Chords {
    val current: StateFlow<ChordWorkspace>
    suspend fun execute(command: ChordCommand): Either<ChordFailure, Unit>
}
