package com.pekochan069.guitarlearner.domain

import java.io.File
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class GuitarPitchDetectorTest {
    private val detector = YinHarmonicDetector()

    @Test fun resolvesAllSixStringsAndSignedCentOffsets() {
        for (string in StandardString.entries) {
            for (offset in listOf(-12.0, -5.0, 0.0, 3.0, 12.0)) {
                val frequency = string.frequencyHz * 2.0.pow(offset / 1200.0)
                val supported = detector.analyze(tone(frequency), RATE) as PitchEvidence.Supported
                assertEquals("$string $offset", offset, TuningPolicy.cents(supported.pitch, string), 0.9)
            }
        }
    }

    @Test fun weakFundamentalWithDominantHarmonicsKeepsTheObservedLowOctave() {
        for (string in StandardString.entries) {
            val frequency = string.frequencyHz
            val input = FloatArray(SIZE) { index ->
                (0.06 * sin(phase(index, frequency)) + 0.35 * sin(phase(index, frequency * 2)) +
                    0.18 * sin(phase(index, frequency * 3))).toFloat()
            }
            val supported = detector.analyze(input, RATE) as PitchEvidence.Supported
            assertEquals(string.name, 0.0, TuningPolicy.cents(supported.pitch, string), 1.0)
        }
    }

    @Test fun missingFundamentalNoiseAndIdentifiableMixedStringsAbstain() {
        val frequency = StandardString.E2.frequencyHz
        val missing = FloatArray(SIZE) { (0.3 * sin(phase(it, frequency * 2)) + 0.2 * sin(phase(it, frequency * 3))).toFloat() }
        assertTrue(detector.analyze(missing, RATE) is PitchEvidence.Uncertain)
        val random = Random(1234)
        val noise = FloatArray(SIZE) { random.nextFloat() * 0.5f - 0.25f }
        assertTrue(detector.analyze(noise, RATE) is PitchEvidence.Uncertain)
        for ((first, second) in listOf(StandardString.E2 to StandardString.A2, StandardString.D3 to StandardString.B3,
            StandardString.G3 to StandardString.E4)) {
            val mixed = FloatArray(SIZE) { (0.25 * sin(phase(it, first.frequencyHz)) + 0.25 * sin(phase(it, second.frequencyHz))).toFloat() }
            assertTrue("$first+$second", detector.analyze(mixed, RATE) is PitchEvidence.Uncertain)
        }
    }

    @Test fun decayIsMeasuredWhileSilenceClippingAndWeakInputCannotEarnInTune() {
        val decaying = FloatArray(SIZE) { (exp(-it.toDouble() / SIZE) * 0.3 * sin(phase(it, 110.0))).toFloat() }
        val supported = detector.analyze(decaying, RATE) as PitchEvidence.Supported
        assertEquals(110.0, supported.pitch.value, 0.15)
        assertEquals(PitchEvidence.Silence, detector.analyze(FloatArray(SIZE), RATE))
        assertTrue(detector.analyze(tone(110.0, 1.5).map { it.coerceIn(-1f, 1f) }.toFloatArray(), RATE) is PitchEvidence.Uncertain)
        assertTrue(detector.analyze(tone(110.0, 0.002), RATE) !is PitchEvidence.Supported)
        assertTrue(detector.analyze(FloatArray(SIZE) { Float.NaN }, RATE) is PitchEvidence.Uncertain)
    }

    @Test fun lowFrequencyHandlingRumbleDoesNotReplaceAnObservedGuitarFundamental() {
        for (string in StandardString.entries) {
            val input = FloatArray(SIZE) { (0.3 * sin(phase(it, 10.0)) + 0.1 * sin(phase(it, string.frequencyHz))).toFloat() }
            val supported = detector.analyze(input, RATE) as PitchEvidence.Supported
            assertEquals(string.name, 0.0, TuningPolicy.cents(supported.pitch, string), 1.0)
        }
    }

    @Test fun recordedGuitarFixturesUseTheProductDetectorAndAbsoluteTargetPolicy() {
        val directory = System.getenv("TUNER_GUITAR_FIXTURES")
        assumeTrue("Set TUNER_GUITAR_FIXTURES to the Iowa WAV directory", directory != null)
        val reference = mapOf(StandardString.E2 to 80.3042, StandardString.A2 to 108.60821, StandardString.D3 to 144.65742,
            StandardString.G3 to 192.9161, StandardString.B3 to 244.00083, StandardString.E4 to 325.18869)
        for ((string, expectedFrequency) in reference) {
            val file = File(requireNotNull(directory), "${string.name}.wav")
            val audio = AudioSystem.getAudioInputStream(file)
            val input = audio.use {
                assertEquals(48_000f, it.format.sampleRate)
                assertEquals(1, it.format.channels)
                assertEquals(16, it.format.sampleSizeInBits)
                val bytes = it.readBytes()
                FloatArray(bytes.size / 2) { index ->
                    (((bytes[index * 2].toInt() and 255) or (bytes[index * 2 + 1].toInt() shl 8)).toShort() / 32768f)
                }
            }
            val accepted = mutableListOf<Double>()
            val rejected = mutableMapOf<PitchEvidence, Int>()
            var analysisNanos = 0L
            for (offset in RATE / 4 until RATE * 5 / 4 step 960) {
                val window = input.copyOfRange(offset, offset + SIZE)
                val started = System.nanoTime()
                val result = detector.analyze(window, RATE)
                analysisNanos += System.nanoTime() - started
                if (result is PitchEvidence.Supported) {
                    val feedback = TuningPolicy().reduce(TuningMemory(), TunerTarget.Automatic, TuningTolerance.Normal,
                        TuningInput.Observe(MonotonicNanos(0), MonotonicNanos(0), result)).feedback as TuningFeedback.Measured
                    assertEquals("Wrong accepted target in ${file.name}", string, feedback.string)
                    assertTrue("Wrong octave/frequency in ${file.name}: ${result.pitch.value}", abs(1200 * log2(result.pitch.value / expectedFrequency)) < 30.0)
                    accepted += result.pitch.value
                } else rejected[result] = rejected.getOrDefault(result, 0) + 1
            }
            println("Iowa $string rejected=$rejected")
            assertTrue("Too few confident windows in ${file.name}: ${accepted.size}", accepted.size >= 20)
            val median = accepted.sorted()[accepted.size / 2]
            assertTrue("Median differs from independent spectrum in ${file.name}: $median", abs(1200 * log2(median / expectedFrequency)) < 12.0)
            println("Iowa $string accepted=${accepted.size}/50 medianHz=$median centsFromStandard=${1200 * log2(median / string.frequencyHz)} desktopJvmMeanAnalysisMs=${analysisNanos / 50_000_000.0}")
        }
    }

    private fun tone(frequency: Double, amplitude: Double = 0.3): FloatArray =
        FloatArray(SIZE) { (amplitude * sin(phase(it, frequency))).toFloat() }
    private fun phase(index: Int, frequency: Double): Double = 2.0 * PI * frequency * index / RATE

    companion object {
        private const val RATE = 48_000
        private const val SIZE = 8192
    }
}
