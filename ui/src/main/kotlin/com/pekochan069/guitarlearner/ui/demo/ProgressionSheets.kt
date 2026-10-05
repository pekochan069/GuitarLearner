package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.presentation.contract.*
import com.pekochan069.guitarlearner.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressionSheets(state: ProgressionUiState, eventSink: (ProgressionEvent) -> Unit) {
    var deleteId by remember { mutableStateOf<String?>(null) }
    val recordToDelete = state.records.firstOrNull { it.id == deleteId }
    val replacement = state.replacement
    if (replacement != null) {
        AlertDialog(onDismissRequest = { eventSink(ProgressionEvent.CancelReplacement) },
            title = { Text(stringResource(R.string.progression_replace_title)) },
            text = { Text(stringResource(if (replacement is ProgressionReplacementUi.Load) R.string.progression_replace_load else R.string.progression_replace_new)) },
            confirmButton = { TextButton(onClick = { eventSink(ProgressionEvent.ConfirmReplacement) }, modifier = Modifier.testTag("progression_confirm_replace")) {
                Text(stringResource(if (replacement is ProgressionReplacementUi.Load) R.string.chord_load else R.string.progression_new))
            } },
            dismissButton = { TextButton(onClick = { eventSink(ProgressionEvent.CancelReplacement) }, modifier = Modifier.testTag("progression_cancel_replace")) { Text(stringResource(R.string.chord_cancel)) } })
    } else if (recordToDelete != null) {
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(stringResource(R.string.progression_delete_title)) }, text = { Text(recordToDelete.name) },
            confirmButton = { TextButton(onClick = { eventSink(ProgressionEvent.Delete(recordToDelete.id)); deleteId = null }, modifier = Modifier.testTag("progression_confirm_delete")) { Text(stringResource(R.string.chord_delete)) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.chord_cancel)) } })
    } else if (state.sheet != ProgressionSheetUi.None) {
        ModalBottomSheet(onDismissRequest = { eventSink(ProgressionEvent.CloseSheet) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = Modifier.testTag("progression_sheet")) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(when (state.sheet) {
                        ProgressionSheetUi.Step -> R.string.progression_step_details
                        ProgressionSheetUi.Chord -> if (state.editingIndex == null) R.string.progression_add_chord else R.string.progression_chord_editor
                        ProgressionSheetUi.Settings -> R.string.progression_settings
                        ProgressionSheetUi.Save -> R.string.progression_save
                        ProgressionSheetUi.Collection -> R.string.progression_collection
                        ProgressionSheetUi.None -> R.string.progression_title
                    }), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { eventSink(ProgressionEvent.CloseSheet) }, modifier = Modifier.size(48.dp).testTag("progression_close_sheet")) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.progression_close))
                    }
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).testTag("progression_sheet_scroll")
                    .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.notice?.let { ProgressionStatus(stringResource(it.label), "progression_sheet_error", true) }
                    when (state.sheet) {
                        ProgressionSheetUi.Step -> state.steps.getOrNull(state.selectedIndex)?.let { ProgressionStepDetails(it, state, eventSink) }
                        ProgressionSheetUi.Chord -> ProgressionChordPicker(state, eventSink)
                        ProgressionSheetUi.Settings -> ProgressionSettings(state, eventSink)
                        ProgressionSheetUi.Save -> OutlinedTextField(state.name, { eventSink(ProgressionEvent.SetName(it)) },
                            modifier = Modifier.fillMaxWidth().testTag("progression_name"), enabled = !state.busy,
                            label = { Text(stringResource(R.string.progression_name)) }, singleLine = true, isError = state.name.trim().length > 80)
                        ProgressionSheetUi.Collection -> {
                            if (state.records.isEmpty()) Text(stringResource(R.string.progression_collection_empty))
                            state.records.forEach { record ->
                                ListItem(headlineContent = { Text(record.name, Modifier.testTag("progression_record_${record.id}")) },
                                    supportingContent = { Text(pluralStringResource(R.plurals.progression_step_count, record.stepCount, record.stepCount)) })
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { eventSink(ProgressionEvent.Load(record.id)) }, enabled = !state.busy && !state.readFailed,
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("progression_load_${record.id}")) { Text(stringResource(R.string.chord_load)) }
                                    TextButton(onClick = { deleteId = record.id }, enabled = !state.busy && !state.readFailed,
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("progression_delete_${record.id}")) { Text(stringResource(R.string.chord_delete)) }
                                }
                            }
                        }
                        ProgressionSheetUi.None -> Unit
                    }
                }
                when (state.sheet) {
                    ProgressionSheetUi.Chord -> Button(onClick = { eventSink(ProgressionEvent.CommitEditor) },
                        enabled = state.editor.canSave && !state.busy && !state.readFailed && !state.invalidSettings,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp).testTag("progression_commit_chord")) {
                        Text(stringResource(if (state.editingIndex == null) R.string.progression_add_chord else R.string.progression_apply_chord))
                    }
                    ProgressionSheetUi.Save -> Button(onClick = { eventSink(ProgressionEvent.Save) }, enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp).testTag("progression_save")) { Text(stringResource(R.string.progression_save)) }
                    ProgressionSheetUi.Step -> Button(onClick = { eventSink(ProgressionEvent.Play(true)) }, enabled = state.canPlay && state.selectedIndex in state.steps.indices,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp).testTag("progression_play_selected")) { Text(stringResource(R.string.progression_play_selected)) }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ProgressionStepDetails(step: ProgressionStepUi, state: ProgressionUiState, eventSink: (ProgressionEvent) -> Unit) {
    Text(step.title(), style = MaterialTheme.typography.headlineSmall)
    if (step.name.isNotBlank()) Text(step.name, style = MaterialTheme.typography.bodyMedium)
    step.sounding?.takeIf { it != step.shape }?.let { Text(stringResource(R.string.progression_sounding, it)) }
    ChordDropdown(stringResource(R.string.progression_duration), step.duration.label(), NoteValueUi.entries.map { it.label() },
        "progression_duration_${step.index}") { eventSink(ProgressionEvent.SetDuration(step.index, NoteValueUi.entries[it], step.dotted)) }
    Text(step.durationLabel(state.denominator), style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(step.dotted, { eventSink(ProgressionEvent.SetDuration(step.index, step.duration, !step.dotted)) },
            label = { Text(stringResource(R.string.progression_dot)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_dot_${step.index}"))
        if (!step.rest) FilterChip(step.tied, { eventSink(ProgressionEvent.SetTie(step.index, !step.tied)) }, enabled = step.canTie || step.tied,
            label = { Text(stringResource(R.string.progression_tie)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_tie_${step.index}"))
    }
    if (!step.rest) ChordFretboard(step.strings, "progression_step_${step.index}", state.editor.capo)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!step.rest) OutlinedButton(onClick = { eventSink(ProgressionEvent.OpenEditor(step.index)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_edit_${step.index}")) { Text(stringResource(R.string.chord_edit)) }
        TextButton(onClick = { eventSink(ProgressionEvent.Move(step.index, -1)) }, enabled = step.index > 0, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_up_${step.index}")) { Text(stringResource(R.string.progression_up)) }
        TextButton(onClick = { eventSink(ProgressionEvent.Move(step.index, 1)) }, enabled = step.index < state.steps.lastIndex, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_down_${step.index}")) { Text(stringResource(R.string.progression_down)) }
        TextButton(onClick = { eventSink(ProgressionEvent.Remove(step.index)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_remove_${step.index}")) { Text(stringResource(R.string.progression_remove)) }
    }
}

@Composable
private fun ProgressionSettings(state: ProgressionUiState, eventSink: (ProgressionEvent) -> Unit) {
    OutlinedTextField(state.bpmInput, { eventSink(ProgressionEvent.SetTempo(it)) }, label = { Text(stringResource(R.string.progression_tempo)) },
        modifier = Modifier.fillMaxWidth().testTag("progression_tempo"), singleLine = true, isError = state.bpmError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text(stringResource(if (state.bpmError) R.string.progression_tempo_error else R.string.progression_tempo_unit)) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(state.numeratorInput, { eventSink(ProgressionEvent.SetNumerator(it)) }, label = { Text(stringResource(R.string.progression_beats)) },
            modifier = Modifier.weight(1f).testTag("progression_beats"), singleLine = true, isError = state.numeratorError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = if (state.numeratorError) ({ Text(stringResource(R.string.progression_beats_error)) }) else null)
        ChordDropdown(stringResource(R.string.progression_beat_unit), "${state.denominator.denominator}", BeatUnitUi.entries.map { "${it.denominator}" },
            "progression_beat_unit", Modifier.weight(1f)) { eventSink(ProgressionEvent.SetDenominator(BeatUnitUi.entries[it])) }
    }
    Text("${state.numerator}/${state.denominator.denominator}", style = MaterialTheme.typography.titleMedium)
    FilterChip(state.metronome, { eventSink(ProgressionEvent.SetMetronome(!state.metronome)) }, label = { Text(stringResource(R.string.metronome_title)) },
        modifier = Modifier.heightIn(min = 48.dp).testTag("progression_click"))
    ChordContextControls(state.editor) { eventSink(ProgressionEvent.ChordInput(it)) }
}

@Composable
private fun ProgressionChordPicker(state: ProgressionUiState, eventSink: (ProgressionEvent) -> Unit) {
    val editor = state.editor
    val input: (ChordEvent) -> Unit = { eventSink(ProgressionEvent.ChordInput(it)) }
    PrimaryScrollableTabRow(state.chordSource.ordinal, edgePadding = 0.dp) {
        ProgressionChordSourceUi.entries.forEach { source ->
            Tab(state.chordSource == source, onClick = { eventSink(ProgressionEvent.SetChordSource(source)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("progression_source_${source.name}"),
                text = { Text(stringResource(when (source) { ProgressionChordSourceUi.Named -> R.string.progression_named_chord
                    ProgressionChordSourceUi.Saved -> R.string.progression_saved_chord; ProgressionChordSourceUi.Manual -> R.string.progression_manual_chord })) })
        }
    }
    when (state.chordSource) {
        ProgressionChordSourceUi.Named -> {
            val fieldWidth = 144.dp * LocalDensity.current.fontScale
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChordDropdown(stringResource(R.string.chord_root), editor.roots[editor.root], editor.roots, "progression_root", Modifier.width(fieldWidth).weight(1f)) { input(ChordEvent.SetRoot(it)) }
                ChordDropdown(stringResource(R.string.chord_quality), editor.quality.label(), ChordQualityUi.entries.map { it.label() }, "progression_quality", Modifier.width(fieldWidth).weight(1f)) { input(ChordEvent.SetQuality(ChordQualityUi.entries[it])) }
            }
            Text(stringResource(R.string.progression_shape_search, editor.preset?.let { stringResource(it.label) } ?: stringResource(R.string.chord_custom_tuning)), style = MaterialTheme.typography.bodySmall)
            when (editor.lookup) {
                ChordLookupUi.Searching -> LinearProgressIndicator(Modifier.fillMaxWidth().testTag("progression_searching"))
                ChordLookupUi.NoShapes -> ProgressionStatus(stringResource(R.string.chord_no_shapes, editor.lookupShapeSymbol.orEmpty()), "progression_lookup_status")
                ChordLookupUi.Ready -> {
                    Text(stringResource(R.string.chord_shape_symbol, editor.lookupShapeSymbol.orEmpty()), Modifier.testTag("progression_lookup_shape"), style = MaterialTheme.typography.titleLarge)
                    editor.lookupSymbol?.let { Text(stringResource(R.string.progression_sounding, it), Modifier.testTag("progression_lookup_sounding")) }
                    if (editor.lookupOmitted.isNotBlank()) Text(stringResource(R.string.chord_lookup_omitted, editor.lookupOmitted))
                    ChordFretboard(editor.lookupStrings, "progression_lookup", editor.capo)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { input(ChordEvent.SelectRepresentative(editor.representativeIndex - 1)) }, enabled = editor.representativeIndex > 0,
                            modifier = Modifier.size(48.dp).testTag("progression_previous_shape")) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.chord_previous)) }
                        Text(stringResource(R.string.chord_representative_count, editor.representativeIndex + 1, editor.representativeCount), style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { input(ChordEvent.SelectRepresentative(editor.representativeIndex + 1)) }, enabled = editor.representativeIndex + 1 < editor.representativeCount,
                            modifier = Modifier.size(48.dp).testTag("progression_next_shape")) { Icon(painterResource(R.drawable.ic_arrow_forward), stringResource(R.string.chord_next)) }
                    }
                    TextButton(onClick = { input(ChordEvent.CopyRepresentative) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_customize_shape")) { Text(stringResource(R.string.progression_customize_shape)) }
                }
                ChordLookupUi.Idle -> Unit
            }
        }
        ProgressionChordSourceUi.Saved -> {
            OutlinedButton(onClick = { eventSink(ProgressionEvent.CopyCurrentChord) }, modifier = Modifier.heightIn(min = 48.dp).testTag("progression_copy_current")) { Text(stringResource(R.string.progression_copy_current)) }
            editor.records.forEach { source ->
                OutlinedButton(onClick = { eventSink(ProgressionEvent.CopyCustomChord(source.id)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progression_copy_${source.id}")) { Text(source.name) }
            }
        }
        ProgressionChordSourceUi.Manual -> {
            ChordSummary(editor.analysis, editor.soundingSymbol, editor.shapeSymbol, editor.notes)
            ChordFretboard(editor.strings, "progression_editor", editor.capo, input)
            OutlinedTextField(editor.name, { input(ChordEvent.SetName(it)) }, modifier = Modifier.fillMaxWidth().testTag("progression_chord_name"),
                label = { Text(stringResource(R.string.progression_personal_chord_name)) }, isError = editor.nameError)
            editor.strings.forEach { ChordStringControls(it, input) }
        }
    }
}
