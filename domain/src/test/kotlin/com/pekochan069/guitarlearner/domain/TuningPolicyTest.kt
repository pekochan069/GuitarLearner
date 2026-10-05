package com.pekochan069.guitarlearner.domain

import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningPolicyTest {
    private val policy = TuningPolicy()

    @Test fun requiresContinuousThreeHundredMillisecondsAndTicksCannotEarnDwell() {
        var memory = TuningMemory()
        for (time in listOf(0L, 100L, 200L, 299L)) memory = observe(memory, time)
        assertEquals(TuningJudgment.Settling, measured(memory).judgment)
        val ticked = policy.reduce(memory, TunerTarget.Automatic, TuningTolerance.Normal, TuningInput.Tick(time(300)))
        assertEquals(TuningJudgment.Settling, measured(ticked).judgment)
        assertEquals(TuningJudgment.InTune, measured(observe(ticked, 300)).judgment)
    }

    @Test fun toleranceIsInclusiveAndHasSignedRaiseLowerFeedback() {
        for (tolerance in TuningTolerance.entries) {
            for (sign in listOf(-1, 1)) {
                val boundary = observe(TuningMemory(), 0, cents = tolerance.cents.toDouble() * sign, tolerance = tolerance)
                assertEquals(TuningJudgment.Settling, measured(boundary).judgment)
                val outside = observe(TuningMemory(), 0, cents = (tolerance.cents + 0.01) * sign, tolerance = tolerance)
                assertEquals(if (sign == -1) TuningJudgment.Low else TuningJudgment.High, measured(outside).judgment)
            }
        }
        assertEquals(TuningJudgment.High, measured(stable(4.0, TuningTolerance.Strict)).judgment)
        assertEquals(TuningJudgment.InTune, measured(stable(4.0, TuningTolerance.Normal)).judgment)
        assertEquals(TuningJudgment.InTune, measured(stable(4.0, TuningTolerance.Relaxed)).judgment)
        assertTrue(measured(observe(TuningMemory(), 0, cents = -12.0)).cents < 0.0)
    }

    @Test fun gapsUncertainSilenceOutOfToleranceAndAutomaticTargetChangesResetDwell() {
        val settled = stable()
        assertEquals(TuningJudgment.Settling, measured(observe(settled, 401)).judgment)
        for (evidence in listOf(PitchEvidence.Silence, PitchEvidence.Uncertain(Ambiguity.HarmonicOnly))) {
            val invalid = policy.reduce(settled, TunerTarget.Automatic, TuningTolerance.Normal,
                TuningInput.Observe(time(320), time(320), evidence))
            assertNull(invalid.dwell)
            assertNull(invalid.lastValid)
            assertEquals(TuningJudgment.Settling, measured(observe(invalid, 340)).judgment)
        }
        val outside = observe(settled, 320, cents = 6.0)
        assertEquals(TuningJudgment.Settling, measured(observe(outside, 340)).judgment)
        assertEquals(TuningJudgment.Settling, measured(observe(settled, 320, string = StandardString.E4)).judgment)
        val reset = policy.reduce(settled, TunerTarget.Automatic, TuningTolerance.Normal, TuningInput.Reset)
        assertEquals(TuningFeedback.PluckOneString, reset.feedback)
        assertEquals(TuningJudgment.Settling, measured(observe(reset, 400)).judgment)
    }

    @Test fun automaticStringsAreAbsoluteAndManualOctavesNeverFold() {
        for (string in StandardString.entries) assertEquals(string, measured(observe(TuningMemory(), 0, string = string)).string)
        val manual = TunerTarget.Manual(StandardString.E2)
        val wrongOctave = observe(TuningMemory(), 0, string = StandardString.E4, target = manual)
        assertEquals(TuningFeedback.Uncertain(Ambiguity.WrongOctave), wrongOctave.feedback)
        val sharp = observe(TuningMemory(), 0, cents = 12.0, target = manual)
        assertEquals(StandardString.E2, measured(sharp).string)
        assertEquals(12.0, measured(sharp).cents, 0.00001)
        assertEquals(TuningJudgment.High, measured(sharp).judgment)
    }

    @Test fun independentExpiryClearsValuesAndRejectsStaleOutOfOrderAndFutureFrames() {
        val settled = stable()
        assertEquals(TuningJudgment.InTune, measured(policy.reduce(settled, TunerTarget.Automatic, TuningTolerance.Normal,
            TuningInput.Tick(time(1199)))).judgment)
        val expired = policy.reduce(settled, TunerTarget.Automatic, TuningTolerance.Normal, TuningInput.Tick(time(1200)))
        assertEquals(TuningFeedback.PluckOneString, expired.feedback)
        assertNull(expired.dwell)
        assertNull(expired.lastValid)
        val supported = PitchEvidence.Supported(requireNotNull(PitchHz.checked(82.406889)))
        for ((capture, delivery) in listOf(300L to 1200L, 200L to 400L, 450L to 400L)) {
            val result = policy.reduce(expired, TunerTarget.Automatic, TuningTolerance.Normal,
                TuningInput.Observe(time(capture), time(delivery), supported))
            assertEquals(TuningFeedback.PluckOneString, result.feedback)
        }
    }

    @Test fun invalidFrequenciesCannotBecomeEvidence() {
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertNull(PitchHz.checked(value))
    }

    private fun stable(cents: Double = 0.0, tolerance: TuningTolerance = TuningTolerance.Normal): TuningMemory {
        var memory = TuningMemory()
        for (time in listOf(0L, 100L, 200L, 300L)) memory = observe(memory, time, cents = cents, tolerance = tolerance)
        return memory
    }

    private fun observe(memory: TuningMemory, millis: Long, string: StandardString = StandardString.E2, cents: Double = 0.0,
        target: TunerTarget = TunerTarget.Automatic, tolerance: TuningTolerance = TuningTolerance.Normal): TuningMemory =
        policy.reduce(memory, target, tolerance, TuningInput.Observe(time(millis), time(millis),
            PitchEvidence.Supported(requireNotNull(PitchHz.checked(string.frequencyHz * 2.0.pow(cents / 1200.0))))))

    private fun time(millis: Long): MonotonicNanos = MonotonicNanos(millis * 1_000_000)
    private fun measured(memory: TuningMemory): TuningFeedback.Measured = memory.feedback as TuningFeedback.Measured
}
