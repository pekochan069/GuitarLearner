package com.pekochan069.guitarlearner.presentation.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.withCompositionLocal
import arrow.core.Either
import com.pekochan069.guitarlearner.domain.AppearanceChange
import com.pekochan069.guitarlearner.domain.AppearanceFailure
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.AppearanceSnapshot
import com.pekochan069.guitarlearner.domain.ChordCommand
import com.pekochan069.guitarlearner.domain.ChordDraft
import com.pekochan069.guitarlearner.domain.ChordFailure
import com.pekochan069.guitarlearner.domain.ChordIdentity
import com.pekochan069.guitarlearner.domain.ChordLookup
import com.pekochan069.guitarlearner.domain.ChordQuality
import com.pekochan069.guitarlearner.domain.ChordQuery
import com.pekochan069.guitarlearner.domain.ChordShape
import com.pekochan069.guitarlearner.domain.ChordTheory
import com.pekochan069.guitarlearner.domain.ChordWorkspace
import com.pekochan069.guitarlearner.domain.Chords
import com.pekochan069.guitarlearner.domain.DraftPersistence
import com.pekochan069.guitarlearner.domain.GuitarPitch
import com.pekochan069.guitarlearner.domain.LanguagePreference
import com.pekochan069.guitarlearner.domain.Metronome
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.MetronomeFailure
import com.pekochan069.guitarlearner.domain.MetronomeSnapshot
import com.pekochan069.guitarlearner.domain.PitchClass
import com.pekochan069.guitarlearner.domain.StringStop
import com.pekochan069.guitarlearner.domain.ThemePreference
import com.pekochan069.guitarlearner.presentation.contract.ChordEvent
import com.pekochan069.guitarlearner.presentation.contract.ChordNotice
import com.pekochan069.guitarlearner.presentation.contract.ChordQualityUi
import com.pekochan069.guitarlearner.presentation.contract.ChordSection
import com.pekochan069.guitarlearner.presentation.contract.ChordStopUi
import com.pekochan069.guitarlearner.presentation.contract.FeatureId
import com.pekochan069.guitarlearner.presentation.contract.FoundationDestination
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.FoundationState
import com.pekochan069.guitarlearner.presentation.contract.TuningPresetUi
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.presenterTestOf
import com.slack.circuit.test.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChordPresentationTest {
    @Test fun invalidCapoOctaveAndFretTextKeepAcceptedMusicAndDisableSaveUntilCorrected() = runTest {
        val port = ChordTestPort()
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), port, ControlledProgressions(), ControlledTraining()).test {
            var state = awaitItem()
            assertTrue(state.chords.canSave)
            state.chord(ChordEvent.SetCapo("12x"))
            state = stateWhere { it.chords.capoInput == "12x" }
            assertTrue(state.chords.capoError)
            assertFalse(state.chords.canSave)
            assertEquals(0, state.chords.capo)
            assertEquals("C", state.chords.soundingSymbol)
            state.chord(ChordEvent.SetOctave(0, "8"))
            state = stateWhere { it.chords.strings[0].octaveInput == "8" }
            assertTrue(state.chords.strings[0].octaveError)
            assertEquals("E2", state.chords.strings[0].tuning)
            state.chord(ChordEvent.SetCapo("2"))
            state = stateWhere { it.chords.capo == 2 }
            assertEquals("D", state.chords.soundingSymbol)
            assertFalse(state.chords.canSave)
            state.chord(ChordEvent.SetOctave(0, "2"))
            state = stateWhere { it.chords.canSave }
            state.chord(ChordEvent.SetFret(1, "99"))
            state = stateWhere { it.chords.strings[1].fretInput == "99" }
            assertTrue(state.chords.strings[1].fretError)
            assertEquals(3, state.chords.strings[1].fret)
            assertFalse(state.chords.canSave)
            assertEquals("D", state.chords.soundingSymbol)
            state.chord(ChordEvent.SetFret(1, "3"))
            assertTrue(stateWhere { it.chords.canSave }.chords.canSave)
        }
    }

    @Test fun enharmonicCustomInputKeepsTheWrittenOctaveAndItsActualPitch() = runTest {
        assertEquals(GuitarPitch(PitchClass.C, 4), parseGuitarPitch("B♯", "3"))
        assertEquals(GuitarPitch(PitchClass.B, 3), parseGuitarPitch("C♭", "4"))
        assertEquals(null, parseGuitarPitch("B#", "6"))
        val port = ChordTestPort()
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), port, ControlledProgressions(), ControlledTraining()).test {
            var state = awaitItem()
            state.chord(ChordEvent.SetNote(0, "B#"))
            state = stateWhere { it.chords.strings[0].tuning == "C3" }
            state.chord(ChordEvent.SetOctave(0, "3"))
            state = stateWhere { it.chords.strings[0].tuning == "C4" }
            assertEquals("B#", state.chords.strings[0].noteInput)
            assertEquals("3", state.chords.strings[0].octaveInput)
            state.chord(ChordEvent.SetNote(0, "H"))
            state = stateWhere { it.chords.strings[0].noteInput == "H" }
            assertTrue(state.chords.strings[0].noteError)
            assertEquals("C4", state.chords.strings[0].tuning)
            assertFalse(state.chords.canSave)
            state.chord(ChordEvent.SetNote(0, "C♭"))
            state = stateWhere { it.chords.strings[0].tuning == "B2" }
            state.chord(ChordEvent.SetOctave(0, "4"))
            state = stateWhere { it.chords.strings[0].tuning == "B3" }
            assertEquals("C♭", state.chords.strings[0].noteInput)
            assertEquals("4", state.chords.strings[0].octaveInput)
            assertTrue(state.chords.canSave)
        }
    }

    @Test fun copyingSelectedLookupAndCapoChangesRetainTheInterpretationAndShapeMeaning() = runTest {
        val port = ChordTestPort()
        val shape = ChordShape(listOf(StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2),
            StringStop.Fretted(2), StringStop.Fretted(1), StringStop.Fretted(3)))
        val identity = ChordIdentity(PitchClass.A, ChordQuality.MinorSeventh)
        port.snapshot.value = port.snapshot.value.copy(lookup = ChordLookup.Ready(ChordQuery(port.snapshot.value.draft.context, identity), listOf(shape)))
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), port, ControlledProgressions(), ControlledTraining()).test {
            var state = awaitItem()
            assertEquals("Am7/C", state.chords.lookupSymbol)
            state.chord(ChordEvent.CopyRepresentative)
            state = stateWhere { it.chords.soundingSymbol == "Am7/C" }
            assertEquals(ChordSection.Edit, state.chords.section)
            assertEquals("Am7/C", state.chords.shapeSymbol)
            state.chord(ChordEvent.SetCapo("2"))
            state = stateWhere { it.chords.capo == 2 }
            assertEquals("Bm7/D", state.chords.soundingSymbol)
            assertEquals("Am7/C", state.chords.shapeSymbol)
            assertTrue(state.chords.candidates.any { it.selected && it.quality == ChordQualityUi.MinorSeventh })
            state.chord(ChordEvent.SetCapo("0"))
            state = stateWhere { it.chords.capo == 0 }
            assertEquals("Am7/C", state.chords.soundingSymbol)
            assertEquals("Am7/C", state.chords.shapeSymbol)
            assertTrue(state.chords.candidates.any { it.selected && it.quality == ChordQualityUi.MinorSeventh })
        }
    }

    @Test fun typedSaveFailureKeepsDraftAndRepeatedTapsDoNotQueueDuplicateRecordOperations() = runTest {
        val port = ChordTestPort()
        port.pending = CompletableDeferred()
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), port, ControlledProgressions(), ControlledTraining()).test {
            var state = awaitItem()
            state.chord(ChordEvent.Save)
            state.chord(ChordEvent.Save)
            state = stateWhere { it.chords.busy }
            assertFalse(state.chords.canSave)
            runCurrent()
            assertEquals(listOf(ChordCommand.SaveDraft), port.requests)
            port.pending!!.complete(Either.Left(ChordFailure.WriteFailed))
            state = stateWhere { it.chords.notice == ChordNotice.WriteFailed && !it.chords.busy }
            assertEquals("C", state.chords.soundingSymbol)
            assertEquals("Known shape", state.chords.name)
            assertTrue(state.chords.canSave)
        }
    }

    @Test fun overlappingNameEditsIgnoreTheOlderFailure() = runTest {
        val port = ChordTestPort()
        val first = CompletableDeferred<Either<ChordFailure, Unit>>()
        val latest = CompletableDeferred<Either<ChordFailure, Unit>>()
        port.pendingCommands[ChordCommand.SetName("f")] = first
        port.pendingCommands[ChordCommand.SetName("fo")] = latest
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), port, ControlledProgressions(), ControlledTraining()).test {
            var state = awaitItem()
            state.chord(ChordEvent.SetName("f"))
            state = stateWhere { it.chords.name == "f" && it.chords.canSave }
            state.chord(ChordEvent.SetName("fo"))
            state = stateWhere { it.chords.name == "fo" && it.chords.canSave }
            first.complete(Either.Left(ChordFailure.WriteFailed))
            runCurrent()
            expectNoEvents()
            latest.complete(Either.Right(Unit))
            runCurrent()
            expectNoEvents()
            assertEquals(null, state.chords.notice)
            assertEquals("fo", port.current.value.draft.name)
        }
    }

    @Test fun aContextFailureReturningAfterANewerSuccessfulEditCannotRestoreNotice() = runTest {
        val port = ChordTestPort()
        val contextResult = CompletableDeferred<Either<ChordFailure, Unit>>()
        port.pendingCommands[ChordCommand.SetCapo(2)] = contextResult
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), port, ControlledProgressions(), ControlledTraining()).test {
            var state = awaitItem()
            state.chord(ChordEvent.SetCapo("2"))
            state = stateWhere { it.chords.capo == 2 }
            port.snapshot.value = port.snapshot.value.copy(persistence = DraftPersistence.Unsynced, actionFailure = ChordFailure.WriteFailed)
            state = stateWhere { it.chords.notice == ChordNotice.WriteFailed }
            state.chord(ChordEvent.SetName("Latest"))
            state = stateWhere { it.chords.name == "Latest" && it.chords.notice == null && it.chords.canSave }
            contextResult.complete(Either.Left(ChordFailure.WriteFailed))
            runCurrent()
            expectNoEvents()
            assertEquals(null, state.chords.notice)
            assertEquals(2, state.chords.capo)
        }
    }

    @Test fun anOlderSuccessfulCommandCannotClearTheNewestFailure() = runTest {
        val port = ChordTestPort()
        val contextResult = CompletableDeferred<Either<ChordFailure, Unit>>()
        port.pendingCommands[ChordCommand.SetCapo(2)] = contextResult
        port.pendingCommands[ChordCommand.SetName("Latest")] = CompletableDeferred(Either.Left(ChordFailure.WriteFailed))
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), port, ControlledProgressions(), ControlledTraining()).test {
            var state = awaitItem()
            state.chord(ChordEvent.SetCapo("2"))
            state = stateWhere { it.chords.capo == 2 }
            state.chord(ChordEvent.SetName("Latest"))
            state = stateWhere { it.chords.notice == ChordNotice.WriteFailed }
            contextResult.complete(Either.Right(Unit))
            runCurrent()
            expectNoEvents()
            assertEquals(ChordNotice.WriteFailed, state.chords.notice)
        }
    }

    @Test fun restoredValidFieldsMatchTheAcknowledgedDraftAndTheNextSave() = runTest {
        val port = ChordTestPort()
        val acknowledged = port.current.value.draft
        port.pendingCommands[ChordCommand.SetName("Changed")] = CompletableDeferred(Either.Left(ChordFailure.WriteFailed))
        val registry = SaveableStateRegistry(null) { true }
        val presenter = FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), port, ControlledProgressions(), ControlledTraining())
        var saved: Map<String, List<Any?>> = emptyMap()
        presenterTestOf(presenter.presentWithRegistry(registry)) {
            var state = awaitItem()
            state.chord(ChordEvent.SetName("Changed"))
            state = stateWhere { it.chords.notice == ChordNotice.WriteFailed && it.chords.name == "Changed" }
            state.chord(ChordEvent.SetCapo("2"))
            state = stateWhere { it.chords.capo == 2 }
            state.chord(ChordEvent.SetNote(0, "D"))
            state = stateWhere { it.chords.strings[0].tuning == "D2" }
            state.chord(ChordEvent.SetOctave(0, "3"))
            state = stateWhere { it.chords.strings[0].tuning == "D3" }
            state.chord(ChordEvent.SetFret(1, "5"))
            state = stateWhere { it.chords.strings[1].fret == 5 }
            state.chord(ChordEvent.SetFret(4, "01"))
            stateWhere { it.chords.strings[4].fretInput == "01" }
            saved = registry.performSave()
        }
        assertTrue(saved.isNotEmpty())
        val restarted = ChordTestPort()
        val restoredRegistry = SaveableStateRegistry(saved) { true }
        val restoredPresenter = FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), restarted, ControlledProgressions(), ControlledTraining())
        presenterTestOf(restoredPresenter.presentWithRegistry(restoredRegistry)) {
            val state = awaitItem()
            assertEquals(saved.keys, restoredRegistry.performSave().keys)
            assertEquals(acknowledged.name, state.chords.name)
            assertEquals("0", state.chords.capoInput)
            assertEquals("E", state.chords.strings[0].noteInput)
            assertEquals("2", state.chords.strings[0].octaveInput)
            assertEquals("3", state.chords.strings[1].fretInput)
            assertEquals("01", state.chords.strings[4].fretInput)
            assertTrue(state.chords.canSave)
            state.chord(ChordEvent.Save)
            assertFalse(stateWhere { it.chords.busy }.chords.canSave)
            assertTrue(stateWhere { !it.chords.busy }.chords.canSave)
            assertEquals(acknowledged, restarted.savedDraft)
            assertEquals(state.chords.name, restarted.savedDraft?.name)
        }
    }

    @Test fun restorationKeepsInvalidTextAndMatchingEnharmonicPairs() = runTest {
        val port = ChordTestPort()
        port.update(port.current.value.draft.copy(context = port.current.value.draft.context.copy(
            tuning = port.current.value.draft.context.tuning.withString(1, GuitarPitch(PitchClass.C, 4)))))
        val acknowledged = port.current.value.draft
        val registry = SaveableStateRegistry(null) { true }
        val presenter = FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), port, ControlledProgressions(), ControlledTraining())
        var saved: Map<String, List<Any?>> = emptyMap()
        presenterTestOf(presenter.presentWithRegistry(registry)) {
            var state = awaitItem()
            state.chord(ChordEvent.SetNote(1, "B#"))
            state = stateWhere { it.chords.strings[1].tuning == "C5" }
            state.chord(ChordEvent.SetOctave(1, "3"))
            state = stateWhere { it.chords.strings[1].tuning == "C4" }
            state.chord(ChordEvent.SetName("x".repeat(81)))
            state.chord(ChordEvent.SetCapo("bad"))
            state.chord(ChordEvent.SetNote(0, "H"))
            state.chord(ChordEvent.SetOctave(0, "bad"))
            state.chord(ChordEvent.SetFret(0, "99"))
            stateWhere { it.chords.nameError && it.chords.strings[0].fretError }
            saved = registry.performSave()
        }
        assertTrue(saved.isNotEmpty())
        val restarted = ChordTestPort().apply { update(acknowledged) }
        val restoredRegistry = SaveableStateRegistry(saved) { true }
        val restoredPresenter = FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), restarted, ControlledProgressions(), ControlledTraining())
        presenterTestOf(restoredPresenter.presentWithRegistry(restoredRegistry)) {
            val state = awaitItem()
            assertEquals(saved.keys, restoredRegistry.performSave().keys)
            assertEquals("x".repeat(81), state.chords.name)
            assertTrue(state.chords.nameError)
            assertEquals("bad", state.chords.capoInput)
            assertTrue(state.chords.capoError)
            assertEquals("H", state.chords.strings[0].noteInput)
            assertTrue(state.chords.strings[0].noteError)
            assertEquals("bad", state.chords.strings[0].octaveInput)
            assertTrue(state.chords.strings[0].octaveError)
            assertEquals("99", state.chords.strings[0].fretInput)
            assertTrue(state.chords.strings[0].fretError)
            assertEquals(ChordStopUi.Muted, state.chords.strings[0].stop)
            assertEquals("B#", state.chords.strings[1].noteInput)
            assertEquals("3", state.chords.strings[1].octaveInput)
            assertEquals("C4", state.chords.strings[1].tuning)
            assertFalse(state.chords.canSave)
            state.chord(ChordEvent.Save)
            runCurrent()
            assertTrue(restarted.requests.isEmpty())
        }
    }

    @Test fun navigationLeavesMetronomeAloneAndEventsMapToTypedCommands() = runTest {
        val port = ChordTestPort()
        val metronome = ChordTestMetronome()
        FoundationPresenter(ChordTestAppearance(), metronome, ControlledTuner(), port, ControlledProgressions(), ControlledTraining()).test {
            var state = awaitItem()
            state.eventSink(FoundationEvent.OpenFeature(FeatureId.Chords))
            state = stateWhere { it.destination == FoundationDestination.Feature(FeatureId.Chords) }
            state.chord(ChordEvent.SetPreset(TuningPresetUi.DropD))
            state = stateWhere { it.chords.preset == TuningPresetUi.DropD }
            state.chord(ChordEvent.SetStop(0, ChordStopUi.Open))
            state = stateWhere { it.chords.strings[0].stop == ChordStopUi.Open }
            state.chord(ChordEvent.SetRoot(2))
            state = stateWhere { it.chords.root == 2 }
            state.chord(ChordEvent.SetQuality(ChordQualityUi.MinorNinth))
            state = stateWhere { it.chords.quality == ChordQualityUi.MinorNinth }
            runCurrent()
            assertTrue(port.requests.any { it is ChordCommand.SetTuning })
            assertTrue(port.requests.contains(ChordCommand.SetStop(0, StringStop.Open)))
            assertTrue(port.requests.contains(ChordCommand.Search(ChordIdentity(PitchClass.D, ChordQuality.MinorNinth))))
            assertTrue(metronome.requests.isEmpty())
            state.eventSink(FoundationEvent.NavigateBack)
            assertEquals(FoundationDestination.Home, stateWhere { it.destination == FoundationDestination.Home }.destination)
        }
    }

    @Test fun omissionLabelsAndReadFailureRemainExplicitPresentationStates() = runTest {
        val port = ChordTestPort()
        val stops = ChordShape(listOf(StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2),
            StringStop.Fretted(3), StringStop.Fretted(1), StringStop.Open))
        port.update(port.snapshot.value.draft.copy(shape = stops))
        port.snapshot.value = port.snapshot.value.copy(readFailure = ChordFailure.ReadFailed)
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), ControlledTuner(), port, ControlledProgressions(), ControlledTraining()).test {
            val state = awaitItem()
            assertTrue(state.chords.readFailed)
            assertFalse(state.chords.canSave)
            assertTrue(state.chords.candidates.any { it.quality == ChordQualityUi.Seventh && it.omitted == "5" })
        }
    }
}

private fun FoundationState.chord(event: ChordEvent) { eventSink(FoundationEvent.Chord(event)) }
private suspend fun CircuitReceiveTurbine<FoundationState>.stateWhere(predicate: (FoundationState) -> Boolean): FoundationState {
    while (true) { val state = awaitItem(); if (predicate(state)) return state }
}

private fun FoundationPresenter.presentWithRegistry(registry: SaveableStateRegistry): @Composable () -> FoundationState =
    { withCompositionLocal(LocalSaveableStateRegistry provides registry) { present() } }

private class ChordTestPort : Chords {
    val snapshot = MutableStateFlow(ChordWorkspace())
    override val current = snapshot.asStateFlow()
    val requests = mutableListOf<ChordCommand>()
    var pending: CompletableDeferred<Either<ChordFailure, Unit>>? = null
    val pendingCommands = mutableMapOf<ChordCommand, CompletableDeferred<Either<ChordFailure, Unit>>>()
    var savedDraft: ChordDraft? = null
    init {
        update(ChordDraft("Known shape", shape = ChordShape(listOf(StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2),
            StringStop.Open, StringStop.Fretted(1), StringStop.Open))))
    }

    fun update(value: ChordDraft) {
        val draft = ChordTheory.normalize(value)
        snapshot.value = snapshot.value.copy(draft = draft, analysis = ChordTheory.analyze(draft.context, draft.shape), actionFailure = null)
    }

    override suspend fun execute(command: ChordCommand): Either<ChordFailure, Unit> {
        requests += command
        if (command == ChordCommand.SaveDraft) {
            val saved = snapshot.value.draft
            return (pending?.await() ?: Either.Right(Unit)).onRight { savedDraft = saved }
        }
        val draft = snapshot.value.draft
        when (command) {
            is ChordCommand.SetName -> update(draft.copy(name = command.name))
            is ChordCommand.SetCapo -> update(draft.copy(context = draft.context.copy(capo = command.capo),
                selected = draft.selected?.let { it.copy(root = it.root.transpose(command.capo - draft.context.capo)) }))
            is ChordCommand.SetStringPitch -> update(draft.copy(context = draft.context.copy(tuning = draft.context.tuning.withString(command.index, command.pitch))))
            is ChordCommand.SetTuning -> update(draft.copy(context = draft.context.copy(tuning = command.tuning)))
            is ChordCommand.SetStop -> update(draft.copy(shape = draft.shape.withString(command.index, command.stop)))
            ChordCommand.CopyRepresentative -> {
                val lookup = snapshot.value.lookup as ChordLookup.Ready
                update(ChordDraft(context = lookup.query.context, shape = lookup.shapes[lookup.selectedIndex], selected = lookup.query.identity))
            }
            else -> Unit
        }
        return pendingCommands[command]?.await() ?: Either.Right(Unit)
    }
}

private class ChordTestAppearance : AppearanceSettings {
    override val current = MutableStateFlow(AppearanceSnapshot(ThemePreference.System, LanguagePreference.System)).asStateFlow()
    override suspend fun select(change: AppearanceChange): Either<AppearanceFailure, Unit> = Either.Right(Unit)
}

private class ChordTestMetronome : Metronome {
    override val current = MutableStateFlow(MetronomeSnapshot()).asStateFlow()
    val requests = mutableListOf<MetronomeCommand>()
    override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> {
        requests += command
        return Either.Right(Unit)
    }
}
