package com.pekochan069.guitarlearner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TrainingTest {
    @Test fun everyRepresentationUsesStandardGuitarPositionsAndSelectedDistances() {
        for (representation in TrainingRepresentation.entries) {
            var draw = 0
            val choose: (Int) -> Int = { bound -> draw++ % bound }
            val notes = List(156) { TrainingRules.question(TrainingSettings(representation = representation), choose) }
            assertEquals((40..76).toSet(), notes.flatMap { it.positions }.map { it.midi }.toSet())
            assertEquals(PitchClass.entries.toSet(), notes.map { (it.answer as TrainingAnswer.Note).pitchClass }.toSet())
            for (interval in TrainingInterval.entries) {
                val settings = TrainingSettings(TrainingSubject.Interval, representation, intervals = setOf(interval))
                repeat(40) {
                    val question = TrainingRules.question(settings, choose)
                    assertEquals(TrainingAnswer.Interval(interval), question.answer)
                    assertTrue(question.positions.all { it.midi in 40..76 && it.fret in 0..12 && it.stringNumber in 1..6 })
                    assertEquals(interval.semitones, question.positions[1].midi - question.positions[0].midi)
                }
            }
        }
    }

    @Test fun knownNotesIntervalsAndOctaveEquivalenceUseOneAnswerIdentity() {
        assertEquals(TrainingAnswer.Note(PitchClass.Cs), TrainingQuestion.Note(TrainingPosition(1, 4)).answer)
        assertEquals(TrainingQuestion.Note(TrainingPosition(0, 0)).answer, TrainingQuestion.Note(TrainingPosition(5, 0)).answer)
        assertEquals(TrainingAnswer.Interval(TrainingInterval.MinorThird),
            TrainingQuestion.Interval(TrainingPosition(0, 0), TrainingPosition(0, 3)).answer)
        assertEquals(TrainingAnswer.Interval(TrainingInterval.Unison),
            TrainingQuestion.Interval(TrainingPosition(0, 5), TrainingPosition(1, 0)).answer)
        assertEquals(TrainingAnswer.Interval(TrainingInterval.Octave),
            TrainingQuestion.Interval(TrainingPosition(0, 0), TrainingPosition(0, 12)).answer)
        assertEquals(13, TrainingInterval.entries.size)
        assertEquals(6, TrainingInterval.Tritone.semitones)
    }

    @Test fun firstResponseIsFrozenAndEveryTransitionRequiresTheCurrentKey() {
        val initial = session()
        assertSame(initial, (initial.next(initial.key) as TrainingStage.Active).session)
        assertSame(initial, initial.answer(initial.key, TrainingAnswer.Interval(TrainingInterval.Unison)))
        val wrong = TrainingAnswer.Note(PitchClass.F)
        val answered = initial.answer(initial.key, wrong)
        assertFalse(requireNotNull(answered.response).correct)
        assertSame(answered, answered.answer(answered.key, answered.question.answer))
        val next = (answered.next(answered.key) as TrainingStage.Active).session
        assertEquals(1, next.index)
        assertSame(next, next.answer(answered.key, next.question.answer))
        assertSame(next, (next.next(answered.key) as TrainingStage.Active).session)
        assertSame(next, next.answer(TrainingQuestionKey(99, 1), next.question.answer))
        assertEquals(wrong, next.responses.single().chosen)
    }

    @Test fun tenResponsesRequireExplicitNextAndResultsHaveDerivedScoring() {
        var stage: TrainingStage = TrainingStage.Active(session())
        repeat(10) { index ->
            val session = (stage as TrainingStage.Active).session
            assertEquals(index, session.index)
            val answer = if (index == 3) TrainingAnswer.Note(PitchClass.F) else session.question.answer
            val answered = session.answer(session.key, answer)
            assertEquals(index + 1, answered.responses.size)
            assertEquals(index, answered.index)
            stage = answered.next(answered.key)
        }
        val results = stage as TrainingStage.Results
        assertEquals(10, results.responses.size)
        assertEquals(9, results.correctCount)
        assertFalse(results.responses[3].correct)
        assertEquals(TrainingStage.Setup, TrainingSnapshot().stage)
    }

    @Test fun emptyIntervalPoolCannotStartAndSessionSettingsCopyTheSelectedPool() {
        assertEquals(TrainingFailure.EmptyIntervalPool,
            TrainingSession.start(1, TrainingSettings(TrainingSubject.Interval, intervals = emptySet())) { 0 }.leftOrNull())
        val selected = mutableSetOf(TrainingInterval.MinorThird, TrainingInterval.PerfectFifth)
        val settings = TrainingSettings(TrainingSubject.Interval, intervals = selected)
        selected.clear()
        val started = requireNotNull(TrainingSession.start(1, settings) { 0 }.getOrNull())
        assertEquals(setOf(TrainingInterval.MinorThird, TrainingInterval.PerfectFifth), started.settings.intervals)
        repeat(10) { assertTrue(TrainingRules.question(settings) { bound -> bound - 1 }.answer ==
            TrainingAnswer.Interval(TrainingInterval.PerfectFifth)) }
        assertTrue(TrainingSession.start(1, TrainingSettings(intervals = emptySet())) { 0 }.isRight())
    }

    @Test fun completedResultsSnapshotExactlyTenResponses() {
        val question = TrainingQuestion.Note(TrainingPosition(0, 0))
        val responses = MutableList(10) { TrainingResponse(question, question.answer) }
        val results = TrainingStage.Results(TrainingSettings(), responses)
        responses.clear()
        assertEquals(10, results.responses.size)
        assertEquals(10, results.correctCount)
        assertThrows(IllegalArgumentException::class.java) { TrainingStage.Results(TrainingSettings(), emptyList()) }
    }

    private fun session(): TrainingSession = requireNotNull(TrainingSession.start(1, TrainingSettings()) { 0 }.getOrNull())
}
