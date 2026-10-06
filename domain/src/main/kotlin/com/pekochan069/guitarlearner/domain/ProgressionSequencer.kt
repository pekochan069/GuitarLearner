package com.pekochan069.guitarlearner.domain

import kotlin.math.roundToLong

data class ProgressionTick(val frame: Long, val position: ProgressionPosition, val attack: List<Int?>? = null,
    val silence: Boolean = false, val click: BeatAccent? = null, val complete: Boolean = false, val durationTicks: Int = 0)

class ProgressionSequencer(private val content: ProgressionContent, startIndex: Int, sampleRate: Int) {
    private val frameNumerator = sampleRate.toLong() * 60
    private var bpm = content.timing.bpm
    private var enabled = content.metronomeEnabled
    private var pendingBpm: Int? = null
    private var pendingEnabled: Boolean? = null
    private var step = startIndex
    private var remaining = 0
    private var tick = 0L
    private var countIn = content.timing.numerator * (64 / content.timing.denominator.denominator)
    private val beatTicks = 64 / content.timing.denominator.denominator
    private var wholeFrame = 0L
    private var fractionalFrame = 0.0
    private var finished = false
    init { require(sampleRate > 0 && startIndex in content.steps.indices) }
    val nextFrame: Long get() = wholeFrame + fractionalFrame.roundToLong()
    fun setTempo(value: Int) { require(value in 40..240); pendingBpm = value }
    fun setMetronome(value: Boolean) { pendingEnabled = value }

    fun nextTick(): ProgressionTick {
        check(!finished)
        pendingBpm?.let { bpm = it }; pendingBpm = null
        pendingEnabled?.let { enabled = it }; pendingEnabled = null
        val frame = nextFrame
        val counting = countIn > 0
        val beat = (tick / beatTicks % content.timing.numerator).toInt()
        val click = if (tick % beatTicks == 0L && (counting || enabled)) {
            if (beat == 0) BeatAccent.Accent else BeatAccent.Normal
        } else null
        var attack: List<Int?>? = null
        var silence = false
        var duration = 0
        var complete = false
        if (!counting && remaining == 0) {
            val current = content.steps[step]
            duration = current.duration.ticks
            if (current is ProgressionStep.Chord) {
                val tied = step > 0 && (content.steps[step - 1] as? ProgressionStep.Chord)?.tieToNext == true && tick > content.timing.numerator * beatTicks
                if (!tied) attack = ChordTheory.tones(content.context, current.shape)
            } else silence = true
            remaining = duration
        }
        val position = ProgressionPosition(if (counting) -1 else step, if (counting) beat else null, bpm, enabled)
        if (counting) countIn-- else {
            remaining--
            if (remaining == 0) {
                step++
                if (step == content.steps.size) {
                    if (content.loop) step = 0 else finished = true
                }
            }
        }
        val denominator = bpm * 16
        wholeFrame += frameNumerator / denominator
        fractionalFrame += (frameNumerator % denominator).toDouble() / denominator
        val carry = fractionalFrame.toLong()
        wholeFrame += carry; fractionalFrame -= carry
        tick++
        if (finished) complete = true
        return ProgressionTick(frame, position, attack, silence, click, complete, duration)
    }
}
