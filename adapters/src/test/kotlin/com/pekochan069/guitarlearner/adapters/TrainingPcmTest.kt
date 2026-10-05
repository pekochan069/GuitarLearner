package com.pekochan069.guitarlearner.adapters

import com.pekochan069.guitarlearner.domain.IntervalPresentation
import com.pekochan069.guitarlearner.domain.TrainingInstrument
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class TrainingPcmTest {
    @Test fun ascendingDescendingAndHarmonicShareTheSamePitchSamplesAndNeverClip() {
        val first = TrainingPcm.render(TrainingTone(listOf(52), IntervalPresentation.Ascending), ::sample)
        val second = TrainingPcm.render(TrainingTone(listOf(55), IntervalPresentation.Ascending), ::sample)
        val ascending = TrainingPcm.render(TrainingTone(listOf(52, 55), IntervalPresentation.Ascending), ::sample)
        val descending = TrainingPcm.render(TrainingTone(listOf(52, 55), IntervalPresentation.Descending), ::sample)
        val harmonic = TrainingPcm.render(TrainingTone(listOf(52, 55), IntervalPresentation.Harmonic), ::sample)
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

    @Test fun theSelectedInstrumentBankSuppliesEveryPitchIncludingComparisonC4() {
        val requested = mutableListOf<Pair<TrainingInstrument, Int>>()
        val outputs = TrainingInstrument.entries.map { instrument ->
            TrainingPcm.render(TrainingTone(listOf(60), IntervalPresentation.Ascending, instrument)) { chosen, midi ->
                requested.add(chosen to midi)
                sample(chosen, midi)
            }
        }
        assertEquals(TrainingInstrument.entries.map { it to 60 }, requested)
        assertFalse(outputs[0].contentEquals(outputs[1]))
        assertEquals(sample(TrainingInstrument.Guitar, 60)[1000], outputs[1][1000])
        assertThrows(IllegalArgumentException::class.java) {
            TrainingPcm.render(TrainingTone(listOf(60), IntervalPresentation.Ascending)) { _, _ -> ShortArray(10) }
        }
    }

    @Test fun allBundledNotesHaveTheExpectedFormatAndTheInstrumentsHaveDistinctRecordings() {
        for (instrument in TrainingInstrument.entries) for (midi in 40..76) {
            val samples = recordedSample(instrument, midi)
            assertEquals(TrainingPcm.NOTE_FRAMES, samples.size)
            assertEquals(0.toShort(), samples.first())
            assertEquals(0.toShort(), samples.last())
            val peak = samples.maxOf { abs(it.toInt()) }
            assertTrue("$instrument/$midi must be audible and bounded", peak in 500..12_000)
            assertArrayEquals(samples, TrainingPcm.render(TrainingTone(listOf(midi), IntervalPresentation.Ascending, instrument), ::recordedSample))
        }
        assertFalse(recordedSample(TrainingInstrument.Piano, 60).contentEquals(recordedSample(TrainingInstrument.Guitar, 60)))
    }

    @Test fun bundledComparisonC4AndA4HaveFundamentalsNearTheirEqualTemperamentReference() {
        for (instrument in TrainingInstrument.entries) for (midi in listOf(60, 69)) {
            val samples = recordedSample(instrument, midi)
            val reference = 440.0 * 2.0.pow((midi - 69) / 12.0)
            val peak = (-40..40 step 2).maxBy { cents -> power(samples, reference * 2.0.pow(cents / 1200.0)) }
            assertTrue("$instrument/$midi fundamental offset $peak cents", abs(peak) <= 15)
            val signal = power(samples, reference * 2.0.pow(peak / 1200.0))
            assertTrue(signal > maxOf(power(samples, reference * 2.0.pow(-1.0 / 12)), power(samples, reference * 2.0.pow(1.0 / 12))) * 3)
        }
    }

    private fun sample(instrument: TrainingInstrument, midi: Int): ShortArray = ShortArray(TrainingPcm.NOTE_FRAMES) { frame ->
        val fade = min(1.0, min(frame, TrainingPcm.NOTE_FRAMES - 1 - frame) / 480.0)
        ((frame * midi % 1000 - 500) * (if (instrument == TrainingInstrument.Piano) 12 else 20) * fade).toInt().toShort()
    }

    private fun recordedSample(instrument: TrainingInstrument, midi: Int): ShortArray {
        val bytes = File("src/main/assets/training/${instrument.name.lowercase(Locale.ROOT)}/$midi.pcm").readBytes()
        assertEquals(TrainingPcm.NOTE_FRAMES * Short.SIZE_BYTES, bytes.size)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(TrainingPcm.NOTE_FRAMES) { buffer.short }
    }

    private fun power(samples: ShortArray, frequency: Double): Double {
        var real = 0.0
        var imaginary = 0.0
        for (frame in 1000 until 25_000) {
            val phase = 2.0 * PI * frequency * frame / TrainingPcm.SAMPLE_RATE
            real += samples[frame] * cos(phase)
            imaginary += samples[frame] * sin(phase)
        }
        return real * real + imaginary * imaginary
    }
}
