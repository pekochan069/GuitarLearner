package com.pekochan069.guitarlearner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTheoryTest {
    private val standard = GuitarContext()

    @Test fun independentFormulaFixturesCoverEveryQualityAtEveryRoot() {
        val expected = listOf(
            "0 4 7", "0 3 7", "0 7", "0 3 6", "0 4 8", "0 2 7", "0 5 7",
            "0 4 7 9", "0 3 7 9", "0 2 4 7 9", "0 2 3 7 9", "0 2 4 7", "0 2 3 7",
            "0 4 7 10", "0 4 7 11", "0 3 7 10", "0 3 7 11", "0 3 6 9", "0 3 6 10",
            "0 2 7 10", "0 5 7 10", "0 2 4 7 10", "0 2 4 7 11", "0 2 3 7 10",
            "0 2 4 5 7 10", "0 2 4 5 7 11", "0 2 3 5 7 10", "0 2 4 5 7 9 10",
            "0 2 4 5 7 9 11", "0 2 3 5 7 9 10", "0 4 6 10", "0 4 8 10", "0 1 4 7 10",
            "0 3 4 7 10", "0 2 4 6 7 10", "0 1 4 5 7 9 10",
        ).map { row -> row.split(' ').map(String::toInt).toSet() }
        assertEquals(36, ChordQuality.entries.size)
        for (root in PitchClass.entries) for (quality in ChordQuality.entries) {
            assertEquals("$root $quality", expected[quality.ordinal].map { (it + root.ordinal) % 12 }.toSet(),
                ChordTheory.formula(quality).degrees.map { root.transpose(it.semitones).ordinal }.toSet())
        }
    }

    @Test fun musicalReferenceShapesAndCapoKeepTheirSelectedMeaning() {
        assertCandidate("x32010", PitchClass.C, ChordQuality.Major, "C")
        assertCandidate("x32000", PitchClass.C, ChordQuality.MajorSeventh, "Cmaj7")
        assertCandidate("x53555", PitchClass.D, ChordQuality.MinorNinth, "Dm9")
        assertCandidate("3x3434", PitchClass.G, ChordQuality.SeventhFlat9, "G7♭9")
        assertCandidate("032010", PitchClass.C, ChordQuality.Major, "C/E")
        val shape = shape("x32010")
        val context = standard.copy(capo = 2)
        assertEquals(listOf(null, 50, 54, 57, 62, 66), ChordTheory.tones(context, shape))
        val candidate = candidates(context, shape).first()
        assertEquals("D", ChordTheory.symbol(candidate))
        assertEquals("C", ChordTheory.symbol(ChordTheory.shapeCandidate(candidate, context.capo)))
        assertEquals(shape, ChordTheory.normalize(ChordDraft(context = context, shape = shape)).shape)
    }

    @Test fun exactMatchesPrecedeOmissionsAndRootsAndDefiningTonesCannotBeOmitted() {
        val candidates = candidates(standard, shape("x32213"))
        assertTrue(candidates.size > 1)
        assertEquals(0, candidates.first().omitted.size)
        assertEquals(candidates.map { it.omitted.size }.sorted(), candidates.map { it.omitted.size })
        val c = candidates(standard, shape("x32010"))
        assertTrue(c.none { it.identity.quality == ChordQuality.Seventh })
        val classes = ChordTheory.tones(standard, shape("x32010")).filterNotNull().map { it % 12 }
        assertTrue(c.all { it.identity.root.ordinal in classes })
        val seventhNoFifth = notes(0, 4, 10)
        val omitted = candidates(seventhNoFifth.first, seventhNoFifth.second)
            .first { it.identity == ChordIdentity(PitchClass.C, ChordQuality.Seventh) }
        assertEquals(listOf("5"), omitted.omitted.map { it.symbol })
        val alteredNoFifth = notes(0, 4, 10, 1)
        assertTrue(candidates(alteredNoFifth.first, alteredNoFifth.second)
            .none { it.identity == ChordIdentity(PitchClass.C, ChordQuality.SeventhFlat5) })
        assertFalse(ChordTheory.formula(ChordQuality.Add9).optional.contains(5))
        assertFalse(ChordTheory.formula(ChordQuality.Sixth).optional.contains(5))
        assertFalse(ChordTheory.formula(ChordQuality.ThirteenthFlat9).optional.contains(9))
    }

    @Test fun actualMinimumPitchDeterminesBassInReorderedTuning() {
        val tuning = GuitarTuning(listOf(GuitarPitch(PitchClass.C, 5), GuitarPitch(PitchClass.E, 1),
            GuitarPitch(PitchClass.G, 3), GuitarPitch(PitchClass.C, 4), GuitarPitch(PitchClass.E, 4), GuitarPitch(PitchClass.G, 4)))
        val selected = candidates(GuitarContext(tuning), ChordShape(List(6) { StringStop.Open })).first()
        assertEquals("C/E", ChordTheory.symbol(selected))
        val raised = selected.copy(identity = ChordIdentity(PitchClass.D, ChordQuality.Major), bass = PitchClass.Fs)
        assertEquals("C/E", ChordTheory.symbol(ChordTheory.shapeCandidate(raised, 2)))
    }

    @Test fun compoundDegreesAndEnharmonicNoteNamesFollowSelectedInterpretation() {
        val sharp9 = ChordIdentity(PitchClass.C, ChordQuality.SeventhSharp9)
        assertEquals("♯9", ChordTheory.degree(sharp9, PitchClass.Eb)?.symbol)
        assertEquals("D♯", ChordTheory.noteName(sharp9, PitchClass.Eb))
        assertEquals("3", ChordTheory.degree(sharp9, PitchClass.E)?.symbol)
        val diminished = ChordIdentity(PitchClass.C, ChordQuality.DiminishedSeventh)
        assertEquals("♭♭7", ChordTheory.degree(diminished, PitchClass.A)?.symbol)
        assertEquals("B♭♭", ChordTheory.noteName(diminished, PitchClass.A))
        val ambivalent = shape("x32213")
        val selection = ChordIdentity(PitchClass.A, ChordQuality.MinorSeventh)
        val draft = ChordTheory.normalize(ChordDraft(shape = ambivalent, selected = selection))
        assertEquals(selection, draft.selected)
        assertNotEquals(selection, ChordTheory.normalize(draft.copy(shape = shape("x32010"))).selected)
        assertEquals("B♯3", ChordTheory.pitchName(ChordIdentity(PitchClass.Cs, ChordQuality.MajorSeventh), 60))
        assertEquals("C♭4", ChordTheory.pitchName(ChordIdentity(PitchClass.Ab, ChordQuality.Minor), 59))
    }

    @Test fun emptySingleNoteAndUnrecognizedShapesStayExplicit() {
        assertEquals(ChordAnalysis.Empty, ChordTheory.analyze(standard, ChordShape()))
        assertEquals(ChordAnalysis.Note(40), ChordTheory.analyze(standard, shape("0xxxxx")))
        val unrecognized = notes(0, 1)
        assertEquals(ChordAnalysis.Unrecognized, ChordTheory.analyze(unrecognized.first, unrecognized.second))
    }

    @Test fun everyCatalogSelectionProducesOnlyValidatedCurrentContextRepresentatives() {
        for (root in PitchClass.entries) for (quality in ChordQuality.entries) {
            val identity = ChordIdentity(root, quality)
            val query = ChordQuery(standard.copy(capo = 2), identity)
            val representatives = ChordTheory.representatives(query)
            assertTrue(representatives.size <= 12)
            assertEquals(representatives.size, representatives.distinct().size)
            representatives.forEach { shape ->
                assertTrue("$identity", candidates(query.context, shape).any { it.identity == identity })
                val frets = shape.stops.filterIsInstance<StringStop.Fretted>().map { it.fret }
                assertTrue(frets.isEmpty() || frets.max() - frets.min() <= 3)
            }
        }
    }

    @Test fun exhaustiveWindowPolicyHasTruthfulNoResultUnderCustomTuning() {
        val context = GuitarContext(GuitarTuning(List(6) { GuitarPitch(PitchClass.C, 2) }))
        assertTrue(ChordTheory.representatives(ChordQuery(context, ChordIdentity(PitchClass.C, ChordQuality.Seventh))).isEmpty())
    }

    @Test fun sixStringModelsCopyTheirInputsAndPresetContextHasIndependentLimits() {
        val values = MutableList<StringStop>(6) { StringStop.Open }
        val immutable = ChordShape(values)
        values[0] = StringStop.Muted
        assertEquals(StringStop.Open, immutable.stops[0])
        val context = GuitarContext(GuitarTuning(List(6) { GuitarPitch(PitchClass.B, 6) }), 12)
        assertEquals(List(6) { 119 }, ChordTheory.tones(context, ChordShape(List(6) { StringStop.Fretted(12) })))
        assertEquals(listOf(38, 45, 50, 54, 57, 62), TuningPreset.OpenD.tuning.pitches.map { it.midi })
    }

    private fun assertCandidate(stops: String, root: PitchClass, quality: ChordQuality, symbol: String) {
        val candidate = candidates(standard, shape(stops)).first { it.identity == ChordIdentity(root, quality) }
        assertEquals(symbol, ChordTheory.symbol(candidate))
        assertTrue(candidate.omitted.isEmpty())
    }

    private fun candidates(context: GuitarContext, shape: ChordShape): List<ChordCandidate> =
        (ChordTheory.analyze(context, shape) as ChordAnalysis.Recognized).candidates

    private fun shape(source: String): ChordShape = ChordShape(source.map {
        when (it) { 'x' -> StringStop.Muted; '0' -> StringStop.Open; else -> StringStop.Fretted(it.digitToInt()) }
    })

    private fun notes(vararg classes: Int): Pair<GuitarContext, ChordShape> {
        val pitches = List(6) { GuitarPitch(PitchClass.entries[classes[it % classes.size]], 3) }
        return GuitarContext(GuitarTuning(pitches)) to ChordShape(List(6) { StringStop.Open })
    }
}
