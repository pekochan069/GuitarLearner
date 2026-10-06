package com.pekochan069.guitarlearner.presentation.logic

import com.pekochan069.guitarlearner.domain.*
import com.pekochan069.guitarlearner.presentation.contract.*
import com.slack.circuit.test.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearningPresentationTest {
    @Test fun topicsAndCoursesUseTheSameUnlockedLessonsAndExplorationNeedsNoCompletion() = runTest {
        val learning = ControlledLearning()
        presenter(learning).test {
            var state = awaitItem()
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Learning))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(13, state.learning.lessons.size)
            assertEquals(6, state.learning.lessons.count { it.theory })
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenLesson(LessonUi.CircleOfFifths)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LessonUi.CircleOfFifths, state.learning.lesson)
            assertEquals(12, state.learning.circle.size)
            state.eventSink(FoundationEvent.Learning(LearningEvent.Complete))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue(state.learning.completed)
            state.eventSink(FoundationEvent.Learning(LearningEvent.SetMode(LearningModeUi.Courses)))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue(state.learning.lessons.single { it.id == LessonUi.CircleOfFifths }.completed)
            assertEquals(LessonUi.CircleOfFifths, state.learning.lastViewed)
            state.eventSink(FoundationEvent.Learning(LearningEvent.SetMode(LearningModeUi.Explore)))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenConcept(LearningConceptUi.CircleOfFifths)))
            runCurrent()
            state = expectMostRecentItem()
            assertNull(state.learning.lesson)
            assertEquals(LearningConceptUi.CircleOfFifths, state.learning.concept)
            assertEquals(12, state.learning.circle.size)
            assertEquals(setOf(LessonId.CircleOfFifths), learning.current.value.progress.completed)
        }
    }

    @Test fun explicitPlaybackMapsBothInstrumentsAndEverySelectionOrPageDepartureRevokesTheExample() = runTest {
        val learning = ControlledLearning()
        presenter(learning).test {
            var state = awaitItem()
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Learning))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenLesson(LessonUi.Scales)))
            runCurrent()
            state = expectMostRecentItem()
            assertFalse(learning.requests.any { it is LearningRequest.Listen })
            state.eventSink(FoundationEvent.Learning(LearningEvent.SetRoot(state.learning.roots.indexOf("G"))))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue(state.learning.notes.any { it == LearningNoteUi("F♯", "7") })
            state.eventSink(FoundationEvent.Learning(LearningEvent.Listen))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LearningAudioUi.Playing, state.learning.audio)
            assertEquals(LearningRequest.Listen(TrainingInstrument.Piano), learning.requests.last())
            state.eventSink(FoundationEvent.Learning(LearningEvent.SetInstrument(TrainingInstrumentUi.Guitar)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LearningAudioUi.Idle, state.learning.audio)
            state.eventSink(FoundationEvent.Learning(LearningEvent.Listen))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LearningRequest.Listen(TrainingInstrument.Guitar), learning.requests.last())
            state.eventSink(FoundationEvent.Learning(LearningEvent.SetMode(LearningModeUi.Courses)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LearningAudioUi.Idle, state.learning.audio)
            val count = learning.requests.count { it is LearningRequest.Listen }
            state.eventSink(FoundationEvent.Learning(LearningEvent.Listen))
            assertEquals(count, learning.requests.count { it is LearningRequest.Listen })
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            assertEquals(FoundationDestination.Home, expectMostRecentItem().destination)
        }
    }

    @Test fun linkedTrainingKeepsItsActiveBackContractThenReturnsToTheSameCourseLessonAndSelection() = runTest {
        val learning = ControlledLearning()
        val training = ControlledTraining()
        presenter(learning, training).test {
            var state = awaitItem()
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Learning))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.SetMode(LearningModeUi.Courses)))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenLesson(LessonUi.NotesIntervals)))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.SetRoot(state.learning.roots.indexOf("D"))))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenTraining(TrainingExerciseUi.NoteListening)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Training), state.destination)
            assertTrue(state.returningToLesson)
            assertEquals(TrainingStageUi.Navigation(TrainingPageUi.Setup(TrainingExerciseUi.NoteListening)), state.training.stage)
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue(state.training.stage is TrainingStageUi.Question)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Training), state.destination)
            assertTrue(state.training.stage is TrainingStageUi.Navigation)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Learning), state.destination)
            assertEquals(LessonUi.NotesIntervals, state.learning.lesson)
            assertEquals(LearningModeUi.Courses, state.learning.mode)
            assertEquals("D", state.learning.root)
            assertFalse(state.returningToLesson)
        }
    }

    @Test fun onlyRelevantUsableLinksOpenAndNormalNavigationClearsAnOldLessonReturn() = runTest {
        val learning = ControlledLearning()
        val training = ControlledTraining()
        presenter(learning, training).test {
            var state = awaitItem()
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Learning))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenLesson(LessonUi.Strumming)))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenTool(FeatureId.Chords)))
            expectNoEvents()
            training.current.value = training.current.value.copy(storage = TrainingStorageStatus.Failed(TrainingFailure.SettingsReadFailed))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenTraining(TrainingExerciseUi.TabNote)))
            expectNoEvents()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenTool(FeatureId.Metronome)))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue(state.returningToLesson)
            state.eventSink(FoundationEvent.SetSettingsOpen(true))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Metronome), state.destination)
            assertTrue(state.returningToLesson)
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Tuner))
            runCurrent()
            state = expectMostRecentItem()
            assertFalse(state.returningToLesson)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            assertEquals(FoundationDestination.Home, expectMostRecentItem().destination)
        }
    }

    @Test fun failedSaveOrAudioRetainsTheLessonAndCompletionAndRetryUsesTheMatchingCapability() = runTest {
        val learning = ControlledLearning()
        presenter(learning).test {
            var state = awaitItem()
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Learning))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenLesson(LessonUi.ChordConstruction)))
            runCurrent()
            state = expectMostRecentItem()
            learning.current.value = learning.current.value.copy(storage = LearningStorageState.Failed(LearningFailure.WriteFailed))
            state.eventSink(FoundationEvent.Learning(LearningEvent.Complete))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue(state.learning.completed)
            assertEquals(LearningNoticeUi.WriteFailed, state.learning.saveNotice)
            assertEquals(listOf("C", "E", "G"), state.learning.notes.map { it.name })
            state.eventSink(FoundationEvent.Learning(LearningEvent.RetrySave))
            assertEquals(LearningRequest.RetrySave, learning.requests.last())
            learning.current.value = learning.current.value.copy(storage = LearningStorageState.Failed(LearningFailure.ReadFailed),
                audio = LearningAudioState.Failed(LearningFailure.FocusDenied))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue(state.learning.completed)
            assertEquals(LearningNoticeUi.FocusDenied, state.learning.audioNotice)
            state.eventSink(FoundationEvent.Learning(LearningEvent.RetrySave))
            assertEquals(LearningRequest.RetryRead, learning.requests.last())
        }
    }

    private fun presenter(learning: Learning, training: Training = ControlledTraining()) = FoundationPresenter(
        ControlledAppearance(), ControlledMetronome(), ControlledTuner(), ControlledChords(), training, learning,
    )
}

internal class ControlledLearning : Learning {
    override val current = MutableStateFlow(LearningSnapshot())
    val requests = mutableListOf<LearningRequest>()
    private var target: LearningTarget? = null
    override fun submit(request: LearningRequest) {
        requests += request
        when (request) {
            is LearningRequest.Activate -> {
                target = request.target
                val lesson = (request.target as? LearningTarget.Lesson)?.id
                current.value = current.value.copy(progress = current.value.progress.copy(lastViewed = lesson ?: current.value.progress.lastViewed),
                    audio = LearningAudioState.Idle)
            }
            is LearningRequest.Complete -> current.value = current.value.copy(progress = current.value.progress.copy(completed = current.value.progress.completed + request.lesson))
            is LearningRequest.Listen -> if (target != null) current.value = current.value.copy(audio = LearningAudioState.Playing(request.instrument))
            LearningRequest.Deactivate -> { target = null; current.value = current.value.copy(audio = LearningAudioState.Idle) }
            LearningRequest.RetryRead, LearningRequest.RetrySave -> Unit
        }
    }
}
