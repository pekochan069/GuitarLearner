package com.pekochan069.guitarlearner.adapters

import com.pekochan069.guitarlearner.domain.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class ProgressionPcmTest {
    private val shape = ChordShape(listOf(StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2), StringStop.Open, StringStop.Fretted(1), StringStop.Open))
    @Test fun finalShortestChordPlaysItsFullDurationBeforeCompletion() {
        val content = ProgressionContent(timing = MetronomeConfig(bpm = 240), metronomeEnabled = false,
            steps = listOf(ProgressionStep.Chord("C", shape, NoteDuration(NoteValue.ThirtySecond))))
        val pcm = ProgressionPcm(ProgressionSequencer(content, 0, 48_000), 48_000)
        val buffer = ShortArray(60_000)
        val markers = mutableListOf<ProgressionMarker>()
        pcm.render(buffer, buffer.size, markers::add)
        val start = markers.first { it.position.stepIndex == 0 }.frame.toInt()
        val end = markers.single { it.complete }.frame.toInt()
        assertEquals(1_500, end - start)
        assertTrue(buffer.sliceArray(start until end).any { abs(it.toInt()) > 100 })
        assertTrue(buffer.sliceArray(end until buffer.size).all { it == 0.toShort() })
    }
    @Test fun tieKeepsWaveformAndRestSilencesWhileTheClockContinues() {
        val tied = ProgressionContent(metronomeEnabled = false, steps = listOf(
            ProgressionStep.Chord("C", shape, tieToNext = true), ProgressionStep.Chord("C", shape), ProgressionStep.Rest()))
        val merged = tied.copy(steps = listOf(ProgressionStep.Chord("C", shape, NoteDuration(NoteValue.Half)), ProgressionStep.Rest()))
        fun render(content: ProgressionContent): ShortArray = ShortArray(240_000).also {
            ProgressionPcm(ProgressionSequencer(content, 0, 48_000), 48_000).render(it, it.size) { }
        }
        val samples = render(tied)
        assertArrayEquals(render(merged), samples)
        assertTrue(samples.sliceArray(194_400 until 240_000).all { it == 0.toShort() })
    }
    @Test fun generatedPluckHasCorrectPitchAndUsableLongDecay() {
        val guitar = PluckedGuitar(48_000)
        guitar.strum(listOf(55, null, null, null, null, null), 0)
        val samples = DoubleArray(48_000 * 7) { guitar.next() }
        val period = (239..251).maxBy { lag -> (5_000..15_000).sumOf { samples[it] * samples[it + lag] } }
        assertTrue("G3 period $period", period in 244..246)
        val attack = samples.sliceArray(10_000..20_000).sumOf { abs(it) }
        val sustain = samples.sliceArray(288_000..298_000).sumOf { abs(it) }
        assertTrue("six-second sustain", sustain > attack * 0.08)
    }
    @Test fun exportGeneratedPracticeSampleWhenRequested() {
        val path = System.getenv("PROGRESSION_SAMPLE") ?: return
        val g = ChordShape(listOf(StringStop.Fretted(3), StringStop.Fretted(2), StringStop.Open, StringStop.Open, StringStop.Open, StringStop.Fretted(3)))
        val content = ProgressionContent(steps = listOf(ProgressionStep.Chord("C", shape, NoteDuration(NoteValue.Whole)),
            ProgressionStep.Chord("G", g, NoteDuration(NoteValue.Whole)), ProgressionStep.Rest(NoteDuration(NoteValue.Half))))
        val samples = ShortArray(48_000 * 10)
        ProgressionPcm(ProgressionSequencer(content, 0, 48_000), 48_000).render(samples, samples.size) { }
        val bytes = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(48_000).putInt(96_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples.size * 2)
        samples.forEach(bytes::putShort)
        File(path).writeBytes(bytes.array())
        assertTrue(File(path).length() > 44)
    }
}
