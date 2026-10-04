package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.presentation.contract.ChordAnalysisUi
import com.pekochan069.guitarlearner.presentation.contract.ChordEvent
import com.pekochan069.guitarlearner.presentation.contract.ChordLookupUi
import com.pekochan069.guitarlearner.presentation.contract.ChordNotice
import com.pekochan069.guitarlearner.presentation.contract.ChordQualityUi
import com.pekochan069.guitarlearner.presentation.contract.ChordSection
import com.pekochan069.guitarlearner.presentation.contract.ChordStopUi
import com.pekochan069.guitarlearner.presentation.contract.ChordStringUi
import com.pekochan069.guitarlearner.presentation.contract.ChordUiState
import com.pekochan069.guitarlearner.presentation.contract.TuningPresetUi
import com.pekochan069.guitarlearner.ui.R

@Composable
fun ChordTool(state: ChordUiState, eventSink: (ChordEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("chord_tool"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.chord_description), style = MaterialTheme.typography.bodyLarge)
        PrimaryTabRow(selectedTabIndex = state.section.ordinal) {
            ChordSection.entries.forEach { section ->
                Tab(selected = state.section == section, onClick = { eventSink(ChordEvent.SetSection(section)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("chord_section_" + section.name),
                    text = { Text(stringResource(section.label)) })
            }
        }
        if (state.readFailed) {
            ChordPanel {
                StatusText(stringResource(R.string.chord_read_failed), "chord_read_error", error = true)
                FilledTonalButton(onClick = { eventSink(ChordEvent.RetryStorageRead) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("chord_retry_read")) {
                    Text(stringResource(R.string.chord_retry_read))
                }
            }
        }
        state.notice?.takeUnless { it == ChordNotice.ReadFailed && state.readFailed }?.let {
            StatusText(stringResource(it.label), "chord_action_error", error = true)
        }
        if (state.section != ChordSection.Collection) ChordContextControls(state, eventSink)
        when (state.section) {
            ChordSection.Lookup -> ChordLookupContent(state, eventSink)
            ChordSection.Edit -> ChordEditor(state, eventSink)
            ChordSection.Collection -> ChordCollection(state, eventSink)
        }
    }
}

@Composable
private fun ChordContextControls(state: ChordUiState, eventSink: (ChordEvent) -> Unit) {
    ChordPanel {
        ChordHeading(R.string.chord_context)
        ChordDropdown(stringResource(R.string.chord_tuning),
            state.preset?.let { stringResource(it.label) } ?: stringResource(R.string.chord_custom_tuning),
            TuningPresetUi.entries.map { stringResource(it.label) }, "chord_tuning") { eventSink(ChordEvent.SetPreset(TuningPresetUi.entries[it])) }
        OutlinedTextField(value = state.capoInput, onValueChange = { eventSink(ChordEvent.SetCapo(it)) },
            modifier = Modifier.fillMaxWidth().testTag("chord_capo"), label = { Text(stringResource(R.string.chord_capo)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = state.capoError, singleLine = true,
            supportingText = { Text(stringResource(if (state.capoError) R.string.chord_capo_error else R.string.chord_capo_help)) })
        Text(stringResource(R.string.chord_accepted_capo, state.capo), Modifier.testTag("chord_accepted_capo"), style = MaterialTheme.typography.bodyMedium)
        Text(state.strings.joinToString(" · ") { it.tuning }, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { eventSink(ChordEvent.SetTuningExpanded(!state.tuningExpanded)) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("chord_custom_tuning")) {
            Text(stringResource(if (state.tuningExpanded) R.string.chord_hide_tuning else R.string.chord_edit_tuning))
        }
        if (state.tuningExpanded) {
            Text(stringResource(R.string.chord_tuning_help), style = MaterialTheme.typography.bodyMedium)
            state.strings.forEach { string ->
                Text(stringResource(R.string.chord_string, 6 - string.index), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = string.noteInput, onValueChange = { eventSink(ChordEvent.SetNote(string.index, it)) },
                        modifier = Modifier.weight(1f).testTag("chord_note_" + string.index),
                        label = { Text(stringResource(R.string.chord_note)) }, singleLine = true, isError = string.noteError,
                        supportingText = if (string.noteError) ({ Text(stringResource(R.string.chord_note_error)) }) else null)
                    OutlinedTextField(value = string.octaveInput, onValueChange = { eventSink(ChordEvent.SetOctave(string.index, it)) },
                        modifier = Modifier.weight(1f).testTag("chord_octave_" + string.index),
                        label = { Text(stringResource(R.string.chord_octave)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, isError = string.octaveError,
                        supportingText = if (string.octaveError) ({ Text(stringResource(R.string.chord_octave_error)) }) else null)
                }
            }
        }
    }
}

@Composable
private fun ChordLookupContent(state: ChordUiState, eventSink: (ChordEvent) -> Unit) {
    ChordPanel {
        ChordHeading(R.string.chord_lookup)
        ChordDropdown(stringResource(R.string.chord_root), state.roots[state.root], state.roots, "chord_root") {
            eventSink(ChordEvent.SetRoot(it))
        }
        ChordDropdown(stringResource(R.string.chord_quality), state.quality.label(), ChordQualityUi.entries.map { it.label() }, "chord_quality") {
            eventSink(ChordEvent.SetQuality(ChordQualityUi.entries[it]))
        }
        FilledTonalButton(onClick = { eventSink(ChordEvent.Search) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("chord_search")) { Text(stringResource(R.string.chord_search)) }
        when (state.lookup) {
            ChordLookupUi.Idle -> Text(stringResource(R.string.chord_lookup_idle), style = MaterialTheme.typography.bodyMedium)
            ChordLookupUi.Searching -> {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("chord_searching"))
                StatusText(stringResource(R.string.chord_searching, state.lookupSymbol.orEmpty()), "chord_lookup_status")
            }
            ChordLookupUi.NoShapes -> StatusText(stringResource(R.string.chord_no_shapes, state.lookupSymbol.orEmpty()), "chord_lookup_status")
            ChordLookupUi.Ready -> {
                Text(state.lookupSymbol.orEmpty(), Modifier.testTag("chord_lookup_summary"), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.chord_shape_symbol, state.lookupShapeSymbol.orEmpty()), style = MaterialTheme.typography.bodyLarge)
                Text(state.lookupNotes, Modifier.testTag("chord_lookup_notes"), style = MaterialTheme.typography.bodyMedium)
                if (state.lookupOmitted.isNotEmpty()) Text(stringResource(R.string.chord_lookup_omitted, state.lookupOmitted),
                    Modifier.testTag("chord_lookup_omitted"), style = MaterialTheme.typography.bodyMedium)
                ChordFretboard(state.lookupStrings, "chord_lookup")
                Text(stringResource(R.string.chord_representative_count, state.representativeIndex + 1, state.representativeCount),
                    Modifier.testTag("chord_representative_count"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { eventSink(ChordEvent.SelectRepresentative(state.representativeIndex - 1)) },
                        enabled = state.representativeIndex > 0, modifier = Modifier.heightIn(min = 48.dp).testTag("chord_previous")) {
                        Text(stringResource(R.string.chord_previous))
                    }
                    OutlinedButton(onClick = { eventSink(ChordEvent.SelectRepresentative(state.representativeIndex + 1)) },
                        enabled = state.representativeIndex + 1 < state.representativeCount,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("chord_next")) { Text(stringResource(R.string.chord_next)) }
                    Button(onClick = { eventSink(ChordEvent.CopyRepresentative) }, modifier = Modifier.heightIn(min = 48.dp).testTag("chord_copy")) {
                        Text(stringResource(R.string.chord_copy))
                    }
                }
            }
        }
        Text(stringResource(R.string.chord_representative_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChordEditor(state: ChordUiState, eventSink: (ChordEvent) -> Unit) {
    ChordPanel {
        ChordHeading(R.string.chord_edit)
        StatusText(stringResource(if (state.unsynced) R.string.chord_draft_unsynced else R.string.chord_draft_synced), "chord_draft_status")
        if (state.unsynced) {
            FilledTonalButton(onClick = { eventSink(ChordEvent.RetryDraftWrite) }, enabled = !state.readFailed,
                modifier = Modifier.heightIn(min = 48.dp).testTag("chord_retry_draft")) { Text(stringResource(R.string.chord_retry_draft)) }
        }
        state.targetName?.let { Text(stringResource(R.string.chord_target, it), style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = { eventSink(ChordEvent.NewDraft) }, modifier = Modifier.heightIn(min = 48.dp).testTag("chord_new")) {
            Text(stringResource(R.string.chord_new))
        }
        ChordSummary(state.analysis, state.soundingSymbol, state.shapeSymbol, state.notes)
        ChordFretboard(state.strings, "chord_editor")
        if (state.candidates.isNotEmpty()) {
            Text(stringResource(R.string.chord_candidates), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.chord_candidate_help), style = MaterialTheme.typography.bodyMedium)
            state.candidates.forEachIndexed { index, candidate ->
                FilterChip(selected = candidate.selected, onClick = { eventSink(ChordEvent.SelectCandidate(candidate.root, candidate.quality)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("chord_candidate_$index"),
                    label = { Text(if (candidate.omitted.isEmpty()) stringResource(R.string.chord_exact_candidate, candidate.symbol)
                        else stringResource(R.string.chord_omitted_candidate, candidate.symbol, candidate.omitted)) })
            }
        }
        state.strings.forEach { ChordStringControls(it, eventSink) }
        OutlinedTextField(value = state.name, onValueChange = { eventSink(ChordEvent.SetName(it)) },
            modifier = Modifier.fillMaxWidth().testTag("chord_name"), label = { Text(stringResource(R.string.chord_name)) },
            isError = state.nameError, supportingText = { Text(stringResource(if (state.nameError) R.string.chord_name_error else R.string.chord_name_help)) })
        Button(onClick = { eventSink(ChordEvent.Save) }, enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chord_save")) {
            Text(stringResource(if (state.busy) R.string.chord_saving else R.string.chord_save))
        }
    }
}

@Composable
private fun ChordStringControls(string: ChordStringUi, eventSink: (ChordEvent) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.chord_string_tuning, 6 - string.index, string.tuning), style = MaterialTheme.typography.titleMedium)
        string.note?.let {
            val degree = string.degree
            Text(if (degree == null) stringResource(R.string.chord_string_note_only, it)
                else stringResource(R.string.chord_string_note, it, degree), style = MaterialTheme.typography.bodyMedium)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ChordStopUi.entries.forEach { stop ->
                val description = stringResource(stop.label)
                SegmentedButton(selected = string.stop == stop, onClick = { eventSink(ChordEvent.SetStop(string.index, stop)) },
                    shape = SegmentedButtonDefaults.itemShape(stop.ordinal, ChordStopUi.entries.size),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("chord_stop_${string.index}_${stop.name}")
                        .semantics { contentDescription = description }) {
                    Text(when (stop) { ChordStopUi.Muted -> "X"; ChordStopUi.Open -> "O"; ChordStopUi.Fretted -> description })
                }
            }
        }
        if (string.stop == ChordStopUi.Fretted) {
            OutlinedTextField(value = string.fretInput, onValueChange = { eventSink(ChordEvent.SetFret(string.index, it)) },
                modifier = Modifier.fillMaxWidth().testTag("chord_fret_" + string.index),
                label = { Text(stringResource(R.string.chord_relative_fret)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = string.fretError,
                supportingText = { Text(stringResource(if (string.fretError) R.string.chord_fret_error else R.string.chord_fret_help)) })
        }
    }
}

@Composable
private fun ChordCollection(state: ChordUiState, eventSink: (ChordEvent) -> Unit) {
    var deleteId by remember { mutableStateOf<String?>(null) }
    ChordHeading(R.string.chord_collection)
    Text(stringResource(R.string.chord_collection_help), style = MaterialTheme.typography.bodyMedium)
    if (state.records.isEmpty()) Text(stringResource(R.string.chord_collection_empty), Modifier.testTag("chord_collection_empty"))
    state.records.forEach { record ->
        ChordPanel {
            Text(record.name, Modifier.testTag("chord_record_" + record.id), style = MaterialTheme.typography.titleLarge)
            Text(record.symbol ?: stringResource(record.analysis.label), style = MaterialTheme.typography.titleMedium)
            Text(record.notes, style = MaterialTheme.typography.bodyMedium)
            Text(record.tuning, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.chord_saved_capo, record.capo), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { eventSink(ChordEvent.Load(record.id)) }, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("chord_load_" + record.id)) { Text(stringResource(R.string.chord_load)) }
                OutlinedButton(onClick = { deleteId = record.id }, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("chord_delete_" + record.id)) { Text(stringResource(R.string.chord_delete)) }
            }
        }
    }
    state.records.firstOrNull { it.id == deleteId }?.let { record ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(stringResource(R.string.chord_delete_title)) },
            text = { Text(stringResource(R.string.chord_delete_description, record.name)) },
            confirmButton = { TextButton(onClick = { eventSink(ChordEvent.Delete(record.id)); deleteId = null },
                modifier = Modifier.heightIn(min = 48.dp).testTag("chord_confirm_delete")) { Text(stringResource(R.string.chord_delete)) } },
            dismissButton = { TextButton(onClick = { deleteId = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.chord_cancel)) } })
    }
}

@Composable
private fun ChordSummary(analysis: ChordAnalysisUi, sounding: String?, shape: String?, notes: String) {
    Text(sounding ?: stringResource(analysis.label), Modifier.testTag("chord_summary").semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.headlineMedium)
    if (analysis == ChordAnalysisUi.Note) Text(stringResource(R.string.chord_single_note_help), style = MaterialTheme.typography.bodyMedium)
    if (analysis == ChordAnalysisUi.Unrecognized) Text(stringResource(R.string.chord_unrecognized_help), style = MaterialTheme.typography.bodyMedium)
    shape?.let { Text(stringResource(R.string.chord_shape_symbol, it), Modifier.testTag("chord_shape_summary"), style = MaterialTheme.typography.bodyLarge) }
    if (notes.isNotEmpty()) Text(notes, Modifier.testTag("chord_notes"), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ChordFretboard(strings: List<ChordStringUi>, tag: String) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Text(stringResource(R.string.chord_fretboard_help), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(tag + "_fretboard")) {
        Row {
            Text(stringResource(R.string.chord_string_short), Modifier.width(64.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium)
            for (fret in 0..12) Box(Modifier.width(72.dp).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                Text(fret.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
        strings.reversed().forEach { string ->
            val position = when (string.stop) {
                ChordStopUi.Muted -> stringResource(R.string.chord_muted)
                ChordStopUi.Open -> stringResource(R.string.chord_open)
                ChordStopUi.Fretted -> stringResource(R.string.chord_fret_position, string.fret)
            }
            val description = listOfNotNull(stringResource(R.string.chord_string_description, 6 - string.index, position),
                string.note?.let { stringResource(R.string.chord_tone_description, it) },
                string.degree?.let { stringResource(R.string.chord_degree_description, it) }).joinToString(", ")
            Row(Modifier.testTag(tag + "_position_" + string.index).clearAndSetSemantics { contentDescription = description }
                .drawBehind {
                    drawLine(line, Offset(64.dp.toPx(), size.height / 2), Offset(size.width, size.height / 2))
                    for (fret in 0..13) {
                        val x = (64 + fret * 72).dp.toPx()
                        drawLine(line, Offset(x, 0f), Offset(x, size.height))
                    }
                }, verticalAlignment = Alignment.CenterVertically) {
                Text((6 - string.index).toString(), Modifier.width(64.dp).padding(8.dp), style = MaterialTheme.typography.labelLarge)
                for (fret in 0..12) {
                    val selected = if (string.stop == ChordStopUi.Muted) fret == 0 else fret == string.fret
                    Box(Modifier.width(72.dp).heightIn(min = 80.dp).padding(4.dp), contentAlignment = Alignment.Center) {
                        if (selected) {
                            val label = when (string.stop) { ChordStopUi.Muted -> "X"; ChordStopUi.Open -> "O"; ChordStopUi.Fretted -> string.fret.toString() }
                            Column(Modifier.background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small).padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                                string.note?.let { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall) }
                                string.degree?.let {
                                    Text(if (it == "1") stringResource(R.string.chord_root_marker) else it,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChordDropdown(label: String, value: String, options: List<String>, tag: String, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag(tag))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(index) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(tag + "_option_" + index))
            }
        }
    }
}

@Composable
private fun ChordPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable private fun ChordHeading(resource: Int) {
    Text(stringResource(resource), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}

@Composable private fun StatusText(value: String, tag: String, error: Boolean = false) {
    Text(value, Modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun ChordQualityUi.label(): String = if (this == ChordQualityUi.Major) stringResource(R.string.chord_major) else symbol
private val ChordSection.label: Int get() = when (this) { ChordSection.Lookup -> R.string.chord_lookup; ChordSection.Edit -> R.string.chord_edit; ChordSection.Collection -> R.string.chord_collection }
private val ChordStopUi.label: Int get() = when (this) { ChordStopUi.Muted -> R.string.chord_muted; ChordStopUi.Open -> R.string.chord_open; ChordStopUi.Fretted -> R.string.chord_fretted }
private val ChordAnalysisUi.label: Int get() = when (this) { ChordAnalysisUi.Empty -> R.string.chord_empty; ChordAnalysisUi.Note -> R.string.chord_single_note; ChordAnalysisUi.Recognized -> R.string.chord_candidates; ChordAnalysisUi.Unrecognized -> R.string.chord_unrecognized }
private val TuningPresetUi.label: Int get() = when (this) { TuningPresetUi.Standard -> R.string.chord_standard; TuningPresetUi.DropD -> R.string.chord_drop_d; TuningPresetUi.Dadgad -> R.string.chord_dadgad; TuningPresetUi.OpenG -> R.string.chord_open_g; TuningPresetUi.OpenD -> R.string.chord_open_d }
private val ChordNotice.label: Int get() = when (this) { ChordNotice.InvalidInput -> R.string.chord_invalid_input; ChordNotice.InvalidName -> R.string.chord_name_error; ChordNotice.EmptyShape -> R.string.chord_empty; ChordNotice.CandidateMissing -> R.string.chord_candidate_missing; ChordNotice.RecordMissing -> R.string.chord_record_missing; ChordNotice.ReadFailed -> R.string.chord_read_failed; ChordNotice.WriteFailed -> R.string.chord_write_failed }
