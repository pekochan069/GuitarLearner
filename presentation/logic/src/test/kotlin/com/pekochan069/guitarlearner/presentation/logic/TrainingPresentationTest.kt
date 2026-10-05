package com.pekochan069.guitarlearner.presentation.logic

import arrow.core.Either
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
class TrainingPresentationTest {
    @Test fun staffUsesWrittenGuitarOctaveAndExplicitAccidentalsIncludingCancellation() {
        assertEquals(TrainingStaffNoteUi(-7, TrainingAccidentalUi.Natural), TrainingPosition(0, 0).toStaffUi())
        assertEquals(TrainingStaffNoteUi(14, TrainingAccidentalUi.Natural), TrainingPosition(5, 12).toStaffUi())
        val flat = TrainingPosition(1, 6)
        val natural = TrainingPosition(1, 7)
        assertEquals(TrainingAnswer.Interval(TrainingInterval.MinorSecond), TrainingQuestion.Interval(flat, natural).answer)
        assertEquals(TrainingStaffNoteUi(0, TrainingAccidentalUi.Flat), flat.toStaffUi())
        assertEquals(TrainingStaffNoteUi(0, TrainingAccidentalUi.Natural), natural.toStaffUi())
        val c = TrainingPosition(1, 3)
        val sharp = TrainingPosition(1, 4)
        assertEquals(TrainingStaffNoteUi(-2, TrainingAccidentalUi.Natural), c.toStaffUi())
        assertEquals(TrainingStaffNoteUi(-2, TrainingAccidentalUi.Sharp), sharp.toStaffUi())
    }

    @Test fun questionMappingHidesAnswerUntilSubmissionAndGroupsEquivalentChoices() {
        val session = requireNotNull(TrainingSession.start(17, TrainingSettings(representation = TrainingRepresentation.Staff)) { 0 }.getOrNull())
        val snapshot = TrainingSnapshot(stage = TrainingStage.Active(session))
        val question = snapshot.toUi().stage as TrainingStageUi.Question
        assertNull(question.feedback)
        assertEquals(12, question.choices.distinct().size)
        assertEquals(TrainingQuestionKeyUi(17, 0), question.key)
        assertEquals(listOf(TrainingPositionUi(6, 0)), question.positions)
        val answered = session.answer(session.key, TrainingAnswer.Note(PitchClass.F))
        val after = snapshot.copy(stage = TrainingStage.Active(answered),
            audio = TrainingAudioStatus.Failed(TrainingSound.Question, TrainingFailure.PlaybackFailed)).toUi()
        val feedback = requireNotNull((after.stage as TrainingStageUi.Question).feedback)
        assertEquals(TrainingAnswerUi.Note(TrainingNoteUi.F), feedback.chosen)
        assertEquals(TrainingAnswerUi.Note(TrainingNoteUi.E), feedback.answer)
        assertFalse(feedback.correct)
        assertEquals(TrainingAudioUi.Failed(TrainingSoundUi.Question, TrainingNoticeUi.PlaybackFailed), after.audio)
    }

    @Test fun selectedPoolAndKeyedEventsTranslateWithoutChangingIdentity() {
        val settings = TrainingSettings(TrainingSubject.Interval, intervals = setOf(TrainingInterval.Tritone, TrainingInterval.Octave))
        val session = requireNotNull(TrainingSession.start(9, settings) { 0 }.getOrNull())
        val ui = TrainingSnapshot(stage = TrainingStage.Active(session)).toUi().stage as TrainingStageUi.Question
        assertEquals(listOf(TrainingAnswerUi.Interval(TrainingIntervalUi.Tritone), TrainingAnswerUi.Interval(TrainingIntervalUi.Octave)), ui.choices)
        val key = TrainingQuestionKeyUi(9, 3)
        assertEquals(TrainingRequest.Answer(TrainingQuestionKey(9, 3), TrainingAnswer.Note(PitchClass.Cs)),
            TrainingEvent.Answer(key, TrainingAnswerUi.Note(TrainingNoteUi.CSharp)).toRequest())
        assertEquals(TrainingRequest.Next(TrainingQuestionKey(9, 3)), TrainingEvent.Next(key).toRequest())
        assertEquals(TrainingRequest.Replay(TrainingQuestionKey(9, 3), TrainingSound.Comparison),
            TrainingEvent.Replay(key, TrainingSoundUi.Comparison).toRequest())
        val changed = TrainingSettingsUi(TrainingSubjectUi.Interval, TrainingRepresentationUi.Tab,
            IntervalPresentationUi.Descending, setOf(TrainingIntervalUi.Unison))
        assertEquals(TrainingSettings(TrainingSubject.Interval, TrainingRepresentation.Tab, IntervalPresentation.Descending,
            setOf(TrainingInterval.Unison)), (TrainingEvent.SetSettings(changed).toRequest() as TrainingRequest.SetSettings).settings)
    }

    @Test fun trainingCatalogAndNavigationUseOneCapabilityAndSettingsDismissalPreservesIt() = runTest {
        val training = ControlledTraining()
        FoundationPresenter(TrainingAppearance(), TrainingMetronome(), ControlledTuner(), TrainingChords(), training).test {
            var state = awaitItem()
            assertEquals(listOf(FeatureCategory.Tools, FeatureCategory.Training), state.featureGroups.map { it.category })
            assertEquals(listOf(FeatureId.Training), state.featureGroups.last().features)
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            assertTrue(training.requests.isEmpty())
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Training))
            state = awaitItem()
            assertTrue(training.requests.isEmpty())
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            runCurrent()
            expectNoEvents()
            assertEquals(listOf(TrainingRequest.Start), training.requests)
            state.eventSink(FoundationEvent.SetSettingsOpen(true))
            state = awaitItem()
            state.eventSink(FoundationEvent.NavigateBack)
            state = awaitItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Training), state.destination)
            assertFalse(training.requests.contains(TrainingRequest.Exit))
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Metronome))
            val countAfterNavigation = training.requests.size
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            assertEquals(countAfterNavigation, training.requests.size)
            state = awaitItem()
            assertEquals(TrainingRequest.Exit, training.requests.last())
            val count = training.requests.size
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            assertEquals(count, training.requests.size)
        }
    }
}

internal class ControlledTraining : Training {
    override val current = MutableStateFlow(TrainingSnapshot())
    val requests = mutableListOf<TrainingRequest>()
    override fun submit(request: TrainingRequest) { requests.add(request) }
}
private class TrainingAppearance : AppearanceSettings {
    override val current = MutableStateFlow(AppearanceSnapshot(ThemePreference.Light, LanguagePreference.System))
    override suspend fun select(change: AppearanceChange): Either<AppearanceFailure, Unit> = Either.Right(Unit)
}
private class TrainingMetronome : Metronome {
    override val current = MutableStateFlow(MetronomeSnapshot())
    override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> = Either.Right(Unit)
}
private class TrainingChords : Chords {
    override val current = MutableStateFlow(ChordWorkspace())
    override suspend fun execute(command: ChordCommand): Either<ChordFailure, Unit> = Either.Right(Unit)
}
