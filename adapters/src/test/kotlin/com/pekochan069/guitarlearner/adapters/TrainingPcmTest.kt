package com.pekochan069.guitarlearner.adapters

import com.pekochan069.guitarlearner.domain.IntervalPresentation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class TrainingPcmTest {
    @Test fun ascendingDescendingAndHarmonicShareTheSamePitchSamplesAndNeverClip() {
        val first = TrainingPcm.render(TrainingTone(listOf(52), IntervalPresentation.Ascending))
        val second = TrainingPcm.render(TrainingTone(listOf(55), IntervalPresentation.Ascending))
        val ascending = TrainingPcm.render(TrainingTone(listOf(52, 55), IntervalPresentation.Ascending))
        val descending = TrainingPcm.render(TrainingTone(listOf(52, 55), IntervalPresentation.Descending))
        val harmonic = TrainingPcm.render(TrainingTone(listOf(52, 55), IntervalPresentation.Harmonic))
        val offset = TrainingPcm.NOTE_FRAMES + TrainingPcm.GAP_FRAMES
        assertTrue(first.contentEquals(ascending.sliceArray(first.indices)))
        assertTrue(second.contentEquals(ascending.sliceArray(offset until offset + second.size)))
        assertTrue(second.contentEquals(descending.sliceArray(second.indices)))
        assertTrue(first.contentEquals(descending.sliceArray(offset until offset + first.size)))
        assertTrue(ascending.sliceArray(TrainingPcm.NOTE_FRAMES until offset).all { it == 0.toShort() })
        harmonic.indices.forEach { assertTrue(abs(harmonic[it].toInt() - (first[it] + second[it]) / 2) <= 1) }
        assertTrue(harmonic.all { abs(it.toInt()) <= 12_000 })
        assertEquals(0.toShort(), harmonic.first())
        assertEquals(0.toShort(), harmonic.last())
    }

    @Test fun namedComparisonC4AndA440HaveTheirExpectedFundamental() {
        fun power(midi: Int, frequency: Double): Double {
            val samples = TrainingPcm.render(TrainingTone(listOf(midi), IntervalPresentation.Ascending))
            var real = 0.0
            var imaginary = 0.0
            for (frame in 1000 until 25_000) {
                val phase = 2.0 * PI * frequency * frame / TrainingPcm.SAMPLE_RATE
                real += samples[frame] * cos(phase)
                imaginary += samples[frame] * sin(phase)
            }
            return real * real + imaginary * imaginary
        }
        assertTrue(power(60, 261.625565) > power(60, 440.0) * 100)
        assertTrue(power(69, 440.0) > power(69, 442.0) * 100)
    }
}
