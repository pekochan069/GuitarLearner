package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.presentation.contract.*
import com.pekochan069.guitarlearner.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressionTool(state: ProgressionUiState, eventSink: (ProgressionEvent) -> Unit) {
    var deleteId by remember { mutableStateOf<String?>(null) }
    val validInput = !state.bpmError && !state.numeratorError && !state.editor.capoError && state.editor.strings.none { it.noteError || it.octaveError }
    Column(Modifier.fillMaxWidth().testTag("progression_tool"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.notice?.let { ProgressionStatus(stringResource(it.label), "progression_error", true) }
        state.stopReason?.takeUnless { it == MetronomeStopUi.User }?.let {
            ProgressionStatus(stringResource(when (it) {
                MetronomeStopUi.FocusLoss -> R.string.progression_focus_lost
                MetronomeStopUi.OutputDisconnected -> R.string.progression_output_removed
                else -> R.string.progression_service_ended
            }), "progression_interruption", true)
        }
        if (state.readFailed) {
            ProgressionStatus(stringResource(R.string.progression_read_failed), "progression_read_failed", true)
            FilledTonalButton(onClick = { eventSink(ProgressionEvent.RetryStorageRead) }, modifier = Modifier.testTag("progression_retry_read")) {
                Text(stringResource(R.string.chord_retry_read))
            }
        }
        if (state.unsynced) {
            ProgressionStatus(stringResource(R.string.progression_unsynced), "progression_unsynced", true)
            FilledTonalButton(onClick = { eventSink(ProgressionEvent.RetryDraftWrite) }, enabled = !state.readFailed && !state.busy,
                modifier = Modifier.testTag("progression_retry_draft")) { Text(stringResource(R.string.chord_retry_draft)) }
        }
        OutlinedTextField(state.name, { eventSink(ProgressionEvent.SetName(it)) }, modifier = Modifier.fillMaxWidth().testTag("progression_name"),
            label = { Text(stringResource(R.string.progression_name)) }, singleLine = true, isError = state.name.trim().length > 80)
        if (state.editor.capoError || state.editor.strings.any { it.noteError || it.octaveError }) {
            ProgressionStatus(stringResource(R.string.progression_context_invalid), "progression_context_error", true)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { eventSink(ProgressionEvent.Save) }, enabled = state.canSave, modifier = Modifier.testTag("progression_save")) { Text(stringResource(R.string.progression_save)) }
            OutlinedButton(onClick = { eventSink(ProgressionEvent.NewDraft) }, enabled = !state.busy, modifier = Modifier.testTag("progression_new")) { Text(stringResource(R.string.progression_new)) }
            OutlinedButton(onClick = { eventSink(ProgressionEvent.SetContextOpen(true)) }, modifier = Modifier.testTag("progression_context")) {
                Text(stringResource(R.string.chord_context_summary, state.editor.preset?.let { stringResource(it.label) } ?: stringResource(R.string.chord_custom_tuning), state.editor.capo))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(state.bpmInput, { eventSink(ProgressionEvent.SetTempo(it)) }, label = { Text(stringResource(R.string.progression_tempo)) },
                modifier = Modifier.widthIn(min = 120.dp, max = 180.dp).testTag("progression_tempo"), singleLine = true,
                isError = state.bpmError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = if (state.bpmError) ({ Text(stringResource(R.string.progression_tempo_error)) }) else null)
            OutlinedTextField(state.numeratorInput, { eventSink(ProgressionEvent.SetNumerator(it)) }, label = { Text(stringResource(R.string.progression_beats)) },
                modifier = Modifier.widthIn(min = 120.dp, max = 180.dp).testTag("progression_beats"), singleLine = true,
                isError = state.numeratorError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = if (state.numeratorError) ({ Text(stringResource(R.string.progression_beats_error)) }) else null)
            ChordDropdown(stringResource(R.string.progression_beat_unit), "1/${state.denominator.denominator}",
                BeatUnitUi.entries.map { "1/${it.denominator}" }, "progression_beat_unit", Modifier.widthIn(min = 120.dp, max = 180.dp)) {
                eventSink(ProgressionEvent.SetDenominator(BeatUnitUi.entries[it]))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.metronome, { eventSink(ProgressionEvent.SetMetronome(!state.metronome)) }, label = { Text(stringResource(R.string.metronome_title)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_click"))
            FilterChip(state.loop, { eventSink(ProgressionEvent.SetLoop(!state.loop)) }, label = { Text(stringResource(R.string.progression_loop)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_loop"))
        }
        when {
            state.transport == ProgressionTransportUi.Paused -> ProgressionStatus(stringResource(R.string.progression_paused), "progression_paused")
            state.countInBeat != null -> ProgressionStatus(stringResource(R.string.progression_count_in, (state.countInBeat ?: 0) + 1, state.numerator), "progression_count_in")
            state.transport == ProgressionTransportUi.Preparing -> ProgressionStatus(stringResource(R.string.progression_preparing), "progression_preparing")
        }
        if (state.pendingChange) ProgressionStatus(stringResource(R.string.progression_pending), "progression_pending")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.transport) {
                ProgressionTransportUi.Playing -> Button(onClick = { eventSink(ProgressionEvent.Pause) }, modifier = Modifier.testTag("progression_pause")) { Text(stringResource(R.string.progression_pause)) }
                ProgressionTransportUi.Paused -> Button(onClick = { eventSink(ProgressionEvent.Resume) }, modifier = Modifier.testTag("progression_resume")) { Text(stringResource(R.string.progression_resume)) }
                else -> Button(onClick = { eventSink(ProgressionEvent.Play()) }, enabled = state.steps.isNotEmpty() && state.transport != ProgressionTransportUi.Preparing && validInput,
                    modifier = Modifier.testTag("progression_play")) { Text(stringResource(R.string.progression_play)) }
            }
            OutlinedButton(onClick = { eventSink(ProgressionEvent.Stop) }, enabled = state.transport in listOf(ProgressionTransportUi.Playing, ProgressionTransportUi.Paused, ProgressionTransportUi.Preparing),
                modifier = Modifier.testTag("progression_stop")) { Text(stringResource(R.string.progression_stop)) }
            OutlinedButton(onClick = { eventSink(ProgressionEvent.Play(true)) }, enabled = state.selectedIndex in state.steps.indices && validInput,
                modifier = Modifier.testTag("progression_play_selected")) { Text(stringResource(R.string.progression_play_selected)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { eventSink(ProgressionEvent.OpenEditor()) }, enabled = !state.busy, modifier = Modifier.testTag("progression_add_chord")) { Text(stringResource(R.string.progression_add_chord)) }
            OutlinedButton(onClick = { eventSink(ProgressionEvent.AddRest) }, enabled = !state.busy, modifier = Modifier.testTag("progression_add_rest")) { Text(stringResource(R.string.progression_add_rest)) }
        }
        if (state.steps.isEmpty()) Text(stringResource(R.string.progression_empty), Modifier.testTag("progression_empty"), style = MaterialTheme.typography.bodyMedium)
        state.steps.forEach { step ->
            Card(modifier = Modifier.fillMaxWidth().testTag("progression_card_${step.index}"),
                shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor =
                    if (step.index == state.playingIndex) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { eventSink(ProgressionEvent.Select(step.index)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progression_step_${step.index}")) {
                        Text(stringResource(R.string.progression_step_title, step.index + 1,
                            if (step.rest) stringResource(R.string.progression_rest) else step.name.ifBlank { step.sounding ?: stringResource(R.string.chord_unrecognized) }),
                            style = MaterialTheme.typography.titleMedium)
                    }
                    if (!step.rest && step.name.isNotBlank()) step.sounding?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    step.shape?.let { Text(stringResource(R.string.chord_shape_symbol, it), style = MaterialTheme.typography.bodySmall) }
                    Text("1/${step.duration.denominator}" + if (step.dotted) " ·" else "", style = MaterialTheme.typography.labelLarge)
                    if (step.index == state.selectedIndex) {
                        if (!step.rest) ChordFretboard(step.strings, "progression_step_${step.index}", state.editor.capo)
                        ChordDropdown(stringResource(R.string.progression_duration), "1/${step.duration.denominator}", NoteValueUi.entries.map { "1/${it.denominator}" },
                            "progression_duration_${step.index}") { eventSink(ProgressionEvent.SetDuration(step.index, NoteValueUi.entries[it], step.dotted)) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(step.dotted, { eventSink(ProgressionEvent.SetDuration(step.index, step.duration, !step.dotted)) }, label = { Text(stringResource(R.string.progression_dot)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_dot_${step.index}"))
                            if (!step.rest) FilterChip(step.tied, { eventSink(ProgressionEvent.SetTie(step.index, !step.tied)) }, enabled = step.canTie || step.tied,
                                label = { Text(stringResource(R.string.progression_tie)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_tie_${step.index}"))
                            if (!step.rest) TextButton(onClick = { eventSink(ProgressionEvent.OpenEditor(step.index)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_edit_${step.index}")) { Text(stringResource(R.string.chord_edit)) }
                            TextButton(onClick = { eventSink(ProgressionEvent.Move(step.index, -1)) }, enabled = step.index > 0, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_up_${step.index}")) { Text(stringResource(R.string.progression_up)) }
                            TextButton(onClick = { eventSink(ProgressionEvent.Move(step.index, 1)) }, enabled = step.index < state.steps.lastIndex, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_down_${step.index}")) { Text(stringResource(R.string.progression_down)) }
                            TextButton(onClick = { eventSink(ProgressionEvent.Remove(step.index)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_remove_${step.index}")) { Text(stringResource(R.string.progression_remove)) }
                        }
                    }
                }
            }
        }
        if (state.records.isNotEmpty()) {
            SectionHeading(R.string.progression_collection)
            state.records.forEach { record ->
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(record.name, Modifier.testTag("progression_record_${record.id}"), style = MaterialTheme.typography.titleMedium)
                        Text(pluralStringResource(R.plurals.progression_step_count, record.stepCount, record.stepCount), style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { eventSink(ProgressionEvent.Load(record.id)) }, enabled = !state.busy, modifier = Modifier.testTag("progression_load_${record.id}")) { Text(stringResource(R.string.chord_load)) }
                            TextButton(onClick = { deleteId = record.id }, enabled = !state.busy, modifier = Modifier.testTag("progression_delete_${record.id}")) { Text(stringResource(R.string.chord_delete)) }
                        }
                    }
                }
            }
        }
    }
    if (state.editorOpen || state.contextOpen) {
        ModalBottomSheet(onDismissRequest = { if (state.editorOpen) eventSink(ProgressionEvent.CloseEditor) else eventSink(ProgressionEvent.SetContextOpen(false)) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = Modifier.testTag("progression_sheet")) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("progression_sheet_scroll").padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.contextOpen) ChordContextControls(state.editor) { eventSink(ProgressionEvent.ChordInput(it)) }
                else {
                    SectionHeading(R.string.progression_chord_editor)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { eventSink(ProgressionEvent.CopyCurrentChord) }, modifier = Modifier.testTag("progression_copy_current")) { Text(stringResource(R.string.progression_copy_current)) }
                        state.editor.records.forEach { source ->
                            OutlinedButton(onClick = { eventSink(ProgressionEvent.CopyCustomChord(source.id)) }, modifier = Modifier.testTag("progression_copy_${source.id}")) { Text(source.name) }
                        }
                    }
                    ChordSummary(state.editor.analysis, state.editor.soundingSymbol, state.editor.shapeSymbol, state.editor.notes)
                    ChordFretboard(state.editor.strings, "progression_editor", state.editor.capo) { eventSink(ProgressionEvent.ChordInput(it)) }
                    OutlinedTextField(state.editor.name, { eventSink(ProgressionEvent.ChordInput(ChordEvent.SetName(it))) },
                        modifier = Modifier.fillMaxWidth().testTag("progression_chord_name"), label = { Text(stringResource(R.string.chord_name)) }, isError = state.editor.nameError)
                    Button(onClick = { eventSink(ProgressionEvent.CommitEditor) }, enabled = state.editor.canSave && !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("progression_commit_chord")) { Text(stringResource(if (state.editingIndex == null) R.string.progression_add_chord else R.string.progression_apply_chord)) }
                    state.editor.strings.forEach { ChordStringControls(it) { eventSink(ProgressionEvent.ChordInput(it)) } }
                }
                TextButton(onClick = { eventSink(ProgressionEvent.CloseEditor); eventSink(ProgressionEvent.SetContextOpen(false)) }, modifier = Modifier.testTag("progression_close_sheet")) { Text(stringResource(R.string.chord_close_context)) }
            }
        }
    }
    state.records.firstOrNull { it.id == deleteId }?.let { record ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(stringResource(R.string.progression_delete_title)) }, text = { Text(record.name) },
            confirmButton = { TextButton(onClick = { eventSink(ProgressionEvent.Delete(record.id)); deleteId = null }, modifier = Modifier.testTag("progression_confirm_delete")) { Text(stringResource(R.string.chord_delete)) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.chord_cancel)) } })
    }
}
@Composable private fun ProgressionStatus(value: String, tag: String, error: Boolean = false) {
    Text(value, Modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}
private val ProgressionNotice.label: Int get() = when (this) {
    ProgressionNotice.InvalidInput -> R.string.chord_invalid_input; ProgressionNotice.InvalidName -> R.string.chord_name_error
    ProgressionNotice.EmptyShape -> R.string.chord_empty; ProgressionNotice.InvalidTie -> R.string.progression_invalid_tie
    ProgressionNotice.EmptyProgression -> R.string.progression_empty; ProgressionNotice.RecordMissing -> R.string.chord_record_missing
    ProgressionNotice.ReadFailed -> R.string.progression_read_failed; ProgressionNotice.WriteFailed -> R.string.progression_write_failed
    ProgressionNotice.FocusDenied -> R.string.progression_focus_denied; ProgressionNotice.ServiceUnavailable -> R.string.progression_service_failed
    ProgressionNotice.AudioUnavailable -> R.string.progression_audio_failed
}
