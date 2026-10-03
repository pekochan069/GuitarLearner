package com.pekochan069.guitarlearner.domain

import kotlin.math.roundToLong

data class ScheduledBeat(val frame: Long, val beatIndex: Int, val config: MetronomeConfig)

class MetronomeSequencer(config: MetronomeConfig, sampleRate: Int) {
    private val framesPerMinute = sampleRate.toLong() * 60
    private var config = config
    private var pendingTempo: Int? = null
    private var pendingBar: MetronomeConfig? = null
    private var beatIndex = 0
    private var wholeFrame = 0L
    private var fractionalFrame = 0.0

    init { require(sampleRate > 0) }

    val nextFrame: Long get() = wholeFrame + fractionalFrame.roundToLong()

    fun setTempo(bpm: Int) {
        require(bpm in 40..240)
        pendingTempo = bpm
        pendingBar = pendingBar?.copy(bpm = bpm)
    }

    fun setPattern(denominator: BeatUnit, beats: List<BeatAccent>) {
        val selected = pendingBar ?: config.copy(bpm = pendingTempo ?: config.bpm)
        pendingBar = selected.copy(denominator = denominator, beats = beats)
    }

    fun setConfig(config: MetronomeConfig) {
        pendingBar = config
        pendingTempo = null
    }

    fun nextBeat(): ScheduledBeat {
        if (beatIndex == 0) {
            pendingBar?.let { config = it }
            pendingBar = null
        }
        pendingTempo?.let { config = config.copy(bpm = it) }
        pendingTempo = null

        val beat = ScheduledBeat(nextFrame, beatIndex, config)
        beatIndex = (beatIndex + 1) % config.numerator
        wholeFrame += framesPerMinute / config.bpm
        fractionalFrame += (framesPerMinute % config.bpm).toDouble() / config.bpm
        val carriedFrames = fractionalFrame.toLong()
        wholeFrame += carriedFrames
        fractionalFrame -= carriedFrames
        return beat
    }

    fun reset(config: MetronomeConfig) {
        this.config = config
        pendingTempo = null
        pendingBar = null
        beatIndex = 0
        wholeFrame = 0
        fractionalFrame = 0.0
    }
}
