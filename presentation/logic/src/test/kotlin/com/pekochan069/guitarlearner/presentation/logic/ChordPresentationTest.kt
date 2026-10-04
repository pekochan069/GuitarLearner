package com.pekochan069.guitarlearner.presentation.logic

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
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), port).test {
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
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), port).test {
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
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), port).test {
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
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), port).test {
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

    @Test fun navigationLeavesMetronomeAloneAndEventsMapToTypedCommands() = runTest {
        val port = ChordTestPort()
        val metronome = ChordTestMetronome()
        FoundationPresenter(ChordTestAppearance(), metronome, port).test {
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
        FoundationPresenter(ChordTestAppearance(), ChordTestMetronome(), port).test {
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

private class ChordTestPort : Chords {
    val snapshot = MutableStateFlow(ChordWorkspace())
    override val current = snapshot.asStateFlow()
    val requests = mutableListOf<ChordCommand>()
    var pending: CompletableDeferred<Either<ChordFailure, Unit>>? = null
    init {
        update(ChordDraft("Known shape", shape = ChordShape(listOf(StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2),
            StringStop.Open, StringStop.Fretted(1), StringStop.Open))))
    }

    fun update(value: ChordDraft) {
        val draft = ChordTheory.normalize(value)
        snapshot.value = snapshot.value.copy(draft = draft, analysis = ChordTheory.analyze(draft.context, draft.shape))
    }

    override suspend fun execute(command: ChordCommand): Either<ChordFailure, Unit> {
        requests += command
        if (command == ChordCommand.SaveDraft) return pending?.await() ?: Either.Right(Unit)
        val draft = snapshot.value.draft
        when (command) {
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
        return Either.Right(Unit)
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
