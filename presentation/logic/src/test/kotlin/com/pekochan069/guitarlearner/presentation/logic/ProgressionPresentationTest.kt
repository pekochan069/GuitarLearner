package com.pekochan069.guitarlearner.presentation.logic

import arrow.core.Either
import com.pekochan069.guitarlearner.domain.*
import com.pekochan069.guitarlearner.presentation.contract.*
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressionPresentationTest {
    private val shape = ChordShape(listOf(StringStop.Muted, StringStop.Fretted(3), StringStop.Fretted(2), StringStop.Open, StringStop.Fretted(1), StringStop.Open))
    @Test fun openingAnExistingChordEditorStopsBeforeLocalShapeChanges() = runTest {
        val port = ControlledProgressions()
        val original = ProgressionDraft("Practice", ProgressionContent(steps = listOf(ProgressionStep.Chord("C", shape))))
        port.current.value = ProgressionWorkspace(draft = original, playback = ProgressionPlayback.Playing(ProgressionPosition(0)))
        port.onCommand = { if (it == ProgressionCommand.Stop) port.current.value = port.current.value.copy(playback = ProgressionPlayback.Stopped()) }
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.OpenEditor(0))
            state = stateWhere { it.progressions.sheet == ProgressionSheetUi.Chord && it.progressions.transport == ProgressionTransportUi.Stopped }
            state.progression(ProgressionEvent.ChordInput(ChordEvent.SetFret(5, "3")))
            stateWhere { it.progressions.editor.strings[5].fret == 3 }
            assertEquals(listOf(ProgressionCommand.Stop), port.commands)
            assertEquals(original, port.current.value.draft)
        }
    }
    @Test fun failedLoadAndNewShowPublishedContentInsteadOfOldValidRawInputs() = runTest {
        val port = ControlledProgressions()
        val old = ProgressionContent(context = GuitarContext(capo = 5), timing = MetronomeConfig(bpm = 140), steps = listOf(ProgressionStep.Rest()))
        val loaded = ProgressionContent(context = GuitarContext(TuningPreset.DropD.tuning, 2),
            timing = MetronomeConfig(80, BeatUnit.Eighth, List(6) { BeatAccent.Normal }), steps = listOf(ProgressionStep.Rest()))
        val record = SavedProgression("saved", "Loaded", loaded)
        port.current.value = ProgressionWorkspace(draft = ProgressionDraft("Old", old), records = listOf(record))
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.SetTempo("0140"))
            state = stateWhere { it.progressions.bpmInput == "0140" }
            state.progression(ProgressionEvent.SetNumerator("04"))
            state = stateWhere { it.progressions.numeratorInput == "04" }
            state.progression(ProgressionEvent.ChordInput(ChordEvent.SetCapo("05")))
            state = stateWhere { it.progressions.editor.capoInput == "05" }
            state.progression(ProgressionEvent.ChordInput(ChordEvent.SetNote(0, "e")))
            state = stateWhere { it.progressions.editor.strings[0].noteInput == "e" }
            state.progression(ProgressionEvent.ChordInput(ChordEvent.SetOctave(0, "02")))
            state = stateWhere { it.progressions.editor.strings[0].octaveInput == "02" }
            port.result = Either.Left(ProgressionFailure.WriteFailed)
            port.onCommand = { command ->
                if (command is ProgressionCommand.Load) port.current.value = port.current.value.copy(
                    draft = ProgressionDraft(record.name, record.content, record.id), persistence = DraftPersistence.Unsynced)
                if (command == ProgressionCommand.NewDraft) port.current.value = port.current.value.copy(
                    draft = ProgressionDraft(content = ProgressionContent(context = loaded.context)), persistence = DraftPersistence.Unsynced)
            }
            state.progression(ProgressionEvent.Load(record.id))
            state = stateWhere { it.progressions.replacement is ProgressionReplacementUi.Load }
            state.progression(ProgressionEvent.ConfirmReplacement)
            state = stateWhere { it.progressions.name == "Loaded" && !it.progressions.busy }
            assertEquals("80", state.progressions.bpmInput)
            assertEquals("6", state.progressions.numeratorInput)
            assertEquals("2", state.progressions.editor.capoInput)
            assertEquals("D", state.progressions.editor.strings[0].noteInput)
            assertEquals("2", state.progressions.editor.strings[0].octaveInput)
            assertTrue(state.progressions.canSave)
            assertTrue(state.progressions.unsynced)
            assertEquals(ProgressionNotice.WriteFailed, state.progressions.notice)
            state.progression(ProgressionEvent.SetTempo("0080"))
            state = stateWhere { it.progressions.bpmInput == "0080" }
            state.progression(ProgressionEvent.NewDraft)
            state = stateWhere { it.progressions.replacement == ProgressionReplacementUi.NewDraft }
            state.progression(ProgressionEvent.ConfirmReplacement)
            state = stateWhere { it.progressions.steps.isEmpty() }
            assertEquals("90", state.progressions.bpmInput)
            assertEquals("4", state.progressions.numeratorInput)
            assertEquals("2", state.progressions.editor.capoInput)
            assertFalse(state.progressions.canSave)
            assertTrue(state.progressions.unsynced)
        }
    }
    @Test fun copiedShapesUseProgressionContextAndNeverMutateTheChordViewer() = runTest {
        val port = ControlledProgressions()
        port.current.value = ProgressionWorkspace(draft = ProgressionDraft(content = ProgressionContent(context = GuitarContext(capo = 2))))
        val chords = ProgressionSourceChords(ChordDraft("Viewer C", GuitarContext(capo = 7), shape))
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), chords, port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.OpenEditor())
            state = stateWhere { it.progressions.sheet == ProgressionSheetUi.Chord }
            state.progression(ProgressionEvent.CopyCurrentChord)
            state = stateWhere { it.progressions.editor.name == "Viewer C" && it.progressions.chordSource == ProgressionChordSourceUi.Manual }
            assertEquals("D", state.progressions.editor.soundingSymbol)
            assertEquals("C", state.progressions.editor.shapeSymbol)
            state.progression(ProgressionEvent.ChordInput(ChordEvent.SetFret(5, "3")))
            state = stateWhere { it.progressions.editor.strings[5].fret == 3 }
            state.progression(ProgressionEvent.CommitEditor)
            stateWhere { it.progressions.sheet == ProgressionSheetUi.None && it.progressions.editor.lookup == ChordLookupUi.Idle && !it.progressions.busy }
            val inserted = (port.commands.single() as ProgressionCommand.Insert).step as ProgressionStep.Chord
            assertEquals("Viewer C", inserted.name)
            assertEquals(StringStop.Fretted(3), inserted.shape.stops[5])
            assertEquals(shape, chords.current.value.draft.shape)
            assertEquals(7, chords.current.value.draft.context.capo)
        }
    }
    @Test fun invalidRawInputsKeepAcceptedMusicAndPreventSaving() = runTest {
        val port = ControlledProgressions()
        port.current.value = ProgressionWorkspace(draft = ProgressionDraft("Practice", ProgressionContent(steps = listOf(ProgressionStep.Chord("C", shape)))))
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            var state = awaitItem()
            assertTrue(state.progressions.canSave)
            state.progression(ProgressionEvent.SetTempo("bad"))
            state = stateWhere { it.progressions.bpmInput == "bad" }
            assertTrue(state.progressions.bpmError)
            assertFalse(state.progressions.canSave)
            assertFalse(state.progressions.canPlay)
            assertTrue(state.progressions.invalidSettings)
            state.progression(ProgressionEvent.Play())
            state.progression(ProgressionEvent.Save)
            runCurrent()
            assertTrue(port.commands.none { it is ProgressionCommand.Play || it == ProgressionCommand.Save })
            state.progression(ProgressionEvent.SetTempo("90"))
            state = stateWhere { !it.progressions.bpmError }
            state.progression(ProgressionEvent.ChordInput(ChordEvent.SetOctave(0, "8")))
            state = stateWhere { it.progressions.editor.strings[0].octaveError }
            assertFalse(state.progressions.canSave)
            assertEquals(2, port.current.value.draft.content.context.tuning.pitches[0].octave)
            assertTrue(port.commands.none { it is ProgressionCommand.SetContext })
            state.progression(ProgressionEvent.ChordInput(ChordEvent.SetOctave(0, "2")))
            state = stateWhere { !it.progressions.editor.strings[0].octaveError }
            assertTrue(state.progressions.canSave)
        }
    }
    @Test fun presentedPauseAndLiveChangeAcknowledgmentRemainVisible() = runTest {
        val port = ControlledProgressions()
        port.current.value = ProgressionWorkspace(draft = ProgressionDraft("Practice", ProgressionContent(timing = MetronomeConfig(bpm = 120),
            metronomeEnabled = false, steps = listOf(ProgressionStep.Rest()))), playback = ProgressionPlayback.Paused(ProgressionPosition(0, bpm = 90)))
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            val state = awaitItem()
            assertEquals(ProgressionTransportUi.Paused, state.progressions.transport)
            assertTrue(state.progressions.pendingChange)
            port.current.value = port.current.value.copy(playback = ProgressionPlayback.Playing(ProgressionPosition(0, bpm = 120, metronomeEnabled = false)))
            val acknowledged = stateWhere { !it.progressions.pendingChange }
            assertEquals(0, acknowledged.progressions.playingIndex)
            assertEquals(ProgressionTransportUi.Playing, acknowledged.progressions.transport)
        }
    }
    @Test fun selectedFreshStartStopsBeforePlayAndFailedStorageKeepsRecordAcknowledgments() = runTest {
        val port = ControlledProgressions()
        port.current.value = ProgressionWorkspace(selectedIndex = 1, draft = ProgressionDraft("Practice", ProgressionContent(steps = listOf(ProgressionStep.Rest(), ProgressionStep.Rest()))),
            persistence = DraftPersistence.Unsynced, actionFailure = ProgressionFailure.WriteFailed)
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            val state = awaitItem()
            assertTrue(state.progressions.unsynced)
            assertEquals(ProgressionNotice.WriteFailed, state.progressions.notice)
            assertTrue(state.progressions.records.isEmpty())
            state.progression(ProgressionEvent.Play(true))
            runCurrent()
            assertEquals(listOf(ProgressionCommand.Stop, ProgressionCommand.Play(1)), port.commands)
        }
    }
    @Test fun namedShapeLookupUsesCapoZeroAndCopiesAQuarterNoteWithoutNamingOrMutatingTheSource() = runTest {
        val port = ControlledProgressions()
        port.current.value = ProgressionWorkspace(draft = ProgressionDraft(content = ProgressionContent(context = GuitarContext(capo = 2))))
        val chords = ProgressionSourceChords(ChordDraft("Viewer C", GuitarContext(capo = 7), shape))
        val viewer = chords.current.value
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), chords, port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.OpenEditor())
            state = stateWhere { it.progressions.editor.lookup == ChordLookupUi.Ready }
            assertEquals(ProgressionChordSourceUi.Named, state.progressions.chordSource)
            assertEquals("C", state.progressions.editor.lookupShapeSymbol)
            assertEquals("D", state.progressions.editor.lookupSymbol)
            assertEquals("", state.progressions.editor.name)
            state.progression(ProgressionEvent.SetChordSource(ProgressionChordSourceUi.Named))
            runCurrent()
            state.progression(ProgressionEvent.CommitEditor)
            stateWhere { it.progressions.sheet == ProgressionSheetUi.None && it.progressions.editor.lookup == ChordLookupUi.Idle && !it.progressions.busy }
            val inserted = (port.commands.single() as ProgressionCommand.Insert).step as ProgressionStep.Chord
            assertEquals("", inserted.name)
            assertEquals(NoteDuration(NoteValue.Quarter), inserted.duration)
            assertEquals(ChordTheory.representatives(ChordQuery(GuitarContext(), ChordIdentity(PitchClass.C, ChordQuality.Major))).first(), inserted.shape)
            assertEquals(viewer, chords.current.value)
        }
    }
    @Test fun staleNamedPreviewCannotCommitAfterRootChangeAndNeverOverwritesAManualCopy() = runTest {
        val port = ControlledProgressions()
        val chords = ProgressionSourceChords(ChordDraft("Viewer C", shape = shape))
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), chords, port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.OpenEditor())
            state = stateWhere { it.progressions.editor.lookup == ChordLookupUi.Ready }
            state.progression(ProgressionEvent.ChordInput(ChordEvent.SetRoot(PitchClass.A.ordinal)))
            state.progression(ProgressionEvent.CommitEditor)
            runCurrent()
            assertTrue(port.commands.isEmpty())
            state = stateWhere { it.progressions.editor.root == PitchClass.A.ordinal }
            state.progression(ProgressionEvent.ChordInput(ChordEvent.SetQuality(ChordQualityUi.Minor)))
            state.progression(ProgressionEvent.CopyCurrentChord)
            state = stateWhere { it.progressions.chordSource == ProgressionChordSourceUi.Manual }
            state.progression(ProgressionEvent.ChordInput(ChordEvent.SetFret(5, "3")))
            state = stateWhere { it.progressions.editor.strings[5].fret == 3 }
            assertEquals(ChordLookupUi.Idle, state.progressions.editor.lookup)
            state.progression(ProgressionEvent.CommitEditor)
            stateWhere { it.progressions.sheet == ProgressionSheetUi.None && !it.progressions.busy }
            val inserted = (port.commands.single() as ProgressionCommand.Insert).step as ProgressionStep.Chord
            assertEquals("Viewer C", inserted.name)
            assertEquals(StringStop.Fretted(3), inserted.shape.stops[5])
            assertEquals(shape, chords.current.value.draft.shape)
        }
    }
    @Test fun copyTabRequiresAChosenShapeAndClosingNamedLookupCannotApplyIt() = runTest {
        val port = ControlledProgressions()
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.OpenEditor())
            state = stateWhere { it.progressions.editor.lookup == ChordLookupUi.Ready }
            state.progression(ProgressionEvent.SetChordSource(ProgressionChordSourceUi.Saved))
            state = stateWhere { it.progressions.chordSource == ProgressionChordSourceUi.Saved }
            assertFalse(state.progressions.editor.canSave)
            state.progression(ProgressionEvent.CommitEditor)
            state.progression(ProgressionEvent.CloseSheet)
            stateWhere { it.progressions.sheet == ProgressionSheetUi.None && it.progressions.editor.lookup == ChordLookupUi.Idle && !it.progressions.busy }
            assertTrue(port.commands.isEmpty())
        }
    }
    @Test fun stepInspectionKeepsPlaybackAndSelectedStartOnlyClosesAfterAcceptance() = runTest {
        val port = ControlledProgressions()
        port.current.value = ProgressionWorkspace(draft = ProgressionDraft(content = ProgressionContent(steps = listOf(ProgressionStep.Rest()))),
            playback = ProgressionPlayback.Playing(ProgressionPosition(0)))
        port.onCommand = { if (it is ProgressionCommand.Select) port.current.value = port.current.value.copy(selectedIndex = it.index) }
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.OpenStep(0))
            state = stateWhere { it.progressions.sheet == ProgressionSheetUi.Step }
            assertEquals(ProgressionTransportUi.Playing, state.progressions.transport)
            assertEquals(listOf(ProgressionCommand.Select(0)), port.commands)
            port.onCommand = { port.result = if (it is ProgressionCommand.Play) Either.Left(ProgressionFailure.FocusDenied) else Either.Right(Unit) }
            state.progression(ProgressionEvent.Play(true))
            state = stateWhere { it.progressions.notice == ProgressionNotice.FocusDenied }
            assertEquals(ProgressionSheetUi.Step, state.progressions.sheet)
            port.onCommand = null
            port.result = Either.Right(Unit)
            state.progression(ProgressionEvent.Play(true))
            stateWhere { it.progressions.sheet == ProgressionSheetUi.None }
            assertEquals(listOf(ProgressionCommand.Select(0), ProgressionCommand.Stop, ProgressionCommand.Play(0), ProgressionCommand.Stop, ProgressionCommand.Play(0)), port.commands)
        }
    }
    @Test fun failedSaveKeepsTheSheetAndNameUntilCheckedRetrySucceeds() = runTest {
        val port = ControlledProgressions()
        port.current.value = ProgressionWorkspace(draft = ProgressionDraft("Practice", ProgressionContent(steps = listOf(ProgressionStep.Rest()))))
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.OpenSheet(ProgressionSheetUi.Save))
            state = stateWhere { it.progressions.sheet == ProgressionSheetUi.Save }
            port.result = Either.Left(ProgressionFailure.WriteFailed)
            state.progression(ProgressionEvent.Save)
            state = stateWhere { it.progressions.notice == ProgressionNotice.WriteFailed && !it.progressions.busy }
            assertEquals(ProgressionSheetUi.Save, state.progressions.sheet)
            assertEquals("Practice", state.progressions.name)
            assertTrue(state.progressions.records.isEmpty())
            port.result = Either.Right(Unit)
            state.progression(ProgressionEvent.Save)
            stateWhere { it.progressions.sheet == ProgressionSheetUi.None && !it.progressions.busy }
            assertEquals(listOf(ProgressionCommand.Save, ProgressionCommand.Save), port.commands)
        }
    }
    @Test fun cancellingNewOrLoadPreservesMusicalWorkAndInvalidSettings() = runTest {
        val port = ControlledProgressions()
        val original = ProgressionDraft("Practice", ProgressionContent(steps = listOf(ProgressionStep.Rest())))
        val record = SavedProgression("saved", "Saved", original.content)
        port.current.value = ProgressionWorkspace(draft = original, records = listOf(record))
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.SetTempo("bad"))
            state = stateWhere { it.progressions.invalidSettings }
            state.progression(ProgressionEvent.NewDraft)
            state = stateWhere { it.progressions.replacement == ProgressionReplacementUi.NewDraft }
            state.progression(ProgressionEvent.CancelReplacement)
            state = stateWhere { it.progressions.replacement == null }
            state.progression(ProgressionEvent.Load(record.id))
            state = stateWhere { it.progressions.replacement is ProgressionReplacementUi.Load }
            state.progression(ProgressionEvent.CancelReplacement)
            state = stateWhere { it.progressions.replacement == null }
            assertEquals("bad", state.progressions.bpmInput)
            assertEquals(original, port.current.value.draft)
            assertTrue(port.commands.isEmpty())
        }
    }
    @Test fun closingReplacementIntentAndQueuedStepSelectionCannotReopenAModalAfterNavigation() = runTest {
        val port = ControlledProgressions()
        port.current.value = ProgressionWorkspace(draft = ProgressionDraft(content = ProgressionContent(steps = listOf(ProgressionStep.Rest()))))
        val selected = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.NewDraft)
            state = stateWhere { it.progressions.replacement != null }
            state.progression(ProgressionEvent.CloseSheet)
            state = stateWhere { it.progressions.replacement == null }
            assertEquals(ProgressionSheetUi.None, state.progressions.sheet)
            port.onCommand = { if (it is ProgressionCommand.Select) {
                selected.complete(Unit); release.await()
                port.current.value = port.current.value.copy(selectedIndex = it.index)
            } }
            state.progression(ProgressionEvent.OpenStep(0))
            selected.await()
            state.progression(ProgressionEvent.CloseSheet)
            state.progression(ProgressionEvent.OpenSheet(ProgressionSheetUi.Settings))
            state = stateWhere { it.progressions.sheet == ProgressionSheetUi.Settings }
            release.complete(Unit)
            state = stateWhere { it.progressions.selectedIndex == 0 }
            assertEquals(ProgressionSheetUi.Settings, state.progressions.sheet)
        }
    }
    @Test fun anAcknowledgedSaveCannotCloseANewerSheet() = runTest {
        val port = ControlledProgressions()
        port.current.value = ProgressionWorkspace(draft = ProgressionDraft("Practice", ProgressionContent(steps = listOf(ProgressionStep.Rest()))))
        val saving = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        port.onCommand = { if (it == ProgressionCommand.Save) { saving.complete(Unit); release.await() } }
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.OpenSheet(ProgressionSheetUi.Save))
            state = stateWhere { it.progressions.sheet == ProgressionSheetUi.Save }
            state.progression(ProgressionEvent.Save)
            saving.await()
            state.progression(ProgressionEvent.CloseSheet)
            state.progression(ProgressionEvent.OpenSheet(ProgressionSheetUi.Settings))
            state = stateWhere { it.progressions.sheet == ProgressionSheetUi.Settings }
            release.complete(Unit)
            state = stateWhere { !it.progressions.busy }
            assertEquals(ProgressionSheetUi.Settings, state.progressions.sheet)
        }
    }
    @Test fun aQueuedContextChangeRejectsTheEarlierNamedPreviewAtCommit() = runTest {
        val port = ControlledProgressions()
        val writing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        port.onCommand = { when (it) {
            is ProgressionCommand.SetName -> { writing.complete(Unit); release.await() }
            is ProgressionCommand.SetContext -> port.current.value = port.current.value.copy(
                draft = port.current.value.draft.copy(content = port.current.value.draft.content.copy(context = it.context)))
            else -> Unit
        } }
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.OpenEditor())
            state = stateWhere { it.progressions.editor.lookup == ChordLookupUi.Ready }
            state.progression(ProgressionEvent.SetName("Practice"))
            writing.await()
            state.progression(ProgressionEvent.ChordInput(ChordEvent.SetPreset(TuningPresetUi.OpenD)))
            state.progression(ProgressionEvent.CommitEditor)
            runCurrent()
            release.complete(Unit)
            state = stateWhere { it.progressions.notice == ProgressionNotice.ShapeChanged && !it.progressions.busy }
            assertEquals(ProgressionSheetUi.Chord, state.progressions.sheet)
            assertEquals(TuningPresetUi.OpenD, state.progressions.editor.preset)
            assertTrue(port.current.value.draft.content.steps.isEmpty())
            assertTrue(port.commands.none { it is ProgressionCommand.Insert })
            assertEquals(TuningPreset.OpenD.tuning, port.current.value.draft.content.context.tuning)
        }
    }
    @Test fun aQueuedInsertKeepsItsNameAndKindWhenAnotherEditorOpensBeforeAcknowledgment() = runTest {
        val port = ControlledProgressions()
        port.current.value = ProgressionWorkspace(draft = ProgressionDraft(content = ProgressionContent(steps = listOf(ProgressionStep.Chord("Stored", shape)))))
        val writing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        port.onCommand = { if (it is ProgressionCommand.SetName) { writing.complete(Unit); release.await() } }
        FoundationPresenter(ProgressionAppearance(), ProgressionMetronome(), ControlledTuner(), ProgressionSourceChords(), port).test {
            var state = awaitItem()
            state.progression(ProgressionEvent.OpenEditor())
            state = stateWhere { it.progressions.editor.lookup == ChordLookupUi.Ready }
            state.progression(ProgressionEvent.SetName("Practice"))
            writing.await()
            state.progression(ProgressionEvent.CommitEditor)
            state.progression(ProgressionEvent.CloseSheet)
            state.progression(ProgressionEvent.OpenEditor(0))
            state = stateWhere { it.progressions.sheet == ProgressionSheetUi.Chord && it.progressions.editingIndex == 0 && it.progressions.editor.name == "Stored" }
            release.complete(Unit)
            state = stateWhere { !it.progressions.busy }
            assertEquals(ProgressionSheetUi.Chord, state.progressions.sheet)
            assertEquals("Stored", state.progressions.editor.name)
            val inserted = port.commands.filterIsInstance<ProgressionCommand.Insert>().single().step as ProgressionStep.Chord
            assertEquals("", inserted.name)
            assertTrue(port.commands.none { it is ProgressionCommand.ReplaceChord })
            assertEquals(ChordTheory.representatives(ChordQuery(GuitarContext(), ChordIdentity(PitchClass.C, ChordQuality.Major))).first(), inserted.shape)
        }
    }
}
private fun FoundationState.progression(event: ProgressionEvent) { eventSink(FoundationEvent.Progression(event)) }
private suspend fun CircuitReceiveTurbine<FoundationState>.stateWhere(predicate: (FoundationState) -> Boolean): FoundationState {
    while (true) { val state = awaitItem(); if (predicate(state)) return state }
}
private class ProgressionSourceChords(draft: ChordDraft = ChordDraft()) : Chords {
    override val current = MutableStateFlow(ChordWorkspace(draft = ChordTheory.normalize(draft)))
    override suspend fun execute(command: ChordCommand): Either<ChordFailure, Unit> = error("Progression mutated chord viewer: $command")
}
private class ProgressionAppearance : AppearanceSettings {
    override val current = MutableStateFlow(AppearanceSnapshot(ThemePreference.System, LanguagePreference.System))
    override suspend fun select(change: AppearanceChange): Either<AppearanceFailure, Unit> = error("Unexpected appearance request")
}
private class ProgressionMetronome : Metronome {
    override val current = MutableStateFlow(MetronomeSnapshot())
    override suspend fun execute(command: MetronomeCommand): Either<MetronomeFailure, Unit> = error("Unexpected metronome request")
}
