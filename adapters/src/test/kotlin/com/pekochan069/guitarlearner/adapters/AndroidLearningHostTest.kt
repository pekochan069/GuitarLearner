package com.pekochan069.guitarlearner.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.*
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidLearningHostTest {
    @Test fun editsDuringAnOlderWriteRemainVisibleAndOnlyTheLatestAcknowledgmentShowsSaved() = runTest {
        val io = QueuedDispatcher()
        val rig = Rig(this, io = io)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            rig.host.current.collect { if (it.storage == LearningStorageState.Saved) assertEquals(rig.store.progress, it.progress) }
        }
        runCurrent()
        io.runNext()
        runCurrent()
        rig.host.submit(activate(LessonId.NotesIntervals))
        runCurrent()
        assertEquals(LearningStorageState.Saving, rig.host.current.value.storage)
        rig.host.submit(LearningRequest.Complete(LessonId.NotesIntervals))
        rig.host.submit(activate(LessonId.Scales))
        rig.host.submit(LearningRequest.Complete(LessonId.Scales))
        runCurrent()
        val latest = LearningProgress(setOf(LessonId.NotesIntervals, LessonId.Scales), LessonId.Scales)
        assertEquals(latest, rig.host.current.value.progress)
        assertTrue(rig.store.writes.isEmpty())
        io.runNext()
        runCurrent()
        assertEquals(LearningProgress(lastViewed = LessonId.NotesIntervals), rig.store.progress)
        assertEquals(latest, rig.host.current.value.progress)
        assertEquals(LearningStorageState.Saving, rig.host.current.value.storage)
        io.runNext()
        runCurrent()
        assertEquals(latest, rig.store.progress)
        assertEquals(LearningStorageState.Saved, rig.host.current.value.storage)
        assertEquals(2, rig.store.writes.size)
        rig.host.close()
    }

    @Test fun failedSaveRetainsCompletionAndRetryPersistsTheLatestQueuedEdits() = runTest {
        val rig = Rig(this)
        rig.open(LessonId.NotesIntervals)
        rig.store.writeResult = LearningFailure.WriteFailed.left()
        rig.host.submit(LearningRequest.Complete(LessonId.NotesIntervals))
        runCurrent()
        assertEquals(LearningStorageState.Failed(LearningFailure.WriteFailed), rig.host.current.value.storage)
        assertEquals(setOf(LessonId.NotesIntervals), rig.host.current.value.progress.completed)
        val attempted = rig.store.writes.size
        rig.host.submit(activate(LessonId.Scales))
        rig.host.submit(LearningRequest.Complete(LessonId.Scales))
        runCurrent()
        val latest = LearningProgress(setOf(LessonId.NotesIntervals, LessonId.Scales), LessonId.Scales)
        assertEquals(latest, rig.host.current.value.progress)
        assertEquals(attempted, rig.store.writes.size)
        assertEquals(LearningStorageState.Failed(LearningFailure.WriteFailed), rig.host.current.value.storage)
        rig.store.writeResult = Unit.right()
        rig.host.submit(LearningRequest.RetrySave)
        runCurrent()
        assertEquals(latest, rig.store.progress)
        assertEquals(LearningStorageState.Saved, rig.host.current.value.storage)
        rig.host.submit(LearningRequest.Complete(LessonId.Scales))
        runCurrent()
        assertEquals(attempted + 1, rig.store.writes.size)
        rig.host.close()
    }

    @Test fun readRecoveryMergesLocalCompletionsAndRetainsTheLocallyViewedLesson() = runTest {
        val rig = Rig(this, readFailure = true)
        rig.open(LessonId.NotesIntervals)
        rig.host.submit(LearningRequest.Complete(LessonId.NotesIntervals))
        rig.host.submit(activate(LessonId.Scales))
        rig.host.submit(LearningRequest.RetrySave)
        runCurrent()
        assertTrue(rig.store.writes.isEmpty())
        assertEquals(LearningStorageState.Failed(LearningFailure.ReadFailed), rig.host.current.value.storage)
        rig.store.progress = LearningProgress(setOf(LessonId.CircleOfFifths), LessonId.CircleOfFifths)
        rig.store.readFailure = false
        rig.host.submit(LearningRequest.RetryRead)
        runCurrent()
        val merged = LearningProgress(setOf(LessonId.CircleOfFifths, LessonId.NotesIntervals), LessonId.Scales)
        assertEquals(merged, rig.host.current.value.progress)
        assertEquals(merged, rig.store.progress)
        assertEquals(LearningStorageState.Saved, rig.host.current.value.storage)
        rig.host.close()
    }

    @Test fun backgroundOrDeactivateRejectsLatePreflightAndForegroundNeverAutoplays() = runTest {
        for (depart in listOf<(AndroidLearningHost) -> Unit>(
            { it.visibilityChanged(LearningVisibility.Background) },
            { it.submit(LearningRequest.Deactivate) },
        )) {
            val rig = Rig(this)
            rig.open(LessonId.Scales)
            assertTrue(rig.outputs.isEmpty())
            rig.metronome.gate = CompletableDeferred()
            rig.host.submit(LearningRequest.Listen(TrainingInstrument.Guitar))
            runCurrent()
            assertEquals(LearningAudioState.Preparing(TrainingInstrument.Guitar), rig.host.current.value.audio)
            depart(rig.host)
            rig.metronome.gate!!.complete(Unit.right())
            runCurrent()
            assertTrue(rig.outputs.isEmpty())
            assertEquals(LearningAudioState.Idle, rig.host.current.value.audio)
            assertEquals(LessonId.Scales, rig.host.current.value.progress.lastViewed)
            rig.host.visibilityChanged(LearningVisibility.Foreground)
            runCurrent()
            assertTrue(rig.outputs.isEmpty())
            rig.open(LessonId.Scales)
            rig.listen(TrainingInstrument.Guitar)
            assertEquals(8, rig.outputs.single().tone!!.steps.size)
            assertTrue(rig.outputs.single().tone!!.steps.all { it.size == 1 })
            assertEquals(TrainingInstrument.Guitar, rig.outputs.single().tone!!.instrument)
            rig.host.close()
        }
    }

    @Test fun topicAndBackgroundDepartureRevokePermittedBeforeLatePlayingOrCompletionCallbacks() = runTest {
        val rig = Rig(this)
        rig.ignoreOutputCancellation = true
        rig.open(LessonId.Scales)
        rig.listen()
        val first = rig.outputs.single()
        rig.host.submit(LearningRequest.Deactivate)
        assertFalse(first.permitted())
        first.playing!!()
        first.completion.complete(Unit.right())
        first.interrupted(TrainingFailure.PlaybackFailed)
        runCurrent()
        assertTrue(first.stopped)
        assertEquals(LearningAudioState.Idle, rig.host.current.value.audio)
        rig.open(LessonId.ChordConstruction)
        rig.listen(TrainingInstrument.Guitar)
        val second = rig.outputs.last()
        assertEquals(listOf(3), second.tone!!.steps.map { it.size })
        rig.host.visibilityChanged(LearningVisibility.ConfigurationContinuation)
        assertTrue(second.permitted())
        rig.host.visibilityChanged(LearningVisibility.Locked)
        assertFalse(second.permitted())
        second.playing!!()
        second.completion.complete(TrainingFailure.PlaybackFailed.left())
        runCurrent()
        assertEquals(LearningAudioState.Idle, rig.host.current.value.audio)
        rig.host.visibilityChanged(LearningVisibility.Foreground)
        runCurrent()
        assertEquals(2, rig.outputs.size)
        rig.listen()
        val completed = rig.outputs.last()
        completed.completion.complete(Unit.right())
        runCurrent()
        assertFalse(completed.permitted())
        completed.playing!!()
        runCurrent()
        assertEquals(LearningAudioState.Idle, rig.host.current.value.audio)
        rig.host.close()
    }

    @Test fun metronomeAndOutputFailuresOfferExplicitRetryWithoutLosingProgress() = runTest {
        val rig = Rig(this)
        rig.open(LessonId.BasicProgressions)
        rig.host.submit(LearningRequest.Complete(LessonId.BasicProgressions))
        runCurrent()
        val progress = rig.host.current.value.progress
        rig.metronome.result = MetronomeFailure.AudioUnavailable.left()
        rig.listen()
        assertTrue(rig.outputs.isEmpty())
        assertEquals(LearningAudioState.Failed(LearningFailure.MetronomeStopFailed(MetronomeFailure.AudioUnavailable)), rig.host.current.value.audio)
        assertEquals(progress, rig.host.current.value.progress)
        rig.metronome.result = Unit.right()
        rig.listen(TrainingInstrument.Guitar)
        assertEquals(listOf(3, 3, 3, 3), rig.outputs.single().tone!!.steps.map { it.size })
        val failed = rig.outputs.single()
        failed.stopResult = TrainingFailure.ShutdownFailed.left()
        failed.completion.complete(TrainingFailure.ShutdownFailed.left())
        runCurrent()
        assertEquals(LearningAudioState.Failed(LearningFailure.ShutdownFailed), rig.host.current.value.audio)
        rig.listen()
        assertEquals(1, rig.outputs.size)
        failed.stopResult = Unit.right()
        rig.listen()
        assertEquals(2, rig.outputs.size)
        rig.outputs.last().completion.complete(TrainingFailure.PlaybackFailed.left())
        runCurrent()
        assertEquals(LearningAudioState.Failed(LearningFailure.PlaybackFailed), rig.host.current.value.audio)
        assertFalse(rig.outputs.last().permitted())
        rig.outputs.last().playing!!()
        runCurrent()
        assertEquals(LearningAudioState.Failed(LearningFailure.PlaybackFailed), rig.host.current.value.audio)
        assertEquals(progress, rig.host.current.value.progress)
        rig.host.close()
    }

    @Test fun startingMetronomeInterruptsExamplesAndTechniqueLessonsNeverPretendToPlayADemonstration() = runTest {
        val rig = Rig(this)
        rig.open(LessonId.ChordConstruction)
        rig.listen()
        val first = rig.outputs.single()
        rig.metronome.current.value = MetronomeSnapshot(playback = PlaybackState.Preparing)
        runCurrent()
        assertTrue(first.stopped)
        assertEquals(LearningAudioState.Failed(LearningFailure.OutputInterrupted), rig.host.current.value.audio)
        val commands = rig.metronome.commands.size
        rig.open(LessonId.PalmMute)
        rig.listen()
        assertEquals(1, rig.outputs.size)
        assertEquals(commands, rig.metronome.commands.size)
        rig.host.close()
    }

    private fun activate(lesson: LessonId): LearningRequest.Activate = LearningRequest.Activate(LearningTarget.Lesson(lesson), LearningSelection())

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun runNext() { tasks.removeFirst().run() }
    }

    private class Store(var readFailure: Boolean) : LearningProgressStore {
        var progress = LearningProgress()
        var writeResult: Either<LearningFailure, Unit> = Unit.right()
        val writes = mutableListOf<LearningProgress>()
        override fun read(): Either<LearningFailure, LearningProgress> = if (readFailure) LearningFailure.ReadFailed.left() else progress.right()
        override fun write(progress: LearningProgress): Either<LearningFailure, Unit> {
            writes.add(progress)
            writeResult.fold({}, { this.progress = progress })
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
            val outcome = gate?.let { withContext(NonCancellable) { it.await() } } ?: result
            outcome.fold({}, { current.value = current.value.copy(playback = PlaybackState.Stopped()) })
            return outcome
        }
    }

    private class Output(
        val permitted: () -> Boolean, val interrupted: (TrainingFailure) -> Unit,
        private val ignoreCancellation: Boolean,
    ) : TrainingToneOutput {
        var tone: TrainingTone? = null
        var playing: (suspend () -> Unit)? = null
        var stopped = false
        var stopResult: Either<TrainingFailure, Unit> = Unit.right()
        val completion = CompletableDeferred<Either<TrainingFailure, Unit>>()
        override suspend fun play(tone: TrainingTone, onPlaying: suspend () -> Unit): Either<TrainingFailure, Unit> {
            if (!permitted()) return TrainingFailure.OutputInterrupted.left()
            this.tone = tone
            playing = onPlaying
            onPlaying()
            return if (ignoreCancellation) withContext(NonCancellable) { completion.await() } else completion.await()
        }
        override fun stop(): Either<TrainingFailure, Unit> { stopped = true; return stopResult }
    }

    private class Rig(private val scope: TestScope, readFailure: Boolean = false, io: CoroutineDispatcher? = null) {
        val metronome = FakeMetronome()
        val store = Store(readFailure)
        val outputs = mutableListOf<Output>()
        var ignoreOutputCancellation = false
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val host = AndroidLearningHost(scope.backgroundScope, metronome, store,
            TrainingToneOutputFactory { permitted, interrupted -> Output(permitted, interrupted, ignoreOutputCancellation).also(outputs::add) },
            dispatcher, io ?: dispatcher).also { it.visibilityChanged(LearningVisibility.Foreground) }
        fun open(lesson: LessonId) {
            host.submit(LearningRequest.Activate(LearningTarget.Lesson(lesson), LearningSelection()))
            scope.runCurrent()
        }
        fun listen(instrument: TrainingInstrument = TrainingInstrument.Piano) {
            host.submit(LearningRequest.Listen(instrument))
            scope.runCurrent()
        }
    }
}
