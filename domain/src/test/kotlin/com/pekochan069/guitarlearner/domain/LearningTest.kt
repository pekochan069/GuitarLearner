package com.pekochan069.guitarlearner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningTest {
    @Test fun oneCatalogKeepsStableLessonIdentityAndCourseOrder() {
        assertEquals(listOf(
            "notes_intervals", "scales", "chord_construction", "diatonic_functions", "basic_progressions", "circle_of_fifths",
            "strumming", "alternate_picking", "hammer_on_pull_off", "slide", "bending", "vibrato", "palm_mute",
        ), LessonId.entries.map { it.savedId })
        assertEquals(List(6) { LessonFamily.Theory } + List(7) { LessonFamily.Technique }, LessonId.entries.map { it.family })
        assertEquals(listOf(
            LessonId.NotesIntervals, LessonId.Scales, LessonId.ChordConstruction,
            LessonId.DiatonicFunctions, LessonId.BasicProgressions, LessonId.CircleOfFifths,
        ), ExplorationConcept.entries.map { it.lesson })
        val selected = LearningSelection(tonic = SpelledNote(NoteLetter.G))
        val lesson = model(LessonId.Scales, selected)
        val exploration = LearningRelations.describe(LearningTarget.Explore(ExplorationConcept.Scales), selected)
        assertEquals(lesson.notes, exploration.notes)
        assertEquals(lesson.fretboard, exploration.fretboard)
    }

    @Test fun majorScalesUseTheirKeySpellingAndAscendThroughTheOctave() {
        val g = model(LessonId.Scales, LearningSelection(tonic = SpelledNote(NoteLetter.G)))
        assertEquals(listOf("G", "A", "B", "C", "D", "E", "F♯", "G"), names(g.notes))
        assertEquals(listOf(55, 57, 59, 60, 62, 64, 66, 67), g.notes.map { it.midi })
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8"), g.notes.map { it.degree.symbol })
        assertEquals(g.notes.map { listOf(it) }, requireNotNull(g.example).steps.map { it.notes })
        val f = model(LessonId.Scales, LearningSelection(tonic = SpelledNote(NoteLetter.F)))
        assertEquals(listOf("F", "G", "A", "B♭", "C", "D", "E", "F"), names(f.notes))
        assertEquals(listOf(53, 55, 57, 58, 60, 62, 64, 65), f.notes.map { it.midi })
        assertTrue(g.fretboard.filter { it.note.spelling.pitchClass == PitchClass.Fs }.all { it.note.spelling.symbol == "F♯" })
        assertTrue(f.fretboard.filter { it.note.spelling.pitchClass == PitchClass.Bb }.all { it.note.spelling.symbol == "B♭" })
    }

    @Test fun naturalMinorLowersThreeSixAndSevenWithoutLosingEnharmonicContext() {
        val a = model(LessonId.Scales, LearningSelection(tonic = SpelledNote(NoteLetter.A), scale = BeginnerScale.NaturalMinor))
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "A"), names(a.notes))
        assertEquals(listOf(57, 59, 60, 62, 64, 65, 67, 69), a.notes.map { it.midi })
        assertEquals(listOf("1", "2", "♭3", "4", "5", "♭6", "♭7", "8"), a.notes.map { it.degree.symbol })
        val flat = model(LessonId.Scales, LearningSelection(tonic = SpelledNote(NoteLetter.D, -1), scale = BeginnerScale.NaturalMinor))
        assertEquals(listOf("D♭", "E♭", "F♭", "G♭", "A♭", "B♭♭", "C♭", "D♭"), names(flat.notes))
        assertEquals(listOf(49, 51, 52, 54, 56, 57, 59, 61), flat.notes.map { it.midi })
        val sharp = model(LessonId.Scales, LearningSelection(tonic = SpelledNote(NoteLetter.C, 1)))
        assertEquals(listOf("C♯", "D♯", "E♯", "F♯", "G♯", "A♯", "B♯", "C♯"), names(sharp.notes))
        val majorFlat = model(LessonId.Scales, LearningSelection(tonic = SpelledNote(NoteLetter.D, -1)))
        assertEquals(sharp.notes.map { it.midi }, majorFlat.notes.map { it.midi })
        assertNotEquals(names(sharp.notes), names(majorFlat.notes))
    }

    @Test fun chordConstructionKeepsEveryDefiningToneInOneHarmonicExample() {
        val c = model(LessonId.ChordConstruction)
        assertEquals(listOf("C", "E", "G"), names(c.notes))
        assertEquals(listOf(48, 52, 55), c.notes.map { it.midi })
        assertEquals(c.notes, requireNotNull(c.example).steps.single().notes)
        val d = model(LessonId.ChordConstruction, LearningSelection(tonic = SpelledNote(NoteLetter.D)))
        assertEquals(listOf("D", "F♯", "A"), names(d.notes))
        assertEquals(listOf(50, 54, 57), d.notes.map { it.midi })
        val fixtures = listOf(
            Triple(ChordQuality.Major, listOf("C", "E", "G"), listOf(48, 52, 55)),
            Triple(ChordQuality.Minor, listOf("C", "E♭", "G"), listOf(48, 51, 55)),
            Triple(ChordQuality.Diminished, listOf("C", "E♭", "G♭"), listOf(48, 51, 54)),
            Triple(ChordQuality.Augmented, listOf("C", "E", "G♯"), listOf(48, 52, 56)),
            Triple(ChordQuality.MajorSeventh, listOf("C", "E", "G", "B"), listOf(48, 52, 55, 59)),
            Triple(ChordQuality.Seventh, listOf("C", "E", "G", "B♭"), listOf(48, 52, 55, 58)),
            Triple(ChordQuality.MinorSeventh, listOf("C", "E♭", "G", "B♭"), listOf(48, 51, 55, 58)),
            Triple(ChordQuality.HalfDiminished, listOf("C", "E♭", "G♭", "B♭"), listOf(48, 51, 54, 58)),
            Triple(ChordQuality.DiminishedSeventh, listOf("C", "E♭", "G♭", "B♭♭"), listOf(48, 51, 54, 57)),
        )
        assertEquals(fixtures.map { it.first }, LearningRelations.beginnerChords)
        fixtures.forEach { (quality, spellings, midi) ->
            val chord = model(LessonId.ChordConstruction, LearningSelection(chord = quality))
            assertEquals(quality.name, spellings, names(chord.notes))
            assertEquals(midi, requireNotNull(chord.example).steps.single().notes.map { it.midi })
        }
    }

    @Test fun diatonicRowsDeriveTheirTriadsFunctionsAndSelectedHighlightFromTheScale() {
        val c = model(LessonId.DiatonicFunctions, LearningSelection(chordIndex = 1))
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"), c.chords.map { it.symbol })
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), c.chords.map { it.roman })
        assertEquals(listOf(
            HarmonicFunction.Tonic, HarmonicFunction.Predominant, HarmonicFunction.Tonic,
            HarmonicFunction.Predominant, HarmonicFunction.Dominant, HarmonicFunction.Tonic, HarmonicFunction.Dominant,
        ), c.chords.map { it.function })
        assertEquals(listOf("D", "F", "A"), names(c.notes))
        assertEquals(c.chords[1].notes, requireNotNull(c.example).steps.single().notes)
        assertEquals(setOf(PitchClass.D, PitchClass.F, PitchClass.A), c.fretboard.map { it.note.spelling.pitchClass }.toSet())
        val g = model(LessonId.DiatonicFunctions, LearningSelection(tonic = SpelledNote(NoteLetter.G), chordIndex = 6))
        assertEquals("F♯dim", g.chords[6].symbol)
        assertEquals(listOf("F♯", "A", "C"), names(g.notes))
    }

    @Test fun retainedNaturalMinorExplorationCannotChangeTheMajorKeyHarmonyLessons() {
        val retained = LearningSelection(scale = BeginnerScale.NaturalMinor)
        val diatonic = model(LessonId.DiatonicFunctions, retained)
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"), diatonic.chords.map { it.symbol })
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), diatonic.chords.map { it.roman })
        val progression = model(LessonId.BasicProgressions, retained)
        assertEquals(listOf("C", "F", "G", "C"), progression.chords.map { it.symbol })
        assertEquals(listOf(listOf(48, 52, 55), listOf(53, 57, 60), listOf(55, 59, 62), listOf(48, 52, 55)),
            requireNotNull(progression.example).steps.map { step -> step.notes.map { it.midi } })
    }

    @Test fun progressionRowsAndAudioShareTheCompleteOrderedChordGroups() {
        val primary = model(LessonId.BasicProgressions, LearningSelection(chordIndex = 1))
        assertEquals(listOf("C", "F", "G", "C"), primary.chords.map { it.symbol })
        assertEquals(listOf("I", "IV", "V", "I"), primary.chords.map { it.roman })
        assertEquals(listOf("F", "A", "C"), names(primary.notes))
        assertEquals(listOf(listOf(48, 52, 55), listOf(53, 57, 60), listOf(55, 59, 62), listOf(48, 52, 55)),
            requireNotNull(primary.example).steps.map { step -> step.notes.map { it.midi } })
        val pop = model(LessonId.BasicProgressions, LearningSelection(progression = BeginnerProgression.OneFiveSixFour))
        assertEquals(listOf("C", "G", "Am", "F"), pop.chords.map { it.symbol })
        assertEquals(listOf("I", "V", "vi", "IV"), pop.chords.map { it.roman })
        val cadence = model(LessonId.BasicProgressions, LearningSelection(progression = BeginnerProgression.TwoFiveOne, chordIndex = 6))
        assertEquals(listOf("Dm", "G", "C"), cadence.chords.map { it.symbol })
        assertEquals(listOf("ii", "V", "I"), cadence.chords.map { it.roman })
        assertEquals(listOf("C", "E", "G"), names(cadence.notes))
        assertEquals(cadence.chords.map { it.notes }, requireNotNull(cadence.example).steps.map { it.notes })
    }

    @Test fun fifthsKeepTwelveDistinctKeysAndTheirCorrectRelativeMinorSpelling() {
        val circle = model(LessonId.CircleOfFifths).circle
        assertEquals(listOf(
            "C/Am", "G/Em", "D/Bm", "A/F♯m", "E/C♯m", "B/G♯m",
            "F♯/D♯m", "D♭/B♭m", "A♭/Fm", "E♭/Cm", "B♭/Gm", "F/Dm",
        ), circle.map { it.tonic.symbol + "/" + it.relativeMinor.symbol + "m" })
        assertEquals(12, circle.map { it.tonic.pitchClass }.toSet().size)
        assertEquals(LearningRelations.tonics.toSet(), circle.map { it.tonic }.toSet())
        val selected = model(LessonId.CircleOfFifths, LearningSelection(tonic = SpelledNote(NoteLetter.F), scale = BeginnerScale.NaturalMinor))
        assertEquals(listOf("F", "G", "A", "B♭", "C", "D", "E", "F"), names(selected.notes))
    }

    @Test fun fretboardContainsEveryMatchingStandardPositionAndUsesItsActualPitch() {
        val chord = model(LessonId.ChordConstruction)
        assertEquals(setOf(
            6 to 0, 6 to 3, 6 to 8, 6 to 12, 5 to 3, 5 to 7, 5 to 10,
            4 to 2, 4 to 5, 4 to 10, 3 to 0, 3 to 5, 3 to 9, 3 to 12,
            2 to 1, 2 to 5, 2 to 8, 1 to 0, 1 to 3, 1 to 8, 1 to 12,
        ), chord.fretboard.map { it.stringNumber to it.fret }.toSet())
        assertEquals(21, chord.fretboard.size)
        assertEquals(listOf("E", "G", "C", "E"), chord.fretboard.filter { it.stringNumber == 6 }.map { it.note.spelling.symbol })
        assertEquals(listOf(40, 43, 48, 52), chord.fretboard.filter { it.stringNumber == 6 }.map { it.note.midi })
        assertEquals(listOf(64, 67, 72, 76), chord.fretboard.filter { it.stringNumber == 1 }.map { it.note.midi })
        assertEquals(setOf("1", "3", "5"), chord.fretboard.map { it.note.degree.symbol }.toSet())
    }

    @Test fun everyAvailableTheoryExampleStaysInsideTheSharedSampleBankWithoutTruncation() {
        val models = LearningRelations.tonics.flatMap { tonic ->
            val selected = LearningSelection(tonic = tonic)
            BeginnerScale.entries.map { model(LessonId.Scales, selected.copy(scale = it)) } +
                LearningRelations.beginnerChords.map { model(LessonId.ChordConstruction, selected.copy(chord = it)) } +
                TrainingInterval.entries.map { model(LessonId.NotesIntervals, selected.copy(interval = it)) } +
                BeginnerScale.entries.flatMap { scale -> BeginnerProgression.entries.map { progression ->
                    model(LessonId.BasicProgressions, selected.copy(scale = scale, progression = progression))
                } }
        }
        models.forEach { value ->
            val example = requireNotNull(value.example)
            assertTrue(example.steps.all { it.notes.isNotEmpty() && it.notes.all { note -> note.midi in 40..76 } })
            assertTrue(value.fretboard.all { it.note.midi in 40..76 })
            assertEquals(value.notes.map { it.spelling.pitchClass }.toSet(), value.fretboard.map { it.note.spelling.pitchClass }.toSet())
        }
        LearningRelations.tonics.forEach { tonic ->
            assertEquals(8, requireNotNull(model(LessonId.Scales, LearningSelection(tonic = tonic)).example).steps.size)
            TrainingInterval.entries.forEach { interval ->
                val example = requireNotNull(model(LessonId.NotesIntervals, LearningSelection(tonic = tonic, interval = interval)).example)
                assertEquals(2, example.steps.size)
                assertEquals(interval.semitones, example.steps[1].notes.single().midi - example.steps[0].notes.single().midi)
            }
        }
    }

    @Test fun techniqueLessonsDoNotPresentSampledTheoryAsTechniqueAudio() {
        LessonId.entries.filter { it.family == LessonFamily.Technique }.forEach { id ->
            val technique = model(id)
            assertNull(technique.example)
            assertTrue(technique.notes.isEmpty() && technique.fretboard.isEmpty() && technique.chords.isEmpty() && technique.circle.isEmpty())
        }
    }

    @Test fun progressAndAudioExamplesDefensivelyCopyTheirCollections() {
        val completed = mutableSetOf(LessonId.Scales)
        val progress = LearningProgress(completed, LessonId.Scales)
        completed.clear()
        assertEquals(setOf(LessonId.Scales), progress.completed)
        assertEquals(progress, progress.copy())
        assertEquals(setOf(LessonId.Scales, LessonId.Strumming), progress.copy(completed = progress.completed + LessonId.Strumming).completed)
        assertThrows(UnsupportedOperationException::class.java) { (progress.completed as MutableSet<LessonId>).clear() }
        val notes = model(LessonId.ChordConstruction).notes.toMutableList()
        val step = ExampleStep(notes)
        val steps = mutableListOf(step)
        val example = LearningExample(steps)
        notes.clear()
        steps.clear()
        assertEquals(listOf("C", "E", "G"), names(example.steps.single().notes))
        assertEquals(LearningStorageState.Saved, LearningSnapshot().storage)
        assertEquals(LearningAudioState.Idle, LearningSnapshot().audio)
    }

    @Test fun selectionAndExampleBoundariesRejectUnsupportedInput() {
        assertThrows(IllegalArgumentException::class.java) { LearningSelection(chord = ChordQuality.Thirteenth) }
        assertThrows(IllegalArgumentException::class.java) { LearningSelection(chordIndex = -1) }
        assertThrows(IllegalArgumentException::class.java) { LearningSelection(chordIndex = 7) }
        assertThrows(IllegalArgumentException::class.java) { LearningNote(SpelledNote(NoteLetter.C), ChordTheory.degree(1), 39) }
        assertThrows(IllegalArgumentException::class.java) { LearningNote(SpelledNote(NoteLetter.C), ChordTheory.degree(1), 49) }
        assertThrows(IllegalArgumentException::class.java) { ExampleStep(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { LearningExample(emptyList()) }
    }

    private fun model(id: LessonId, selection: LearningSelection = LearningSelection()): LearningModel =
        LearningRelations.describe(LearningTarget.Lesson(id), selection)

    private fun names(notes: List<LearningNote>): List<String> = notes.map { it.spelling.symbol }
}
