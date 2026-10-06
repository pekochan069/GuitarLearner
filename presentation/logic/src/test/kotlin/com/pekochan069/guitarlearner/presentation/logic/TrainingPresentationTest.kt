package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.withCompositionLocal
import arrow.core.Either
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
            IntervalPresentationUi.Descending, setOf(TrainingIntervalUi.Unison), TrainingInstrumentUi.Guitar)
        assertNull(TrainingEvent.OpenExercise(TrainingExerciseUi.TabNote).toRequest())
        assertEquals(listOf(TrainingSubjectUi.Note to TrainingRepresentationUi.Listening,
            TrainingSubjectUi.Interval to TrainingRepresentationUi.Listening, TrainingSubjectUi.Note to TrainingRepresentationUi.Staff,
            TrainingSubjectUi.Note to TrainingRepresentationUi.Fretboard, TrainingSubjectUi.Note to TrainingRepresentationUi.Tab),
            TrainingExerciseUi.entries.map { it.subject to it.format })
        assertEquals(TrainingSettings(TrainingSubject.Interval, TrainingRepresentation.Tab, IntervalPresentation.Descending,
            setOf(TrainingInterval.Unison), TrainingInstrument.Guitar), (TrainingEvent.SetSettings(changed).toRequest() as TrainingRequest.SetSettings).settings)
    }

    @Test fun trainingMenuBackAndLiveStageGuardsKeepExercisePagesSeparate() = runTest {
        val training = ControlledTraining()
        FoundationPresenter(TrainingAppearance(), TrainingMetronome(), ControlledTuner(), TrainingChords(), training).test {
            var state = awaitItem()
            assertEquals(listOf(FeatureCategory.Tools, FeatureCategory.Training), state.featureGroups.map { it.category })
            assertTrue(state.featureGroups.last().features.isEmpty())
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            assertTrue(training.requests.isEmpty())
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Training))
            runCurrent()
            expectNoEvents()
            assertEquals(FoundationDestination.Home, state.destination)
            assertEquals(TrainingStageUi.Navigation(TrainingPageUi.Root), state.training.stage)
            val menuSink = state.eventSink
            menuSink(FoundationEvent.Training(TrainingEvent.Start))
            assertTrue(training.requests.isEmpty())
            menuSink(FoundationEvent.Training(TrainingEvent.OpenExercise(TrainingExerciseUi.StaffNote)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(TrainingStageUi.Navigation(TrainingPageUi.Setup(TrainingExerciseUi.StaffNote)), state.training.stage)
            val countInSetup = training.requests.size
            menuSink(FoundationEvent.Training(TrainingEvent.OpenExercise(TrainingExerciseUi.IntervalListening)))
            assertEquals(countInSetup, training.requests.size)
            val staleSetupSink = state.eventSink
            state.eventSink(FoundationEvent.NavigateBack)
            staleSetupSink(FoundationEvent.Training(TrainingEvent.Start))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(TrainingStageUi.Navigation(TrainingPageUi.Root), state.training.stage)
            assertEquals(FoundationDestination.Home, state.destination)
            assertFalse(training.requests.contains(TrainingRequest.Start))
            state.eventSink(FoundationEvent.Training(TrainingEvent.OpenExercise(TrainingExerciseUi.IntervalListening)))
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(TrainingSubjectUi.Interval, state.training.settings.subject)
            val countBeforeInvalidSettings = training.requests.size
            staleSetupSink(FoundationEvent.Training(TrainingEvent.SetSettings(state.training.settings.copy(subject = TrainingSubjectUi.Note))))
            staleSetupSink(FoundationEvent.Training(TrainingEvent.SetSettings(state.training.settings.copy(representation = TrainingRepresentationUi.Staff))))
            assertEquals(countBeforeInvalidSettings, training.requests.size)
            val requested = training.current.value.settings.copy(instrument = TrainingInstrument.Guitar)
            training.current.value = training.current.value.copy(storage = TrainingStorageStatus.Failed(TrainingFailure.SettingsWriteFailed, requested))
            state = awaitItem()
            assertEquals(TrainingInstrumentUi.Guitar, state.training.settings.instrument)
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            runCurrent()
            expectNoEvents()
            assertFalse(training.requests.contains(TrainingRequest.Start))
            training.current.value = training.current.value.copy(settings = requested, storage = TrainingStorageStatus.Ready)
            state = awaitItem()
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            state = awaitItem()
            assertTrue(state.training.stage is TrainingStageUi.Question)
            assertEquals(TrainingInstrumentUi.Guitar, (state.training.stage as TrainingStageUi.Question).settings.instrument)
            val activeRequestCount = training.requests.size
            menuSink(FoundationEvent.Training(TrainingEvent.OpenExercise(TrainingExerciseUi.NoteListening)))
            staleSetupSink(FoundationEvent.Training(TrainingEvent.SetSettings(TrainingSettingsUi())))
            assertEquals(activeRequestCount, training.requests.size)
            state.eventSink(FoundationEvent.SetSettingsOpen(true))
            state = awaitItem()
            state.eventSink(FoundationEvent.NavigateBack)
            state = awaitItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Training), state.destination)
            assertEquals(activeRequestCount, training.requests.size)
            assertTrue(state.training.stage is TrainingStageUi.Question)
            state.eventSink(FoundationEvent.NavigateBack)
            state = awaitItem()
            assertEquals(TrainingStageUi.Navigation(TrainingPageUi.Setup(TrainingExerciseUi.IntervalListening)), state.training.stage)
            state.eventSink(FoundationEvent.NavigateBack)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(TrainingStageUi.Navigation(TrainingPageUi.Root), state.training.stage)
            assertEquals(FoundationDestination.Home, state.destination)
            state.eventSink(FoundationEvent.Training(TrainingEvent.OpenExercise(TrainingExerciseUi.NoteListening)))
            runCurrent()
            state = expectMostRecentItem()
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Metronome))
            val countAfterNavigation = training.requests.size
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            assertEquals(countAfterNavigation, training.requests.size)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(FoundationDestination.Feature(FeatureId.Metronome), state.destination)
            assertEquals(TrainingRequest.Exit, training.requests.last())
            val count = training.requests.size
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            assertEquals(count, training.requests.size)
        }
    }

    @Test fun restoredSetupRetainsRequestedSettingsOnRotationAndReconcilesFreshDurableSettingsAfterReadRetry() = runTest {
        val retained = ControlledTraining()
        val registry = SaveableStateRegistry(null) { true }
        var saved: Map<String, List<Any?>> = emptyMap()
        presenterTestOf(trainingPresenter(retained).withRegistry(registry)) {
            var state = awaitItem()
            state.eventSink(FoundationEvent.Training(TrainingEvent.OpenExercise(TrainingExerciseUi.IntervalListening)))
            runCurrent()
            state = expectMostRecentItem()
            val requested = retained.current.value.settings.copy(instrument = TrainingInstrument.Guitar)
            retained.current.value = retained.current.value.copy(storage = TrainingStorageStatus.Failed(TrainingFailure.SettingsWriteFailed, requested))
            state = awaitItem()
            assertEquals(TrainingInstrumentUi.Guitar, state.training.settings.instrument)
            saved = registry.performSave()
        }
        presenterTestOf(trainingPresenter(retained).withRegistry(SaveableStateRegistry(saved) { true })) {
            val state = awaitItem()
            assertEquals(TrainingStageUi.Navigation(TrainingPageUi.Setup(TrainingExerciseUi.IntervalListening)), state.training.stage)
            assertEquals(TrainingInstrumentUi.Guitar, state.training.settings.instrument)
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            assertFalse(retained.requests.contains(TrainingRequest.Start))
        }
        val restarted = ControlledTraining()
        val durable = TrainingSettings(representation = TrainingRepresentation.Fretboard)
        restarted.current.value = TrainingSnapshot(settings = durable,
            storage = TrainingStorageStatus.Failed(TrainingFailure.SettingsReadFailed))
        presenterTestOf(trainingPresenter(restarted).withRegistry(SaveableStateRegistry(saved) { true })) {
            var state = awaitItem()
            assertEquals(TrainingNoticeUi.SettingsReadFailed, state.training.settingsNotice)
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            assertTrue(restarted.requests.isEmpty())
            state.eventSink(FoundationEvent.Training(TrainingEvent.RetrySettings))
            assertEquals(TrainingRequest.RetrySettings, restarted.requests.single())
            restarted.current.value = TrainingSnapshot(settings = durable)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(TrainingStageUi.Navigation(TrainingPageUi.Setup(TrainingExerciseUi.FretboardNote)), state.training.stage)
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            state = awaitItem()
            val question = state.training.stage as TrainingStageUi.Question
            assertEquals(TrainingRepresentationUi.Fretboard, question.settings.representation)
            assertEquals(TrainingSubjectUi.Note, question.settings.subject)
        }
        val legacy = ControlledTraining()
        legacy.current.value = TrainingSnapshot(settings = TrainingSettings(TrainingSubject.Interval, TrainingRepresentation.Staff))
        presenterTestOf(trainingPresenter(legacy).withRegistry(SaveableStateRegistry(saved) { true })) {
            awaitItem()
            runCurrent()
            var state = expectMostRecentItem()
            assertEquals(TrainingStageUi.Navigation(TrainingPageUi.Root), state.training.stage)
            assertEquals(FoundationDestination.Home, state.destination)
            val homeRequestCount = legacy.requests.size
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            assertEquals(homeRequestCount, legacy.requests.size)
            state.eventSink(FoundationEvent.Training(TrainingEvent.OpenExercise(TrainingExerciseUi.StaffNote)))
            assertEquals(TrainingSubject.Note, legacy.current.value.settings.subject)
            runCurrent()
            state = expectMostRecentItem()
            assertEquals(TrainingStageUi.Navigation(TrainingPageUi.Setup(TrainingExerciseUi.StaffNote)), state.training.stage)
            state.eventSink(FoundationEvent.Training(TrainingEvent.Start))
            state = awaitItem()
            val question = state.training.stage as TrainingStageUi.Question
            assertEquals(TrainingSubjectUi.Note, question.settings.subject)
            assertEquals(TrainingRepresentationUi.Staff, question.settings.representation)
        }
        val oldRoot = saved.mapValues { (_, values) -> values.map { value ->
            val page = ((value as? MutableState<*>)?.value ?: value) as? List<*>
            if (page?.firstOrNull() == "setup") mutableStateOf(listOf("root")) else value
        } }
        presenterTestOf(trainingPresenter(ControlledTraining()).withRegistry(SaveableStateRegistry(oldRoot) { true })) {
            awaitItem()
            runCurrent()
            val state = expectMostRecentItem()
            assertEquals(FoundationDestination.Home, state.destination)
            assertFalse(state.canNavigateBack)
            assertEquals(TrainingStageUi.Navigation(TrainingPageUi.Root), state.training.stage)
        }
    }
}

private fun trainingPresenter(training: Training) = FoundationPresenter(TrainingAppearance(), TrainingMetronome(), ControlledTuner(), TrainingChords(), training)
private fun FoundationPresenter.withRegistry(registry: SaveableStateRegistry): @Composable () -> FoundationState =
    { withCompositionLocal(LocalSaveableStateRegistry provides registry) { present() } }

internal class ControlledTraining : Training {
    override val current = MutableStateFlow(TrainingSnapshot())
    val requests = mutableListOf<TrainingRequest>()
    override fun submit(request: TrainingRequest) {
        requests.add(request)
        when (request) {
            is TrainingRequest.SetSettings -> current.value = current.value.copy(settings = request.settings)
            TrainingRequest.Start -> current.value = current.value.copy(stage = TrainingStage.Active(
                requireNotNull(TrainingSession.start(1, current.value.settings) { 0 }.getOrNull())))
            TrainingRequest.Exit -> current.value = current.value.copy(stage = TrainingStage.Setup)
            else -> Unit
        }
    }
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
