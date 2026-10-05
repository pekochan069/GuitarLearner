package com.pekochan069.guitarlearner.domain

import kotlin.math.abs
import kotlin.math.log2

@JvmInline value class MonotonicNanos(val value: Long)

@JvmInline value class PitchHz private constructor(val value: Double) {
    companion object {
        fun checked(value: Double): PitchHz? = if (value.isFinite() && value > 0.0) PitchHz(value) else null
    }
}

enum class Ambiguity { WeakSignal, CompetingFundamentals, HarmonicOnly, WrongOctave, UnrecognizedPitch }

sealed interface PitchEvidence {
    data object Silence : PitchEvidence
    data class Uncertain(val reason: Ambiguity) : PitchEvidence
    data class Supported(val pitch: PitchHz) : PitchEvidence
}

enum class TuningJudgment { Low, High, Settling, InTune }

sealed interface TuningFeedback {
    data object PluckOneString : TuningFeedback
    data class Uncertain(val reason: Ambiguity) : TuningFeedback
    data class Measured(val string: StandardString, val cents: Double, val judgment: TuningJudgment) : TuningFeedback
}

data class ValidReading(val at: MonotonicNanos, val feedback: TuningFeedback.Measured)
data class Dwell(val string: StandardString, val from: MonotonicNanos, val through: MonotonicNanos)
data class TuningMemory(
    val lastValid: ValidReading? = null,
    val dwell: Dwell? = null,
    val feedback: TuningFeedback = TuningFeedback.PluckOneString,
    val lastObservation: MonotonicNanos? = null,
)

sealed interface TuningInput {
    data object Reset : TuningInput
    data class Observe(val capturedAt: MonotonicNanos, val deliveredAt: MonotonicNanos, val evidence: PitchEvidence) : TuningInput
    data class Tick(val now: MonotonicNanos) : TuningInput
}

class TuningPolicy {
    fun reduce(previous: TuningMemory, target: TunerTarget, tolerance: TuningTolerance, input: TuningInput): TuningMemory = when (input) {
        TuningInput.Reset -> TuningMemory()
        is TuningInput.Tick -> expire(previous, input.now)
        is TuningInput.Observe -> observe(previous, target, tolerance, input)
    }

    private fun observe(previous: TuningMemory, target: TunerTarget, tolerance: TuningTolerance, input: TuningInput.Observe): TuningMemory {
        if (input.capturedAt.value > input.deliveredAt.value ||
            input.deliveredAt.value - input.capturedAt.value >= EXPIRY_NANOS ||
            previous.lastObservation?.let { input.capturedAt.value <= it.value } == true
        ) return expire(previous, input.deliveredAt)

        val feedback = when (val evidence = input.evidence) {
            PitchEvidence.Silence -> TuningFeedback.PluckOneString
            is PitchEvidence.Uncertain -> TuningFeedback.Uncertain(evidence.reason)
            is PitchEvidence.Supported -> {
                val string = when (target) {
                    TunerTarget.Automatic -> StandardString.entries.minBy { abs(cents(evidence.pitch, it)) }
                    is TunerTarget.Manual -> target.string
                }
                val difference = cents(evidence.pitch, string)
                when {
                    target is TunerTarget.Automatic && abs(difference) > 180.0 -> TuningFeedback.Uncertain(Ambiguity.UnrecognizedPitch)
                    target is TunerTarget.Manual && abs(difference) >= 600.0 -> TuningFeedback.Uncertain(Ambiguity.WrongOctave)
                    else -> TuningFeedback.Measured(string, difference, when {
                        difference < -tolerance.cents - CENT_EPSILON -> TuningJudgment.Low
                        difference > tolerance.cents + CENT_EPSILON -> TuningJudgment.High
                        else -> TuningJudgment.Settling
                    })
                }
            }
        }
        if (feedback !is TuningFeedback.Measured) return TuningMemory(feedback = feedback, lastObservation = input.capturedAt)
        val dwell = if (feedback.judgment == TuningJudgment.Settling) {
            val old = previous.dwell
            if (old != null && old.string == feedback.string && input.capturedAt.value - old.through.value <= MAX_GAP_NANOS) {
                old.copy(through = input.capturedAt)
            } else Dwell(feedback.string, input.capturedAt, input.capturedAt)
        } else null
        val judged = if (dwell != null && dwell.through.value - dwell.from.value >= DWELL_NANOS) {
            feedback.copy(judgment = TuningJudgment.InTune)
        } else feedback
        return TuningMemory(ValidReading(input.capturedAt, judged), dwell, judged, input.capturedAt)
    }

    private fun expire(memory: TuningMemory, now: MonotonicNanos): TuningMemory =
        if (memory.lastValid?.let { now.value - it.at.value >= EXPIRY_NANOS } == true) {
            TuningMemory(lastObservation = memory.lastObservation)
        } else memory

    companion object {
        const val EXPIRY_NANOS: Long = 900_000_000L
        private const val DWELL_NANOS = 300_000_000L
        private const val MAX_GAP_NANOS = 100_000_000L
        private const val CENT_EPSILON = 0.000001
        fun cents(pitch: PitchHz, string: StandardString): Double = 1200.0 * log2(pitch.value / string.frequencyHz)
    }
}
