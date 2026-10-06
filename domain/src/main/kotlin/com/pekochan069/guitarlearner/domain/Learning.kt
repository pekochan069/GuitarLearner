package com.pekochan069.guitarlearner.domain

import java.util.Collections
import kotlinx.coroutines.flow.StateFlow

enum class LessonFamily { Theory, Technique }

enum class LessonId(val savedId: String, val family: LessonFamily) {
    NotesIntervals("notes_intervals", LessonFamily.Theory),
    Scales("scales", LessonFamily.Theory),
    ChordConstruction("chord_construction", LessonFamily.Theory),
    DiatonicFunctions("diatonic_functions", LessonFamily.Theory),
    BasicProgressions("basic_progressions", LessonFamily.Theory),
    CircleOfFifths("circle_of_fifths", LessonFamily.Theory),
    Strumming("strumming", LessonFamily.Technique),
    AlternatePicking("alternate_picking", LessonFamily.Technique),
    HammerOnPullOff("hammer_on_pull_off", LessonFamily.Technique),
    Slide("slide", LessonFamily.Technique),
    Bending("bending", LessonFamily.Technique),
    Vibrato("vibrato", LessonFamily.Technique),
    PalmMute("palm_mute", LessonFamily.Technique),
}

enum class ExplorationConcept(val lesson: LessonId) {
    NotesIntervals(LessonId.NotesIntervals), Scales(LessonId.Scales), ChordConstruction(LessonId.ChordConstruction),
    DiatonicFunctions(LessonId.DiatonicFunctions), BasicProgressions(LessonId.BasicProgressions), CircleOfFifths(LessonId.CircleOfFifths),
}

sealed interface LearningTarget {
    data class Lesson(val id: LessonId) : LearningTarget
    data class Explore(val concept: ExplorationConcept) : LearningTarget
}

enum class NoteLetter(val naturalSemitones: Int) { C(0), D(2), E(4), F(5), G(7), A(9), B(11) }

data class SpelledNote(val letter: NoteLetter, val accidental: Int = 0) {
    init { require(accidental in -6..6) }
    val symbol: String get() = letter.name + if (accidental < 0) "♭".repeat(-accidental) else "♯".repeat(accidental)
    val pitchClass: PitchClass get() = PitchClass.entries[Math.floorMod(letter.naturalSemitones + accidental, 12)]
}

enum class BeginnerScale { Major, NaturalMinor }
enum class BeginnerProgression { OneFourFiveOne, OneFiveSixFour, TwoFiveOne }
enum class HarmonicFunction { Tonic, Predominant, Dominant }

data class LearningSelection(
    val tonic: SpelledNote = SpelledNote(NoteLetter.C),
    val scale: BeginnerScale = BeginnerScale.Major,
    val chord: ChordQuality = ChordQuality.Major,
    val interval: TrainingInterval = TrainingInterval.PerfectFifth,
    val progression: BeginnerProgression = BeginnerProgression.OneFourFiveOne,
    val chordIndex: Int = 0,
) {
    init {
        require(chord in LearningRelations.beginnerChords)
        require(chordIndex in 0..6)
    }
}

data class LearningNote(val spelling: SpelledNote, val degree: ChordDegree, val midi: Int) {
    init {
        require(midi in 40..76)
        require(Math.floorMod(midi, 12) == spelling.pitchClass.ordinal)
    }
}

data class LearningFretboardPosition(val stringNumber: Int, val fret: Int, val note: LearningNote) {
    init { require(stringNumber in 1..6 && fret in 0..12) }
}

class LearningChord(
    val root: SpelledNote,
    val quality: ChordQuality,
    val roman: String,
    val function: HarmonicFunction,
    notes: List<LearningNote>,
) {
    val notes: List<LearningNote> = immutable(notes)
    val symbol: String get() = root.symbol + quality.symbol
}

data class LearningCircleKey(val tonic: SpelledNote, val relativeMinor: SpelledNote)

class ExampleStep(notes: List<LearningNote>) {
    val notes: List<LearningNote> = immutable(notes)
    init { require(this.notes.isNotEmpty()) }
}

class LearningExample(steps: List<ExampleStep>) {
    val steps: List<ExampleStep> = immutable(steps)
    init { require(this.steps.isNotEmpty()) }
}

class LearningModel(
    notes: List<LearningNote> = emptyList(),
    fretboard: List<LearningFretboardPosition> = emptyList(),
    chords: List<LearningChord> = emptyList(),
    circle: List<LearningCircleKey> = emptyList(),
    val example: LearningExample? = null,
) {
    val notes: List<LearningNote> = immutable(notes)
    val fretboard: List<LearningFretboardPosition> = immutable(fretboard)
    val chords: List<LearningChord> = immutable(chords)
    val circle: List<LearningCircleKey> = immutable(circle)
}

object LearningRelations {
    val tonics: List<SpelledNote> = immutable(listOf(
        SpelledNote(NoteLetter.C), SpelledNote(NoteLetter.D, -1), SpelledNote(NoteLetter.D),
        SpelledNote(NoteLetter.E, -1), SpelledNote(NoteLetter.E), SpelledNote(NoteLetter.F),
        SpelledNote(NoteLetter.F, 1), SpelledNote(NoteLetter.G), SpelledNote(NoteLetter.A, -1),
        SpelledNote(NoteLetter.A), SpelledNote(NoteLetter.B, -1), SpelledNote(NoteLetter.B),
    ))
    val beginnerChords: List<ChordQuality> = immutable(listOf(
        ChordQuality.Major, ChordQuality.Minor, ChordQuality.Diminished, ChordQuality.Augmented,
        ChordQuality.MajorSeventh, ChordQuality.Seventh, ChordQuality.MinorSeventh,
        ChordQuality.HalfDiminished, ChordQuality.DiminishedSeventh,
    ))
    private val positions = (0..5).flatMap { string -> (0..12).map { fret -> TrainingPosition(string, fret) } }
    private val circle = List(12) { index ->
        val tonic = tonics[index * 7 % 12]
        LearningCircleKey(tonic, ChordTheory.spell(tonic, ChordTheory.degree(6)))
    }

    fun describe(target: LearningTarget, selection: LearningSelection): LearningModel {
        val lesson = when (target) {
            is LearningTarget.Lesson -> target.id
            is LearningTarget.Explore -> target.concept.lesson
        }
        if (lesson.family == LessonFamily.Technique) return LearningModel()
        val chords = when (lesson) {
            LessonId.DiatonicFunctions -> diatonic(selection)
            LessonId.BasicProgressions -> {
                val degrees = when (selection.progression) {
                    BeginnerProgression.OneFourFiveOne -> listOf(0, 3, 4, 0)
                    BeginnerProgression.OneFiveSixFour -> listOf(0, 4, 5, 3)
                    BeginnerProgression.TwoFiveOne -> listOf(1, 4, 0)
                }
                val diatonic = diatonic(selection)
                degrees.map { diatonic[it] }
            }
            else -> emptyList()
        }
        val notes = when (lesson) {
            LessonId.NotesIntervals -> intervalNotes(selection)
            LessonId.Scales -> scaleNotes(selection.tonic, selection.scale)
            LessonId.ChordConstruction -> chordNotes(selection.tonic, selection.chord)
            LessonId.DiatonicFunctions, LessonId.BasicProgressions -> chords[selection.chordIndex.coerceAtMost(chords.lastIndex)].notes
            LessonId.CircleOfFifths -> scaleNotes(selection.tonic, BeginnerScale.Major)
            else -> error("Technique lessons have no sampled theory example")
        }
        val steps = when (lesson) {
            LessonId.ChordConstruction, LessonId.DiatonicFunctions -> listOf(ExampleStep(notes))
            LessonId.BasicProgressions -> chords.map { ExampleStep(it.notes) }
            else -> notes.map { ExampleStep(listOf(it)) }
        }
        val fretboard = positions.mapNotNull { position ->
            notes.firstOrNull { it.spelling.pitchClass.ordinal == position.midi % 12 }?.let { note ->
                LearningFretboardPosition(position.stringNumber, position.fret, note.copy(midi = position.midi))
            }
        }
        return LearningModel(notes, fretboard, chords, if (lesson == LessonId.CircleOfFifths) circle else emptyList(), LearningExample(steps))
    }

    private fun scaleNotes(tonic: SpelledNote, scale: BeginnerScale): List<LearningNote> = (1..8).map { number ->
        val alteration = if (scale == BeginnerScale.NaturalMinor && (number == 3 || number in 6..7)) -1 else 0
        val degree = ChordTheory.degree(number, alteration)
        LearningNote(ChordTheory.spell(tonic, degree), degree, rootMidi(tonic) + if (number == 8) 12 else degree.semitones)
    }

    private fun chordNotes(root: SpelledNote, quality: ChordQuality): List<LearningNote> = ChordTheory.formula(quality).degrees.map { degree ->
        LearningNote(ChordTheory.spell(root, degree), degree, rootMidi(root) + degree.semitones)
    }

    private fun diatonic(selection: LearningSelection): List<LearningChord> {
        val scale = scaleNotes(selection.tonic, BeginnerScale.Major).take(7)
        val romans = listOf("I", "II", "III", "IV", "V", "VI", "VII")
        return scale.mapIndexed { index, root ->
            val intervals = listOf(0, 2, 4).map { offset -> Math.floorMod(scale[(index + offset) % 7].midi - root.midi, 12) }
            val quality = beginnerChords.first { ChordTheory.formula(it).degrees.map(ChordDegree::semitones) == intervals }
            val roman = when (quality) {
                ChordQuality.Minor -> romans[index].lowercase()
                ChordQuality.Diminished -> romans[index].lowercase() + "°"
                else -> romans[index]
            }
            val function = when (index) {
                1, 3 -> HarmonicFunction.Predominant
                4, 6 -> HarmonicFunction.Dominant
                else -> HarmonicFunction.Tonic
            }
            LearningChord(root.spelling, quality, roman, function, chordNotes(root.spelling, quality))
        }
    }

    private fun intervalNotes(selection: LearningSelection): List<LearningNote> {
        val degree = when (selection.interval) {
            TrainingInterval.Unison -> ChordTheory.degree(1)
            TrainingInterval.MinorSecond -> ChordTheory.degree(2, -1)
            TrainingInterval.MajorSecond -> ChordTheory.degree(2)
            TrainingInterval.MinorThird -> ChordTheory.degree(3, -1)
            TrainingInterval.MajorThird -> ChordTheory.degree(3)
            TrainingInterval.PerfectFourth -> ChordTheory.degree(4)
            TrainingInterval.Tritone -> ChordTheory.degree(4, 1)
            TrainingInterval.PerfectFifth -> ChordTheory.degree(5)
            TrainingInterval.MinorSixth -> ChordTheory.degree(6, -1)
            TrainingInterval.MajorSixth -> ChordTheory.degree(6)
            TrainingInterval.MinorSeventh -> ChordTheory.degree(7, -1)
            TrainingInterval.MajorSeventh -> ChordTheory.degree(7)
            TrainingInterval.Octave -> ChordTheory.degree(8)
        }
        val midi = rootMidi(selection.tonic)
        return listOf(
            LearningNote(selection.tonic, ChordTheory.degree(1), midi),
            LearningNote(ChordTheory.spell(selection.tonic, degree), degree, midi + selection.interval.semitones),
        )
    }

    private fun rootMidi(note: SpelledNote): Int = 48 + note.pitchClass.ordinal
}

class LearningProgress(completed: Set<LessonId> = emptySet(), val lastViewed: LessonId? = null) {
    val completed: Set<LessonId> = Collections.unmodifiableSet(completed.toSet())

    fun copy(completed: Set<LessonId> = this.completed, lastViewed: LessonId? = this.lastViewed): LearningProgress =
        LearningProgress(completed, lastViewed)

    override fun equals(other: Any?): Boolean = other is LearningProgress && completed == other.completed && lastViewed == other.lastViewed
    override fun hashCode(): Int = 31 * completed.hashCode() + (lastViewed?.hashCode() ?: 0)
}

sealed interface LearningFailure {
    data object ReadFailed : LearningFailure
    data object WriteFailed : LearningFailure
    data object PlaybackFailed : LearningFailure
    data object ShutdownFailed : LearningFailure
    data object FocusDenied : LearningFailure
    data object OutputInterrupted : LearningFailure
    data class MetronomeStopFailed(val cause: MetronomeFailure) : LearningFailure
}

sealed interface LearningStorageState {
    data object Saved : LearningStorageState
    data object Saving : LearningStorageState
    data class Failed(val failure: LearningFailure) : LearningStorageState
}

sealed interface LearningAudioState {
    data object Idle : LearningAudioState
    data class Preparing(val instrument: TrainingInstrument) : LearningAudioState
    data class Playing(val instrument: TrainingInstrument) : LearningAudioState
    data class Failed(val failure: LearningFailure) : LearningAudioState
}

data class LearningSnapshot(
    val progress: LearningProgress = LearningProgress(),
    val storage: LearningStorageState = LearningStorageState.Saved,
    val audio: LearningAudioState = LearningAudioState.Idle,
)

sealed interface LearningRequest {
    data class Activate(val target: LearningTarget, val selection: LearningSelection) : LearningRequest
    data class Complete(val lesson: LessonId) : LearningRequest
    data class Listen(val instrument: TrainingInstrument) : LearningRequest
    data object RetrySave : LearningRequest
    data object RetryRead : LearningRequest
    data object Deactivate : LearningRequest
}

interface Learning {
    val current: StateFlow<LearningSnapshot>
    fun submit(request: LearningRequest)
}

private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())
