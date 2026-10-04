package com.pekochan069.guitarlearner.domain

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeSequencerTest {
    @Test
    fun ninetyQuarterNoteBpmScalesClickSpacingByTheSelectedNote() {
        val intervals = listOf(BeatUnit.Half to 64_000L, BeatUnit.Quarter to 32_000L,
            BeatUnit.Eighth to 16_000L, BeatUnit.Sixteenth to 8_000L)
        for ((unit, interval) in intervals) {
            val config = MetronomeConfig(90, unit)
            val sequencer = MetronomeSequencer(config, 48_000)
            repeat(8) { index ->
                val beat = sequencer.nextBeat()
                assertEquals("$unit click $index at quarter note = 90", index * interval, beat.frame)
                assertEquals(index % 4, beat.beatIndex)
                assertEquals(config, beat.config)
            }
        }
    }

    @Test
    fun eighthNotesAtQuarterNote120HaveEightQuarterSecondPositionsPerBar() {
        val config = MetronomeConfig(120, BeatUnit.Eighth, List(8) { BeatAccent.Normal })
        val sequencer = MetronomeSequencer(config, 48_000)

        repeat(16) { index ->
            val beat = sequencer.nextBeat()
            assertEquals(index * 12_000L, beat.frame)
            assertEquals(index % 8, beat.beatIndex)
            assertEquals(config, beat.config)
        }
    }

    @Test
    fun mutedAndAccentedPositionsAdvanceLikeNormalPositions() {
        val accents = listOf(BeatAccent.Mute, BeatAccent.Accent, BeatAccent.Normal)
        val sequencer = MetronomeSequencer(MetronomeConfig(120, beats = accents), 48_000)

        repeat(6) { index ->
            val beat = sequencer.nextBeat()
            assertEquals(index * 24_000L, beat.frame)
            assertEquals(accents[index % 3], beat.config.beats[beat.beatIndex])
        }
    }

    @Test
    fun fractionalTempoDoesNotAccumulateWholeFrameRoundingError() {
        for (unit in BeatUnit.entries) {
            val sequencer = MetronomeSequencer(MetronomeConfig(137, unit), 48_000)
            val denominator = 137L * unit.denominator
            repeat(100_000) { index ->
                val beat = sequencer.nextBeat()
                val frameErrorTimesDenominator = abs(beat.frame * denominator - index * 11_520_000L)
                assertTrue("$unit frame error at beat $index", frameErrorTimesDenominator < denominator)
            }
        }
    }

    @Test
    fun minimumAndMaximumQuarterNoteTempoKeepEachNoteValueSpacing() {
        val intervals = listOf(
            Triple(40, BeatUnit.Half, 144_000L), Triple(40, BeatUnit.Quarter, 72_000L),
            Triple(40, BeatUnit.Eighth, 36_000L), Triple(40, BeatUnit.Sixteenth, 18_000L),
            Triple(240, BeatUnit.Half, 24_000L), Triple(240, BeatUnit.Quarter, 12_000L),
            Triple(240, BeatUnit.Eighth, 6_000L), Triple(240, BeatUnit.Sixteenth, 3_000L),
        )
        for ((bpm, unit, interval) in intervals) {
            val sequencer = MetronomeSequencer(MetronomeConfig(bpm, unit), 48_000)
            repeat(8) { index -> assertEquals("$unit at $bpm BPM", index * interval, sequencer.nextBeat().frame) }
        }
    }

    @Test
    fun tempoChangeAtNextBoundaryPreservesBeatOrder() {
        val sequencer = MetronomeSequencer(MetronomeConfig(90), 48_000)
        assertEquals(0, sequencer.nextBeat().beatIndex)
        assertEquals(1, sequencer.nextBeat().beatIndex)

        sequencer.setTempo(120)
        val changed = sequencer.nextBeat()
        assertEquals(2, changed.beatIndex)
        assertEquals(64_000L, changed.frame)
        assertEquals(120, changed.config.bpm)
        val following = sequencer.nextBeat()
        assertEquals(3, following.beatIndex)
        assertEquals(88_000L, following.frame)
    }

    @Test
    fun patternChangesOnlyAtTheNextBar() {
        val old = MetronomeConfig(120, beats = List(3) { BeatAccent.Normal })
        val newBeats = listOf(BeatAccent.Mute, BeatAccent.Accent)
        val sequencer = MetronomeSequencer(old, 48_000)
        sequencer.nextBeat()

        sequencer.setPattern(BeatUnit.Sixteenth, newBeats)
        assertEquals(old, sequencer.nextBeat().config)
        assertEquals(old, sequencer.nextBeat().config)
        val boundary = sequencer.nextBeat()
        assertEquals(0, boundary.beatIndex)
        assertEquals(72_000L, boundary.frame)
        assertEquals(old.copy(denominator = BeatUnit.Sixteenth, beats = newBeats), boundary.config)
        assertEquals(78_000L, sequencer.nextBeat().frame)
    }

    @Test
    fun presetLoadChangesTempoAndPatternTogetherAndCancelsEarlierTempoEdit() {
        val old = MetronomeConfig(120)
        val preset = MetronomeConfig(80, BeatUnit.Eighth, List(8) { BeatAccent.Normal })
        val sequencer = MetronomeSequencer(old, 48_000)
        sequencer.nextBeat()

        sequencer.setTempo(200)
        sequencer.setConfig(preset)
        repeat(3) { index ->
            val beat = sequencer.nextBeat()
            assertEquals(index + 1, beat.beatIndex)
            assertEquals(old, beat.config)
        }
        val boundary = sequencer.nextBeat()
        assertEquals(96_000L, boundary.frame)
        assertEquals(0, boundary.beatIndex)
        assertEquals(preset, boundary.config)
        assertEquals(114_000L, sequencer.nextBeat().frame)
    }

    @Test
    fun laterTempoEditSupersedesPendingPresetTempo() {
        val old = MetronomeConfig(120)
        val preset = MetronomeConfig(80, BeatUnit.Eighth, List(8) { BeatAccent.Mute })
        val sequencer = MetronomeSequencer(old, 48_000)
        sequencer.nextBeat()
        sequencer.setConfig(preset)
        sequencer.setTempo(180)

        repeat(3) { index ->
            val beat = sequencer.nextBeat()
            assertEquals(index + 1, beat.beatIndex)
            assertEquals(old.copy(bpm = 180), beat.config)
        }
        val boundary = sequencer.nextBeat()
        assertEquals(0, boundary.beatIndex)
        assertEquals(72_000L, boundary.frame)
        assertEquals(preset.copy(bpm = 180), boundary.config)
        assertEquals(80_000L, sequencer.nextBeat().frame)
    }

    @Test
    fun patternChangeDoesNotUndoAnEarlierTempoEdit() {
        val sequencer = MetronomeSequencer(MetronomeConfig(120), 48_000)
        sequencer.nextBeat()
        sequencer.setTempo(180)
        sequencer.setPattern(BeatUnit.Half, List(2) { BeatAccent.Normal })

        repeat(3) { assertEquals(180, sequencer.nextBeat().config.bpm) }
        assertEquals(MetronomeConfig(180, BeatUnit.Half, List(2) { BeatAccent.Normal }), sequencer.nextBeat().config)
    }

    @Test
    fun nextFrameCanBeReadWithoutCommittingTheUpcomingBeat() {
        val sequencer = MetronomeSequencer(MetronomeConfig(120), 48_000)
        assertEquals(0L, sequencer.nextFrame)
        assertEquals(0L, sequencer.nextFrame)
        assertEquals(0, sequencer.nextBeat().beatIndex)
        assertEquals(24_000L, sequencer.nextFrame)
        sequencer.setTempo(180)
        assertEquals(24_000L, sequencer.nextFrame)

        val changed = sequencer.nextBeat()
        assertEquals(24_000L, changed.frame)
        assertEquals(1, changed.beatIndex)
        assertEquals(180, changed.config.bpm)
        assertEquals(40_000L, sequencer.nextFrame)
    }

    @Test
    fun resetStartsAtTheConfiguredFirstPositionAndDiscardsPendingChanges() {
        val sequencer = MetronomeSequencer(MetronomeConfig(137), 48_000)
        repeat(7) { sequencer.nextBeat() }
        sequencer.setTempo(240)
        sequencer.setPattern(BeatUnit.Sixteenth, List(16) { BeatAccent.Accent })
        val selected = MetronomeConfig(40, BeatUnit.Eighth, listOf(BeatAccent.Mute, BeatAccent.Normal))

        sequencer.reset(selected)
        assertEquals(ScheduledBeat(0, 0, selected), sequencer.nextBeat())
        assertEquals(ScheduledBeat(36_000, 1, selected), sequencer.nextBeat())
    }

    @Test
    fun configurationOwnsAnImmutablePatternAndRejectsInvalidValues() {
        val input = mutableListOf(BeatAccent.Accent, BeatAccent.Normal)
        val config = MetronomeConfig(beats = input)
        input.clear()
        assertEquals(listOf(BeatAccent.Accent, BeatAccent.Normal), config.beats)
        assertEquals(config, config.copy())
        assertThrows(UnsupportedOperationException::class.java) {
            (config.beats as MutableList<BeatAccent>).clear()
        }
        assertThrows(IllegalArgumentException::class.java) { MetronomeConfig(39) }
        assertThrows(IllegalArgumentException::class.java) { MetronomeConfig(241) }
        assertThrows(IllegalArgumentException::class.java) { MetronomeConfig(beats = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { MetronomeConfig(beats = List(17) { BeatAccent.Normal }) }
        assertThrows(IllegalArgumentException::class.java) { MetronomeSequencer(config, 0) }
    }
}
