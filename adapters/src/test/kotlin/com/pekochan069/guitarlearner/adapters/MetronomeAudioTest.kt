package com.pekochan069.guitarlearner.adapters

import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.BeatUnit
import com.pekochan069.guitarlearner.domain.MetronomeConfig
import com.pekochan069.guitarlearner.domain.MetronomeSequencer
import com.pekochan069.guitarlearner.domain.ScheduledBeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeAudioTest {
    @Test
    fun pcmContainsDistinctAccentsAndAdvancesThroughSilenceAcrossChunks() {
        val sequencer = MetronomeSequencer(MetronomeConfig(120, BeatUnit.Eighth,
            listOf(BeatAccent.Accent, BeatAccent.Normal, BeatAccent.Mute)), 48_000)
        val renderer = MetronomePcm(sequencer, 48_000)
        val samples = ShortArray(72_000)
        val chunk = ShortArray(240)
        val beats = mutableListOf<ScheduledBeat>()
        for (offset in samples.indices step chunk.size) {
            renderer.render(chunk, chunk.size, beats::add)
            chunk.copyInto(samples, offset)
        }
        assertEquals(listOf(0L, 24_000L, 48_000L), beats.map { it.frame })
        assertEquals(listOf(0, 1, 2), beats.map { it.beatIndex })
        assertTrue(samples.sliceArray(48_000 until 72_000).all { it == 0.toShort() })
        val accented = samples.sliceArray(0 until 960)
        val normal = samples.sliceArray(24_000 until 24_960)
        fun energy(values: ShortArray): Long = values.sumOf { it.toLong() * it }
        fun crossings(values: ShortArray): Int = (1 until values.size).count { values[it - 1] <= 0 && values[it] > 0 }
        assertTrue(energy(accented) > energy(normal) * 2)
        assertTrue(crossings(accented) > crossings(normal) * 1.8)
        assertTrue(samples.sliceArray(960 until 24_000).all { it == 0.toShort() })
    }

    @Test
    fun rendererDoesNotCommitTheNextBeatWhileRenderingEarlierSilence() {
        val sequencer = MetronomeSequencer(MetronomeConfig(120), 48_000)
        val renderer = MetronomePcm(sequencer, 48_000)
        val beats = mutableListOf<ScheduledBeat>()
        renderer.render(ShortArray(23_990), 23_990, beats::add)
        sequencer.setTempo(240)
        renderer.render(ShortArray(12_020), 12_020, beats::add)
        assertEquals(listOf(0L, 24_000L, 36_000L), beats.map { it.frame })
        assertEquals(listOf(120, 240, 240), beats.map { it.config.bpm })
        assertEquals(listOf(0, 1, 2), beats.map { it.beatIndex })
    }

    @Test
    fun futurePresentationAnchorDoesNotPublishFrameZeroEarly() {
        val clock = PresentedFrameClock(48_000)
        assertTrue(clock.anchor(0, 1_000_000_000, 100, 4_096, 900_000_000))
        assertEquals(-1L, clock.frame(900_000_000, 100, 4_096))
        assertEquals(0L, clock.frame(1_000_000_000, 100, 4_096))
        assertEquals(480L, clock.frame(1_010_000_000, 600, 4_096))
        assertFalse(clock.anchor(100, 100_000_000, 600, 4_096, 3_000_000_000))
        assertEquals(600L, clock.frame(3_000_000_000, 600, 4_096))
    }

    @Test
    fun timestampsUnwrapAndNeverMoveTheIndicatorBackward() {
        val clock = PresentedFrameClock(48_000)
        val epoch = 0x1_0000_0000L
        assertTrue(clock.anchor(50, 1_000_000_000, epoch + 100, epoch + 200, 1_000_000_000))
        assertEquals(epoch + 50, clock.frame(1_000_000_000, epoch + 100, epoch + 200))
        assertFalse(clock.anchor(20, 900_000_000, epoch + 100, epoch + 200, 1_000_000_000))
        assertEquals(epoch + 100, clock.frame(1_000_000_000, epoch + 100, epoch + 200))
        assertEquals(epoch + 100, clock.frame(1_000_000_000, epoch + 80, epoch + 200))
    }

    @Test
    fun routeChangesInvalidateTimestampAndDistinguishDisconnectionFromManualSwitch() {
        val clock = PresentedFrameClock(48_000)
        assertTrue(clock.anchor(100, 1_000_000_000, 100, 4_096, 1_000_000_000))
        clock.invalidate()
        assertFalse(clock.anchored)
        assertEquals(500L, clock.frame(1_010_000_000, 500, 4_096))
        assertTrue(activeRouteRemoved(8, 2, listOf(2)))
        assertTrue(activeRouteRemoved(8, null, listOf(2)))
        assertFalse(activeRouteRemoved(8, 2, listOf(8, 2)))
        assertFalse(activeRouteRemoved(2, 2, listOf(2)))
        assertFalse(activeRouteRemoved(null, 2, listOf(2)))
    }
}
