package com.pekochan069.guitarlearner.domain

import org.junit.Assert.*
import org.junit.Test

class ProgressionSequencerTest {
    private val shape = ChordShape(listOf(StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2), StringStop.Open, StringStop.Fretted(1), StringStop.Open))
    @Test fun dottedWholeCrossesBarAndTieWithoutAttackAndRestSilences() {
        val content = ProgressionContent(context = GuitarContext(capo = 2), metronomeEnabled = false, steps = listOf(
            ProgressionStep.Chord("C", shape, NoteDuration(NoteValue.Whole, true), true),
            ProgressionStep.Chord("C", shape), ProgressionStep.Rest()))
        val sequence = ProgressionSequencer(content, 0, 48_000)
        val ticks = List(64 + 96 + 16 + 16) { sequence.nextTick() }
        assertEquals(4, ticks.take(64).count { it.click != null })
        assertEquals(1, ticks.count { it.attack != null })
        assertEquals(listOf(null, 50, 54, 57, 62, 66), ticks[64].attack)
        assertEquals(192_000L, ticks[160].frame - ticks[64].frame)
        assertTrue(ticks[176].silence)
        assertTrue(ticks.last().complete)
    }
    @Test fun liveTempoChangesInsideLongChordAndLoopCountsInOnlyOnce() {
        val sequence = ProgressionSequencer(ProgressionContent(loop = true, steps = listOf(ProgressionStep.Chord("C", shape))), 0, 48_000)
        val first = List(80) { sequence.nextTick() }
        assertEquals(4, first.count { it.position.countInBeat != null && it.click != null })
        assertNotNull(first[64].attack)
        sequence.setTempo(120); sequence.setMetronome(false)
        val after = sequence.nextTick()
        assertNull(after.position.countInBeat)
        assertNotNull(after.attack)
        assertEquals(120, after.position.bpm)
        assertNull(after.click)
        assertEquals(1_500L, sequence.nextFrame - after.frame)
    }
    @Test fun invalidTiesRejectAndEditedAdjacencyClearsOnlyInvalidJoins() {
        val chord = ProgressionStep.Chord("C", shape, tieToNext = true)
        assertThrows(IllegalArgumentException::class.java) { ProgressionContent(steps = listOf(chord, ProgressionStep.Rest())) }
        val valid = ProgressionContent(steps = listOf(chord, chord.copy(tieToNext = false)))
        assertFalse((valid.withEditedSteps(listOf(chord, ProgressionStep.Rest())).steps.first() as ProgressionStep.Chord).tieToNext)
        assertThrows(IllegalArgumentException::class.java) { ProgressionStep.Chord("empty", ChordShape()) }
        assertEquals(3, NoteDuration(NoteValue.ThirtySecond, true).ticks)
    }
    @Test fun selectedTiedChordAttacksAfterCompleteEighthNoteCountIn() {
        val chord = ProgressionStep.Chord("C", shape)
        val sequence = ProgressionSequencer(ProgressionContent(timing = MetronomeConfig(120, BeatUnit.Eighth, List(3) { BeatAccent.Normal }),
            metronomeEnabled = false, steps = listOf(chord.copy(tieToNext = true), chord)), 1, 48_000)
        val countIn = List(24) { sequence.nextTick() }
        assertEquals(3, countIn.count { it.click != null })
        assertTrue(countIn.all { it.attack == null && it.position.stepIndex == -1 })
        val first = sequence.nextTick()
        assertEquals(36_000L, first.frame)
        assertEquals(1, first.position.stepIndex)
        assertNotNull(first.attack)
        assertNull(first.click)
    }
    @Test fun fractionalFramesRemainStableAcrossLongLoops() {
        val sequence = ProgressionSequencer(ProgressionContent(timing = MetronomeConfig(bpm = 137), loop = true,
            steps = listOf(ProgressionStep.Rest(NoteDuration(NoteValue.ThirtySecond, true)))), 0, 44_100)
        val ticks = 32_000
        repeat(ticks) { sequence.nextTick() }
        val exact = ticks * 44_100.0 * 60.0 / 137 / 16
        assertTrue(kotlin.math.abs(sequence.nextFrame - exact) < 1.0)
    }
    @Test fun finalShortestChordAndRestKeepTheirFullDuration() {
        for (step in listOf(ProgressionStep.Chord("C", shape, NoteDuration(NoteValue.ThirtySecond)),
            ProgressionStep.Rest(NoteDuration(NoteValue.ThirtySecond)))) {
            val sequence = ProgressionSequencer(ProgressionContent(timing = MetronomeConfig(bpm = 240), steps = listOf(step)), 0, 48_000)
            val ticks = List(66) { sequence.nextTick() }
            assertTrue(ticks.last().complete)
            assertEquals(49_500L, sequence.nextFrame)
            assertEquals(1_500L, sequence.nextFrame - ticks[64].frame)
        }
    }
}
