package com.pekochan069.guitarlearner.adapters

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
        val first = TrainingPcm.render(TrainingTone(listOf(listOf(52))), ::sample)
        val second = TrainingPcm.render(TrainingTone(listOf(listOf(55))), ::sample)
        val ascending = TrainingPcm.render(TrainingTone(listOf(listOf(52), listOf(55))), ::sample)
        val descending = TrainingPcm.render(TrainingTone(listOf(listOf(55), listOf(52))), ::sample)
        val harmonic = TrainingPcm.render(TrainingTone(listOf(listOf(52, 55))), ::sample)
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
            TrainingPcm.render(TrainingTone(listOf(listOf(60)), instrument)) { chosen, midi ->
                requested.add(chosen to midi)
                sample(chosen, midi)
            }
        }
        assertEquals(TrainingInstrument.entries.map { it to 60 }, requested)
        assertFalse(outputs[0].contentEquals(outputs[1]))
        assertEquals(sample(TrainingInstrument.Guitar, 60)[1000], outputs[1][1000])
        assertThrows(IllegalArgumentException::class.java) {
            TrainingPcm.render(TrainingTone(listOf(listOf(60)))) { _, _ -> ShortArray(10) }
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
            assertArrayEquals(samples, TrainingPcm.render(TrainingTone(listOf(listOf(midi)), instrument), ::recordedSample))
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

    @Test fun fullScaleTriadSeventhAndProgressionUseEveryStepAndEveryChordTone() {
        val examples = listOf(
            listOf(60, 62, 64, 65, 67, 69, 71, 72).map { listOf(it) } to listOf(6000, 6200, 6400, 6500, 6700, 6900, 7100, 7200),
            listOf(listOf(60, 64, 67)) to listOf(6366),
            listOf(listOf(60, 64, 67, 70)) to listOf(6525),
            listOf(listOf(60, 64, 67), listOf(65, 69, 72), listOf(67, 71, 74), listOf(60, 64, 67)) to listOf(6366, 6866, 7066, 6366),
        )
        for (instrument in TrainingInstrument.entries) for ((steps, expected) in examples) {
            val requested = mutableListOf<Pair<TrainingInstrument, Int>>()
            val pcm = TrainingPcm.render(TrainingTone(steps, instrument)) { bank, midi ->
                requested.add(bank to midi)
                ShortArray(TrainingPcm.NOTE_FRAMES) { (midi * 100).toShort() }
            }
            assertEquals(steps.flatten().map { instrument to it }, requested)
            assertEquals(expected.size * 31_200 + (expected.size - 1) * 6_720, pcm.size)
            expected.forEachIndexed { index, value ->
                val start = index * (31_200 + 6_720)
                assertEquals(value.toShort(), pcm[start])
                assertEquals(value.toShort(), pcm[start + 31_199])
                if (index < expected.lastIndex) assertTrue(pcm.sliceArray(start + 31_200 until start + 37_920).all { it == 0.toShort() })
            }
        }
        for (steps in listOf(emptyList(), listOf(emptyList()), listOf(listOf(39)), listOf(listOf(77)))) {
            assertThrows(IllegalArgumentException::class.java) { TrainingTone(steps) }
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
