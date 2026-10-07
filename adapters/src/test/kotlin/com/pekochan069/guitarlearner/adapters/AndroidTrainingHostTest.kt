package com.pekochan069.guitarlearner.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidTrainingHostTest {
    @Test fun backgroundRevokesDelayedPreflightWithoutLosingAnAnswerOrAutoplayingOnReturn() = runTest {
        val rig = Rig(this)
        rig.metronome.gate = CompletableDeferred()
        rig.host.submit(TrainingRequest.Start)
        runCurrent()
        val session = rig.session()
        rig.host.submit(TrainingRequest.Answer(session.key, session.question.answer))
        rig.host.visibilityChanged(TrainingVisibility.ConfigurationContinuation)
        assertEquals(1, rig.session().responses.size)
        rig.host.visibilityChanged(TrainingVisibility.Background)
        rig.metronome.gate!!.complete(Unit.right())
        runCurrent()
        assertTrue(rig.outputs.isEmpty())
        assertTrue(requireNotNull(rig.session().response).correct)
        assertEquals(TrainingAudioStatus.Idle, rig.host.current.value.audio)
        rig.host.visibilityChanged(TrainingVisibility.Foreground)
        runCurrent()
        assertTrue(rig.outputs.isEmpty())
        rig.host.submit(TrainingRequest.Replay(session.key, TrainingSound.Question))
        runCurrent()
        assertEquals(1, rig.outputs.size)
    }

    @Test fun replayReplacesOutputAndComparisonIsC4EvenAfterFeedback() = runTest {
        val rig = Rig(this)
        rig.start()
        val first = rig.outputs.single()
        val session = rig.session()
        rig.host.submit(TrainingRequest.Answer(session.key, TrainingAnswer.Note(PitchClass.F)))
        rig.host.submit(TrainingRequest.Replay(session.key, TrainingSound.Comparison))
        runCurrent()
        assertTrue(first.stopped)
        assertEquals(listOf(listOf(60)), rig.outputs.last().tone!!.steps)
        assertEquals(TrainingAnswer.Note(PitchClass.F), rig.session().response!!.chosen)
        rig.host.submit(TrainingRequest.Replay(session.key, TrainingSound.Question))
        runCurrent()
        assertEquals(first.tone, rig.outputs.last().tone)
        assertEquals(3, rig.outputs.size)
        assertEquals(List(3) { MetronomeCommand.Stop }, rig.metronome.commands)
    }

    @Test fun metronomeStopFailureBlocksSoundAndRetryKeepsTheQuestionAndScore() = runTest {
        val rig = Rig(this)
        rig.metronome.result = MetronomeFailure.AudioUnavailable.left()
        rig.start()
        val session = rig.session()
        assertTrue(rig.outputs.isEmpty())
        assertEquals(TrainingAudioStatus.Failed(TrainingSound.Question,
            TrainingFailure.MetronomeStopFailed(MetronomeFailure.AudioUnavailable)), rig.host.current.value.audio)
        rig.host.submit(TrainingRequest.Answer(session.key, session.question.answer))
        rig.metronome.result = Unit.right()
        rig.host.submit(TrainingRequest.Replay(session.key, TrainingSound.Question))
        runCurrent()
        assertEquals(session.question, rig.session().question)
        assertTrue(rig.session().response!!.correct)
        assertEquals(1, rig.outputs.size)
    }

    @Test fun questionComparisonAndNextKeepTheSessionInstrumentWhenFutureSettingsChange() = runTest {
        for (instrument in TrainingInstrument.entries) {
            val rig = Rig(this)
            rig.host.submit(TrainingRequest.SetSettings(TrainingSettings(instrument = instrument)))
            runCurrent()
            rig.start()
            assertEquals(instrument, rig.outputs.single().tone!!.instrument)
            val session = rig.session()
            val other = TrainingInstrument.entries.first { it != instrument }
            rig.host.submit(TrainingRequest.SetSettings(rig.host.current.value.settings.copy(instrument = other)))
            runCurrent()
            rig.host.submit(TrainingRequest.Replay(session.key, TrainingSound.Comparison))
            runCurrent()
            assertEquals(listOf(listOf(60)), rig.outputs.last().tone!!.steps)
            assertEquals(instrument, rig.outputs.last().tone!!.instrument)
            rig.host.submit(TrainingRequest.Answer(session.key, session.question.answer))
            rig.host.submit(TrainingRequest.Next(session.key))
            runCurrent()
            assertEquals(instrument, rig.outputs.last().tone!!.instrument)
            rig.host.submit(TrainingRequest.Exit)
            rig.start()
            assertEquals(other, rig.outputs.last().tone!!.instrument)
            rig.host.close()
        }
    }

    @Test fun failedPlaybackAndFinalShutdownAreFailuresAndRetainTheOutputForRetry() = runTest {
        val rig = Rig(this)
        rig.start()
        val session = rig.session()
        rig.host.submit(TrainingRequest.Answer(session.key, session.question.answer))
        val old = rig.outputs.single()
        old.stopResult = TrainingFailure.ShutdownFailed.left()
        old.completion.complete(TrainingFailure.ShutdownFailed.left())
        runCurrent()
        assertEquals(TrainingAudioStatus.Failed(TrainingSound.Question, TrainingFailure.ShutdownFailed), rig.host.current.value.audio)
        rig.host.submit(TrainingRequest.Next(session.key))
        assertEquals(0, rig.session().index)
        assertTrue(rig.session().response!!.correct)
        assertTrue(old.stopCalls > 0)
        old.stopResult = Unit.right()
        rig.host.submit(TrainingRequest.Replay(session.key, TrainingSound.Question))
        runCurrent()
        assertEquals(2, rig.outputs.size)
        assertEquals(1, rig.session().responses.size)
        rig.outputs.last().completion.complete(TrainingFailure.PlaybackFailed.left())
        runCurrent()
        assertEquals(TrainingAudioStatus.Failed(TrainingSound.Question, TrainingFailure.PlaybackFailed), rig.host.current.value.audio)
        assertEquals(1, rig.session().responses.size)
    }

    @Test fun intervalPresentationsKeepTrainingDirectionAndSimultaneity() = runTest {
        for (presentation in IntervalPresentation.entries) {
            val rig = Rig(this)
            rig.host.submit(TrainingRequest.SetSettings(TrainingSettings(subject = TrainingSubject.Interval,
                intervalPresentation = presentation, intervals = setOf(TrainingInterval.Octave))))
            runCurrent()
            rig.start()
            val expected = when (presentation) {
                IntervalPresentation.Ascending -> listOf(listOf(40), listOf(52))
                IntervalPresentation.Descending -> listOf(listOf(52), listOf(40))
                IntervalPresentation.Harmonic -> listOf(listOf(40, 52))
            }
            assertEquals(expected, rig.outputs.single().tone!!.steps)
            rig.host.close()
        }
    }

    @Test fun latePlaybackCompletionAndMetronomeStartCannotRestoreOldAnswersOrSound() = runTest {
        val rig = Rig(this)
        rig.ignoreOutputCancellation = true
        rig.start()
        val first = rig.outputs.single()
        val session = rig.session()
        rig.host.submit(TrainingRequest.Answer(session.key, session.question.answer))
        rig.metronome.current.value = MetronomeSnapshot(playback = PlaybackState.Preparing)
        runCurrent()
        assertTrue(first.stopped)
        first.completion.complete(Unit.right())
        runCurrent()
        assertEquals(TrainingAudioStatus.Idle, rig.host.current.value.audio)
        assertTrue(rig.session().response!!.correct)
        rig.host.submit(TrainingRequest.Exit)
        assertEquals(TrainingStage.Setup, rig.host.current.value.stage)
        rig.host.submit(TrainingRequest.Answer(session.key, session.question.answer))
        rig.host.submit(TrainingRequest.Start)
        runCurrent()
        val fresh = rig.session()
        assertNotEquals(session.key, fresh.key)
        rig.host.submit(TrainingRequest.Answer(session.key, fresh.question.answer))
        assertNull(rig.session().response)
        rig.outputs.last().completion.complete(Unit.right())
        runCurrent()
    }

    @Test fun nextPlaysExactlyOnceAndResultsRemainUntilExplicitExit() = runTest {
        val rig = Rig(this)
        rig.start()
        repeat(10) {
            val session = rig.session()
            rig.host.submit(TrainingRequest.Next(session.key))
            assertEquals(it, rig.session().index)
            rig.host.submit(TrainingRequest.Answer(session.key, session.question.answer))
            rig.host.submit(TrainingRequest.Answer(session.key, TrainingAnswer.Note(PitchClass.F)))
            rig.host.submit(TrainingRequest.Next(session.key))
            rig.host.submit(TrainingRequest.Next(session.key))
            runCurrent()
        }
        val results = rig.host.current.value.stage as TrainingStage.Results
        assertEquals(10, results.correctCount)
        assertEquals(10, rig.outputs.size)
        assertTrue(rig.outputs.all { it.stopped })
        rig.host.visibilityChanged(TrainingVisibility.Background)
        rig.host.visibilityChanged(TrainingVisibility.Foreground)
        assertSame(results, rig.host.current.value.stage)
        rig.host.submit(TrainingRequest.Exit)
        assertEquals(TrainingStage.Setup, rig.host.current.value.stage)
    }

    @Test fun checkedSettingsApplyToNextSessionAndReadFailuresCannotOverwriteTheirSource() = runTest {
        val rig = Rig(this)
        rig.start()
        val original = rig.session().settings
        val requested = original.copy(representation = TrainingRepresentation.Tab)
        rig.store.writeResult = TrainingFailure.SettingsWriteFailed.left()
        rig.host.submit(TrainingRequest.SetSettings(requested))
        assertTrue(rig.host.current.value.storage is TrainingStorageStatus.Saving)
        runCurrent()
        assertEquals(original, rig.host.current.value.settings)
        assertEquals(original, rig.session().settings)
        rig.store.writeResult = Unit.right()
        rig.host.submit(TrainingRequest.RetrySettings)
        runCurrent()
        assertEquals(requested, rig.host.current.value.settings)
        assertEquals(original, rig.session().settings)
        rig.host.submit(TrainingRequest.Exit)
        rig.host.submit(TrainingRequest.Start)
        assertEquals(TrainingRepresentation.Tab, rig.session().settings.representation)
        assertEquals(1, rig.outputs.size)
        val corrupt = Rig(this, readFailure = true)
        corrupt.host.submit(TrainingRequest.SetSettings(requested))
        runCurrent()
        assertEquals(0, corrupt.store.writes)
        corrupt.store.readFailure = false
        corrupt.host.submit(TrainingRequest.RetrySettings)
        runCurrent()
        assertEquals(TrainingStorageStatus.Ready, corrupt.host.current.value.storage)
        corrupt.host.close()
        rig.host.close()
    }

    private class Store(var readFailure: Boolean) : TrainingSettingsStore {
        var settings = TrainingSettings()
        var writes = 0
        var writeResult: Either<TrainingFailure, Unit> = Unit.right()
        override fun read(): Either<TrainingFailure, TrainingSettings> = if (readFailure) TrainingFailure.SettingsReadFailed.left() else settings.right()
        override fun write(settings: TrainingSettings): Either<TrainingFailure, Unit> {
            writes++
            writeResult.fold({}, { this.settings = settings })
            return writeResult
        }
    }

    private class FakeMetronome : Metronome {
        override val current = MutableStateFlow(MetronomeSnapshot())
        var gate: CompletableDeferred<Either<MetronomeFailure, Unit>>? = null
        var result: Either<MetronomeFailure, Unit> = Unit.right()
        val commands = mutableListOf<MetronomeCommand>()
        override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> {
            commands.add(command)
            val result = gate?.let { withContext(NonCancellable) { it.await() } } ?: result
            result.fold({}, { current.value = current.value.copy(playback = PlaybackState.Stopped()) })
            return result
        }
    }

    private class Output(private val permitted: () -> Boolean, private val ignoreCancellation: Boolean) : TrainingToneOutput {
        var tone: TrainingTone? = null
        var stopped = false
        var stopCalls = 0
        var stopResult: Either<TrainingFailure, Unit> = Unit.right()
        val completion = CompletableDeferred<Either<TrainingFailure, Unit>>()
        override suspend fun play(tone: TrainingTone, onPlaying: suspend () -> Unit): Either<TrainingFailure, Unit> {
            if (!permitted()) return TrainingFailure.OutputInterrupted.left()
            this.tone = tone
            onPlaying()
            return if (ignoreCancellation) withContext(NonCancellable) { completion.await() } else completion.await()
        }
        override fun stop(): Either<TrainingFailure, Unit> { stopped = true; stopCalls++; return stopResult }
    }

    private class Rig(private val scope: TestScope, readFailure: Boolean = false) {
        val metronome = FakeMetronome()
        val store = Store(readFailure)
        val outputs = mutableListOf<Output>()
        var ignoreOutputCancellation = false
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val host = AndroidTrainingHost(scope.backgroundScope, metronome, store,
            TrainingToneOutputFactory { permitted, _ -> Output(permitted, ignoreOutputCancellation).also(outputs::add) },
            { 0 }, dispatcher, dispatcher).also { it.visibilityChanged(TrainingVisibility.Foreground) }
        fun session(): TrainingSession = (host.current.value.stage as TrainingStage.Active).session
        fun start() { host.submit(TrainingRequest.Start); scope.runCurrent() }
    }
}
