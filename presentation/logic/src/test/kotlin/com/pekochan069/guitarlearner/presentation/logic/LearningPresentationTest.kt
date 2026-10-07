package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.withCompositionLocal
import com.pekochan069.guitarlearner.domain.*
import com.pekochan069.guitarlearner.presentation.contract.*
import com.slack.circuit.test.test
import com.slack.circuit.test.presenterTestOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearningPresentationTest {
    @Test fun homeEntriesShareCompletionAndCoursesGuideAnyLessonBackThroughItsOverview() = runTest {
        val learning = ControlledLearning()
        presenter(learning).test {
            var state = awaitItem()
            assertEquals(listOf(FeatureId.Learning, FeatureId.LearningCourses), state.featureGroups.single { it.category == FeatureCategory.Learning }.features)
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Learning))
            runCurrent()
            state = expectMostRecentItem()
            val topics = state.learning.page as LearningUiPage.Topics
            assertEquals(13, topics.lessons.size)
            assertEquals(6, topics.lessons.count { it.theory })
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenLesson(LessonUi.CircleOfFifths)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LearningUiPage.Lesson(LessonUi.CircleOfFifths), state.learning.page)
            assertEquals(12, state.learning.circle.size)
            state.eventSink(FoundationEvent.Learning(LearningEvent.Complete))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue((state.learning.page as LearningUiPage.Lesson).completed)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertTrue((state.learning.page as LearningUiPage.Topics).lessons.single { it.id == LessonUi.CircleOfFifths }.completed)
            assertEquals(LessonUi.CircleOfFifths, (state.learning.page as LearningUiPage.Topics).lastViewed)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(FoundationDestination.Home, state.destination)
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.LearningCourses))
            runCurrent()
            state = expectMostRecentItem()
            val courses = (state.learning.page as LearningUiPage.Courses).courses
            assertEquals(listOf(CourseUi.Theory, CourseUi.Technique), courses.map { it.id })
            assertEquals(listOf(6, 7), courses.map { it.total })
            assertEquals(listOf(1, 0), courses.map { it.completed })
            assertEquals(listOf(LessonUi.NotesIntervals, LessonUi.Strumming), courses.map { it.entryLesson })
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenCourse(CourseUi.Theory)))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenLesson(LessonUi.PalmMute)))
            expectNoEvents()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenLesson(LessonUi.CircleOfFifths)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LearningUiPage.Lesson(LessonUi.CircleOfFifths, true,
                LearningLessonContextUi.Course(CourseUi.Theory, 6, 6, LessonUi.BasicProgressions, null)), state.learning.page)
            assertEquals(12, state.learning.circle.size)
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenCourse(CourseUi.Theory)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(1, (state.learning.page as LearningUiPage.CourseOverview).course.completed)
            state.eventSink(FoundationEvent.Learning(LearningEvent.ContinueCourse(CourseUi.Theory)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LessonUi.NotesIntervals, (state.learning.page as LearningUiPage.Lesson).id)
            state.eventSink(FoundationEvent.Learning(LearningEvent.Complete))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue((state.learning.page as LearningUiPage.Lesson).completed)
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenLesson(LessonUi.Scales)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LearningLessonContextUi.Course(CourseUi.Theory, 2, 6, LessonUi.NotesIntervals, LessonUi.ChordConstruction),
                (state.learning.page as LearningUiPage.Lesson).context)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(2, (state.learning.page as LearningUiPage.CourseOverview).course.completed)
            assertEquals(LessonUi.Scales, (state.learning.page as LearningUiPage.CourseOverview).course.entryLesson)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertTrue(state.learning.page is LearningUiPage.Courses)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            assertEquals(FoundationDestination.Home, expectMostRecentItem().destination)
            assertEquals(setOf(LessonId.NotesIntervals, LessonId.CircleOfFifths), learning.current.value.progress.completed)
        }
    }

    @Test fun continueReadsLiveCompletionAndAllCompleteCoursesReviewTheFirstLesson() = runTest {
        val learning = ControlledLearning()
        presenter(learning).test {
            var state = awaitItem()
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.LearningCourses))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LessonUi.NotesIntervals, (state.learning.page as LearningUiPage.Courses).courses.first().entryLesson)
            learning.current.value = learning.current.value.copy(progress = LearningProgress(setOf(LessonId.NotesIntervals)))
            state.eventSink(FoundationEvent.Learning(LearningEvent.ContinueCourse(CourseUi.Theory)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LearningUiPage.Lesson(LessonUi.Scales, false,
                LearningLessonContextUi.Course(CourseUi.Theory, 2, 6, LessonUi.NotesIntervals, LessonUi.ChordConstruction)), state.learning.page)
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenLesson(LessonUi.CircleOfFifths)))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.Complete))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LessonUi.Scales, (state.learning.page as LearningUiPage.CourseOverview).course.entryLesson)
            assertEquals(2, (state.learning.page as LearningUiPage.CourseOverview).course.completed)
            learning.current.value = learning.current.value.copy(progress = LearningProgress(setOf(LessonId.NotesIntervals, LessonId.Scales,
                LessonId.ChordConstruction, LessonId.DiatonicFunctions, LessonId.BasicProgressions, LessonId.CircleOfFifths)))
            runCurrent()
            state = expectMostRecentItem()
            val theory = (state.learning.page as LearningUiPage.CourseOverview).course
            assertEquals(6, theory.completed)
            assertTrue(theory.reviewing)
            assertEquals(LessonUi.NotesIntervals, theory.entryLesson)
            state.eventSink(FoundationEvent.Learning(LearningEvent.ContinueCourse(CourseUi.Theory)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LessonUi.NotesIntervals, (state.learning.page as LearningUiPage.Lesson).id)
            assertTrue((state.learning.page as LearningUiPage.Lesson).completed)
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.LearningCourses))
            runCurrent()
            state = expectMostRecentItem()
            val technique = (state.learning.page as LearningUiPage.Courses).courses.single { it.id == CourseUi.Technique }
            assertEquals(0, technique.completed)
            assertEquals(7, technique.total)
            assertFalse(technique.reviewing)
            assertEquals(LessonUi.Strumming, technique.entryLesson)
            state.eventSink(FoundationEvent.Learning(LearningEvent.ContinueCourse(CourseUi.Technique)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LearningUiPage.Lesson(LessonUi.Strumming, false,
                LearningLessonContextUi.Course(CourseUi.Technique, 1, 7, null, LessonUi.AlternatePicking)), state.learning.page)
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
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.LearningCourses))
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
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.LearningCourses))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenCourse(CourseUi.Theory)))
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
            assertEquals(FoundationDestination.Feature(FeatureId.LearningCourses), state.destination)
            assertEquals(LearningUiPage.Lesson(LessonUi.NotesIntervals, false,
                LearningLessonContextUi.Course(CourseUi.Theory, 1, 6, null, LessonUi.Scales)), state.learning.page)
            assertEquals("D", state.learning.root)
            assertFalse(state.returningToLesson)
        }
    }

    @Test fun onlyRelevantLinksKeepReturnOnNotificationReopenAndNormalDepartureClearsIt() = runTest {
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
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Metronome))
            state.eventSink(FoundationEvent.SetSettingsOpen(true))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue(state.returningToLesson)
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
            assertTrue((state.learning.page as LearningUiPage.Lesson).completed)
            assertEquals(LearningNoticeUi.WriteFailed, state.learning.saveNotice)
            assertEquals(listOf("C", "E", "G"), state.learning.notes.map { it.name })
            state.eventSink(FoundationEvent.Learning(LearningEvent.RetrySave))
            assertEquals(LearningRequest.RetrySave, learning.requests.last())
            learning.current.value = learning.current.value.copy(storage = LearningStorageState.Failed(LearningFailure.ReadFailed),
                audio = LearningAudioState.Failed(LearningFailure.FocusDenied))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue((state.learning.page as LearningUiPage.Lesson).completed)
            assertEquals(LearningNoticeUi.FocusDenied, state.learning.audioNotice)
            state.eventSink(FoundationEvent.Learning(LearningEvent.RetrySave))
            assertEquals(LearningRequest.RetryRead, learning.requests.last())
        }
    }

    @Test fun basicProgressionsNotificationReopenReturnsToItsCourseSelectionWithoutEditingTheToolDraft() = runTest {
        val learning = ControlledLearning()
        val progressions = ControlledProgressions()
        val original = progressions.current.value
        val registry = SaveableStateRegistry(null) { true }
        var saved: Map<String, List<Any?>> = emptyMap()
        presenterTestOf(presenter(learning, progressions = progressions).withLearningRegistry(registry)) {
            var state = awaitItem()
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.LearningCourses))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenCourse(CourseUi.Theory)))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenLesson(LessonUi.BasicProgressions)))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.SetRoot(state.learning.roots.indexOf("G"))))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.SetProgression(LearningProgressionUi.TwoFiveOne)))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.SetInstrument(TrainingInstrumentUi.Guitar)))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.Learning(LearningEvent.Listen))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(LearningAudioUi.Playing, state.learning.audio)
            assertTrue(FeatureId.Progressions in state.learning.toolLinks)
            state.eventSink(FoundationEvent.Learning(LearningEvent.OpenTool(FeatureId.Progressions)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Progressions), state.destination)
            assertTrue(state.returningToLesson)
            assertEquals(LearningAudioUi.Idle, state.learning.audio)
            saved = registry.performSave()
        }
        presenterTestOf(presenter(learning, progressions = progressions).withLearningRegistry(SaveableStateRegistry(saved) { true })) {
            var state = awaitItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Progressions), state.destination)
            assertTrue(state.returningToLesson)
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Progressions))
            state.eventSink(FoundationEvent.SetSettingsOpen(true))
            runCurrent()
            state = expectMostRecentItem()
            assertTrue(state.returningToLesson)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Progressions), state.destination)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(FoundationDestination.Feature(FeatureId.LearningCourses), state.destination)
            assertEquals(LearningUiPage.Lesson(LessonUi.BasicProgressions, false,
                LearningLessonContextUi.Course(CourseUi.Theory, 5, 6, LessonUi.DiatonicFunctions, LessonUi.CircleOfFifths)), state.learning.page)
            assertEquals("G", state.learning.root)
            assertEquals(LearningProgressionUi.TwoFiveOne, state.learning.progression)
            assertEquals(TrainingInstrumentUi.Guitar, state.learning.instrument)
            assertFalse(state.returningToLesson)
            assertEquals(original, progressions.current.value)
            assertTrue(progressions.commands.isEmpty())
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(CourseUi.Theory, (state.learning.page as LearningUiPage.CourseOverview).course.id)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertTrue(state.learning.page is LearningUiPage.Courses)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            assertEquals(FoundationDestination.Home, expectMostRecentItem().destination)
        }
    }

    @Test fun legacyPagesMapToTypedTopicsAndCoursesAndInvalidValuesFallBackToTopics() {
        val expected = listOf(
            listOf("catalog", "Topics") to LearningPage.Topics,
            listOf("catalog", "Courses") to LearningPage.Courses,
            listOf("catalog", "Explore") to LearningPage.Topics,
            listOf("lesson", "scales", "Topics") to LearningPage.Lesson(LessonId.Scales, LessonOrigin.Topics),
            listOf("lesson", "palm_mute", "Courses") to LearningPage.Lesson(LessonId.PalmMute, LessonOrigin.Course),
            listOf("course", "Technique") to LearningPage.CourseOverview(LessonFamily.Technique),
            listOf("lesson", "basic_progressions", "Course") to LearningPage.Lesson(LessonId.BasicProgressions, LessonOrigin.Course),
            listOf("exploration", "CircleOfFifths") to LearningPage.Lesson(LessonId.CircleOfFifths, LessonOrigin.Topics),
            listOf("exploration", "ChordConstruction") to LearningPage.Lesson(LessonId.ChordConstruction, LessonOrigin.Topics),
        )
        expected.forEach { (saved, page) -> assertEquals(page, LearningPageSaver.restore(saved)) }
        listOf<Any>("unknown", emptyList<String>(), listOf("lesson", "unknown", "Course"), listOf("lesson", "scales", "Explore"),
            listOf("course", "missing"), listOf("exploration", 42)).forEach { assertEquals(LearningPage.Topics, LearningPageSaver.restore(it)) }
        val courseLesson = LearningPage.Lesson(LessonId.PalmMute, LessonOrigin.Course)
        assertEquals(LearningPage.CourseOverview(LessonFamily.Technique), courseLesson.parent)
        assertEquals(LearningPage.Courses, courseLesson.parent?.parent)
        assertEquals(FeatureId.LearningCourses, courseLesson.feature)
        for (feature in listOf(FeatureId.Learning, FeatureId.LearningCourses)) {
            assertNull(LinkedLessonReturnSaver.restore(listOf(listOf("lesson", "palm_mute", "Courses"), emptyList<String>(), "Guitar", feature.name)))
        }
    }

    @Test fun legacySavedDestinationUsesTheRestoredPageForEventGuardsPlaybackAndBack() = runTest {
        val registry = SaveableStateRegistry(null) { true }
        var saved: Map<String, List<Any?>> = emptyMap()
        presenterTestOf(presenter(ControlledLearning()).withLearningRegistry(registry)) {
            awaitItem()
            saved = registry.performSave()
        }
        for ((destination, payload, course) in listOf(
            Triple("feature:learning", listOf("lesson", "circle_of_fifths", "Courses"), true),
            Triple("feature:learning_courses", listOf("exploration", "CircleOfFifths"), false),
        )) {
            val legacy = saved.mapValues { (_, values) -> values.map { value ->
                val unwrapped = (value as? MutableState<*>)?.value ?: value
                when (unwrapped) {
                    "home" -> replaceLearningSavedValue(value, destination)
                    listOf("topics") -> replaceLearningSavedValue(value, payload)
                    else -> value
                }
            } }
            val learning = ControlledLearning()
            presenterTestOf(presenter(learning).withLearningRegistry(SaveableStateRegistry(legacy) { true })) {
                var state = awaitItem()
                runCurrent()
                state = expectMostRecentItem()
                assertEquals(FoundationDestination.Feature(if (course) FeatureId.LearningCourses else FeatureId.Learning), state.destination)
                assertEquals(LessonUi.CircleOfFifths, (state.learning.page as LearningUiPage.Lesson).id)
                assertEquals(12, state.learning.circle.size)
                assertEquals(LearningAudioUi.Idle, state.learning.audio)
                assertFalse(learning.requests.any { it is LearningRequest.Listen })
                state.eventSink(FoundationEvent.Learning(LearningEvent.Complete))
                runCurrent()
                state = expectMostRecentItem()
                assertTrue((state.learning.page as LearningUiPage.Lesson).completed)
                state.eventSink(FoundationEvent.Learning(LearningEvent.Listen))
                runCurrent()
                state = expectMostRecentItem()
                assertEquals(LearningAudioUi.Playing, state.learning.audio)
                state.eventSink(FoundationEvent.NavigateBack)
                runCurrent()
                state = expectMostRecentItem()
                assertEquals(LearningAudioUi.Idle, state.learning.audio)
                if (course) {
                    assertEquals(1, (state.learning.page as LearningUiPage.CourseOverview).course.completed)
                    state.eventSink(FoundationEvent.NavigateBack)
                    runCurrent()
                    state = expectMostRecentItem()
                    assertTrue(state.learning.page is LearningUiPage.Courses)
                } else assertTrue(state.learning.page is LearningUiPage.Topics)
                state.eventSink(FoundationEvent.NavigateBack)
                runCurrent()
                assertEquals(FoundationDestination.Home, expectMostRecentItem().destination)
            }
        }
    }

    private fun presenter(learning: Learning, training: Training = ControlledTraining(), progressions: Progressions = ControlledProgressions()) = FoundationPresenter(
        ControlledAppearance(), ControlledMetronome(), ControlledTuner(), ControlledChords(), progressions, training, learning,
    )
}

private fun FoundationPresenter.withLearningRegistry(registry: SaveableStateRegistry): @Composable () -> FoundationState =
    { withCompositionLocal(LocalSaveableStateRegistry provides registry) { present() } }

private fun replaceLearningSavedValue(previous: Any?, replacement: Any): Any =
    if (previous is MutableState<*>) mutableStateOf(replacement) else replacement

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
