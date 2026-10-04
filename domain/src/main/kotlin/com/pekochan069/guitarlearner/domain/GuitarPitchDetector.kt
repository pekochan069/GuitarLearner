package com.pekochan069.guitarlearner.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

fun interface GuitarPitchDetector {
    fun analyze(monoPcm: FloatArray, sampleRateHz: Int): PitchEvidence
}

class YinHarmonicDetector : GuitarPitchDetector {
    private val fullWindow = hannWindow(2048)

    override fun analyze(monoPcm: FloatArray, sampleRateHz: Int): PitchEvidence {
        if (sampleRateHz !in 8_000..96_000 || monoPcm.size !in 512..16_384 || monoPcm.any { !it.isFinite() }) {
            return uncertain(Ambiguity.WeakSignal)
        }
        val stride = (sampleRateHz / 12_000).coerceAtLeast(1)
        val count = (monoPcm.size / stride).coerceAtMost(2048)
        val rate = sampleRateHz.toDouble() / stride
        val maxLag = (rate / 62.0).toInt()
        if (count < maxLag * 5) return uncertain(Ambiguity.WeakSignal)
        val start = monoPcm.size - count * stride
        val samples = DoubleArray(count) { index ->
            var sum = 0.0
            repeat(stride) { sum += monoPcm[start + index * stride + it] }
            sum / stride
        }
        val highPassAlpha = rate / (rate + 2.0 * PI * 35.0)
        repeat(2) {
            var previousInput = samples[0]
            var previousOutput = 0.0
            for (index in samples.indices) {
                val value = samples[index]
                val filtered = highPassAlpha * (previousOutput + value - previousInput)
                samples[index] = filtered
                previousInput = value
                previousOutput = filtered
            }
        }
        val mean = samples.average()
        var energy = 0.0
        samples.indices.forEach { index ->
            samples[index] -= mean
            energy += samples[index] * samples[index]
        }
        energy /= count
        if (energy < 0.000001) return PitchEvidence.Silence
        if (monoPcm.count { abs(it) >= 0.98f } > monoPcm.size / 100) return uncertain(Ambiguity.WeakSignal)
        if (energy < 0.000004) return uncertain(Ambiguity.WeakSignal)

        val difference = DoubleArray(maxLag + 1)
        val comparisonSize = count - maxLag
        var cumulative = 0.0
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (index in 0 until comparisonSize) {
                val delta = samples[index] - samples[index + lag]
                sum += delta * delta
            }
            cumulative += sum
            difference[lag] = if (cumulative > 0.0) sum * lag / cumulative else 1.0
        }
        var lag = (rate / 420.0).toInt().coerceAtLeast(2)
        while (lag < maxLag - 1 && difference[lag] >= 0.16) lag++
        if (lag >= maxLag - 1) return uncertain(Ambiguity.CompetingFundamentals)
        while (lag + 1 < maxLag && difference[lag + 1] < difference[lag]) lag++
        val a = difference[lag - 1]
        val b = difference[lag]
        val c = difference[lag + 1]
        val denominator = a - 2.0 * b + c
        val interpolated = lag + if (abs(denominator) > 1e-12) (0.5 * (a - c) / denominator).coerceIn(-0.5, 0.5) else 0.0
        val periodicFrequency = rate / interpolated
        if (b > 0.16 || periodicFrequency !in 62.0..420.0) return uncertain(Ambiguity.CompetingFundamentals)

        val window = if (count == fullWindow.size) fullWindow else hannWindow(count)
        val windowSum = window.sum()
        val fundamental = peak(samples, window, windowSum, rate, periodicFrequency, periodicFrequency * 0.020)
        val frequency = fundamental.first
        val harmonics = (floor(3000.0 / frequency).toInt()).coerceIn(1, 12)
        var supportedEnergy = 0.0
        var fundamentalEnergy = 0.0
        for (harmonic in 1..harmonics) {
            val expected = harmonic * frequency
            val amplitude = if (harmonic == 1) fundamental.second else
                peak(samples, window, windowSum, rate, expected, (expected * 0.015).coerceAtMost(18.0)).second
            val power = amplitude * amplitude / 2.0
            supportedEnergy += power
            if (harmonic == 1) fundamentalEnergy = power
        }
        if (fundamentalEnergy / energy < 0.004) return uncertain(Ambiguity.HarmonicOnly)
        if (supportedEnergy / energy < 0.70) return uncertain(Ambiguity.CompetingFundamentals)
        return PitchEvidence.Supported(requireNotNull(PitchHz.checked(frequency)))
    }

    private fun peak(samples: DoubleArray, window: DoubleArray, windowSum: Double, rate: Double,
        center: Double, radius: Double): Pair<Double, Double> {
        val step = radius / 4.0
        val amplitudes = DoubleArray(9) { amplitude(samples, window, windowSum, (center + (it - 4) * step) / rate) }
        val best = amplitudes.indices.maxBy { amplitudes[it] }
        val adjustment = if (best in 1..7) {
            val a = amplitudes[best - 1]
            val b = amplitudes[best]
            val c = amplitudes[best + 1]
            val denominator = a - 2.0 * b + c
            if (abs(denominator) > 1e-12) (0.5 * (a - c) / denominator).coerceIn(-0.5, 0.5) else 0.0
        } else 0.0
        val frequency = center + (best - 4 + adjustment) * step
        return frequency to amplitude(samples, window, windowSum, frequency / rate)
    }

    private fun amplitude(samples: DoubleArray, window: DoubleArray, windowSum: Double, cyclesPerSample: Double): Double {
        val angle = 2.0 * PI * cyclesPerSample
        val rotationReal = cos(angle)
        val rotationImaginary = sin(angle)
        var real = 1.0
        var imaginary = 0.0
        var sumReal = 0.0
        var sumImaginary = 0.0
        for (index in samples.indices) {
            val value = samples[index] * window[index]
            sumReal += value * real
            sumImaginary += value * imaginary
            val nextReal = real * rotationReal - imaginary * rotationImaginary
            imaginary = imaginary * rotationReal + real * rotationImaginary
            real = nextReal
        }
        return 2.0 * sqrt(sumReal * sumReal + sumImaginary * sumImaginary) / windowSum
    }

    private fun uncertain(reason: Ambiguity): PitchEvidence = PitchEvidence.Uncertain(reason)
    private fun hannWindow(count: Int): DoubleArray = DoubleArray(count) { 0.5 - 0.5 * cos(2.0 * PI * it / (count - 1)) }
}
