package com.pekochan069.guitarlearner.adapters

import com.pekochan069.guitarlearner.domain.BeatAccent
import com.pekochan069.guitarlearner.domain.ProgressionPosition
import com.pekochan069.guitarlearner.domain.ProgressionSequencer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

internal data class ProgressionMarker(val frame: Long, val position: ProgressionPosition, val complete: Boolean = false)

internal class ProgressionPcm(val sequencer: ProgressionSequencer, private val sampleRate: Int) {
    private val preroll = sampleRate / 20L
    private var frame = -preroll
    private var endFrame: Long? = null
    private var lastPosition = ProgressionPosition()
    private val guitar = PluckedGuitar(sampleRate)
    private val normal = clickSamples(sampleRate, false)
    private val accent = clickSamples(sampleRate, true)
    private var click: ShortArray? = null
    private var clickOffset = 0
    fun render(buffer: ShortArray, size: Int, marker: (ProgressionMarker) -> Unit) {
        for (index in 0 until size) {
            if (endFrame == frame) {
                guitar.silence(); click = null
                marker(ProgressionMarker(frame + preroll, lastPosition, true))
                endFrame = Long.MIN_VALUE
            }
            if (endFrame == null && frame == sequencer.nextFrame) {
                val tick = sequencer.nextTick()
                lastPosition = tick.position
                marker(ProgressionMarker(frame + preroll, tick.position))
                if (tick.silence) guitar.silence()
                tick.attack?.let { tones ->
                    val durationFrames = tick.durationTicks.toLong() * sampleRate * 60 / (tick.position.bpm * 16)
                    guitar.strum(tones, minOf(sampleRate / 125, ((durationFrames - 1) / 5).toInt()))
                }
                tick.click?.let { click = if (it == BeatAccent.Accent) accent else normal; clickOffset = 0 }
                if (tick.complete) endFrame = sequencer.nextFrame
            }
            val value = guitar.next() + (click?.getOrNull(clickOffset)?.toDouble() ?: 0.0)
            buffer[index] = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            clickOffset++; frame++
        }
    }
}

internal class PluckedGuitar(private val sampleRate: Int) {
    private var strings = emptyList<Voice>()
    fun strum(tones: List<Int?>, spreadFrames: Int) {
        strings = tones.mapIndexedNotNull { index, midi -> midi?.let { Voice(it, index * spreadFrames, sampleRate) } }
    }
    fun silence() { strings = emptyList() }
    fun next(): Double = strings.sumOf { it.next() } * Short.MAX_VALUE * 0.16

    private class Voice(midi: Int, private var delay: Int, sampleRate: Int) {
        private val frequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
        private val count = minOf(16, (sampleRate / 2.0 / frequency).toInt())
        private val coefficient = DoubleArray(count) { 2 * cos(2 * PI * frequency * (it + 1) / sampleRate) }
        private val previous = DoubleArray(count) { -sin(2 * PI * frequency * (it + 1) / sampleRate) }
        private val current = DoubleArray(count)
        private val decay = DoubleArray(count) { exp(-1.0 / (sampleRate * 5.0 / (it + 1).toDouble().pow(0.7))) }
        private val amplitude = DoubleArray(count) { sin(PI * (it + 1) * 0.22) / (it + 1).toDouble().pow(1.3) }
        private var age = 0
        private val attackFrames = maxOf(1, sampleRate / 500)
        fun next(): Double {
            if (delay > 0) { delay--; return 0.0 }
            var sum = 0.0
            for (index in 0 until count) {
                val next = coefficient[index] * current[index] - previous[index]
                previous[index] = current[index]; current[index] = next
                amplitude[index] *= decay[index]
                sum += next * amplitude[index]
            }
            age++
            return sum * minOf(1.0, age.toDouble() / attackFrames)
        }
    }
}
