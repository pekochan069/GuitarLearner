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
}
