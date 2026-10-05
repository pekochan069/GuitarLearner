package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordTool(state: ChordUiState, eventSink: (ChordEvent) -> Unit) {
    var contextOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("chord_tool"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PrimaryScrollableTabRow(selectedTabIndex = state.section.ordinal, edgePadding = 0.dp) {
            ChordSection.entries.forEach { section ->
                Tab(selected = state.section == section, onClick = { eventSink(ChordEvent.SetSection(section)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("chord_section_" + section.name),
                    text = { Text(stringResource(section.label), maxLines = 1) })
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
        when (state.section) {
            ChordSection.Lookup -> ChordLookupContent(state, eventSink) { contextOpen = true }
            ChordSection.Edit -> ChordEditor(state, eventSink) { contextOpen = true }
            ChordSection.Collection -> ChordCollection(state, eventSink)
        }
    }
    if (contextOpen) {
        ModalBottomSheet(onDismissRequest = { contextOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag("chord_context_sheet")) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.chord_context), Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { contextOpen = false },
                        modifier = Modifier.size(48.dp).testTag("chord_close_context")) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.chord_close_context))
                    }
                }
                ChordContextControls(state, eventSink)
            }
        }
    }
}

@Composable
private fun ChordContextControls(state: ChordUiState, eventSink: (ChordEvent) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChordDropdown(stringResource(R.string.chord_tuning),
            state.preset?.let { stringResource(it.label) } ?: stringResource(R.string.chord_custom_tuning),
            TuningPresetUi.entries.map { stringResource(it.label) }, "chord_tuning") { eventSink(ChordEvent.SetPreset(TuningPresetUi.entries[it])) }
        OutlinedTextField(value = state.capoInput, onValueChange = { eventSink(ChordEvent.SetCapo(it)) },
            modifier = Modifier.fillMaxWidth().testTag("chord_capo"), label = { Text(stringResource(R.string.chord_capo)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = state.capoError, singleLine = true,
            supportingText = if (state.capoError) ({ Text(stringResource(R.string.chord_capo_error)) }) else null)
        if (state.capoError) Text(stringResource(R.string.chord_accepted_capo, state.capo),
            Modifier.testTag("chord_accepted_capo"), style = MaterialTheme.typography.bodyMedium)
        Text(state.strings.joinToString(" · ") { it.tuning }, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { eventSink(ChordEvent.SetTuningExpanded(!state.tuningExpanded)) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("chord_custom_tuning")) {
            Text(stringResource(if (state.tuningExpanded) R.string.chord_hide_tuning else R.string.chord_edit_tuning))
        }
        if (state.tuningExpanded) {
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
private fun ChordContextButton(state: ChordUiState, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).testTag("chord_open_context")) {
        Text(stringResource(R.string.chord_context_summary,
            state.preset?.let { stringResource(it.label) } ?: stringResource(R.string.chord_custom_tuning), state.capo))
    }
}

@Composable
private fun ChordContextError(state: ChordUiState) {
    if (state.capoError || state.strings.any { it.noteError || it.octaveError }) {
        StatusText(stringResource(R.string.chord_context_error), "chord_context_error", error = true)
    }
}

@Composable
private fun ChordLookupContent(state: ChordUiState, eventSink: (ChordEvent) -> Unit, openContext: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val fieldWidth = 144.dp * LocalDensity.current.fontScale
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChordDropdown(stringResource(R.string.chord_root), state.roots[state.root], state.roots, "chord_root",
                modifier = Modifier.width(fieldWidth).weight(1f)) { eventSink(ChordEvent.SetRoot(it)) }
            ChordDropdown(stringResource(R.string.chord_quality), state.quality.label(), ChordQualityUi.entries.map { it.label() }, "chord_quality",
                modifier = Modifier.width(fieldWidth).weight(1f)) { eventSink(ChordEvent.SetQuality(ChordQualityUi.entries[it])) }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChordContextButton(state, openContext)
            FilledTonalButton(onClick = { eventSink(ChordEvent.Search) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("chord_search")) { Text(stringResource(R.string.chord_search)) }
        }
        ChordContextError(state)
        when (state.lookup) {
            ChordLookupUi.Idle -> Unit
            ChordLookupUi.Searching -> {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("chord_searching"))
                StatusText(stringResource(R.string.chord_searching, state.lookupSymbol.orEmpty()), "chord_lookup_status")
            }
            ChordLookupUi.NoShapes -> StatusText(stringResource(R.string.chord_no_shapes, state.lookupSymbol.orEmpty()), "chord_lookup_status")
            ChordLookupUi.Ready -> {
                Text(state.lookupSymbol.orEmpty(), Modifier.testTag("chord_lookup_summary"), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.chord_shape_symbol, state.lookupShapeSymbol.orEmpty()), style = MaterialTheme.typography.bodySmall)
                Text(state.lookupNotes, Modifier.testTag("chord_lookup_notes"), style = MaterialTheme.typography.bodyMedium)
                if (state.lookupOmitted.isNotEmpty()) Text(stringResource(R.string.chord_lookup_omitted, state.lookupOmitted),
                    Modifier.testTag("chord_lookup_omitted"), style = MaterialTheme.typography.bodyMedium)
                ChordFretboard(state.lookupStrings, "chord_lookup")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { eventSink(ChordEvent.SelectRepresentative(state.representativeIndex - 1)) },
                        enabled = state.representativeIndex > 0, modifier = Modifier.size(48.dp).testTag("chord_previous")) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.chord_previous))
                    }
                    Text(stringResource(R.string.chord_representative_count, state.representativeIndex + 1, state.representativeCount),
                        Modifier.weight(1f).testTag("chord_representative_count"), style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center)
                    IconButton(onClick = { eventSink(ChordEvent.SelectRepresentative(state.representativeIndex + 1)) },
                        enabled = state.representativeIndex + 1 < state.representativeCount,
                        modifier = Modifier.size(48.dp).testTag("chord_next")) {
                        Icon(painterResource(R.drawable.ic_arrow_forward), stringResource(R.string.chord_next))
                    }
                }
                Button(onClick = { eventSink(ChordEvent.CopyRepresentative) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chord_copy")) { Text(stringResource(R.string.chord_copy)) }
            }
        }
    }
}

@Composable
private fun ChordEditor(state: ChordUiState, eventSink: (ChordEvent) -> Unit, openContext: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.unsynced) {
            StatusText(stringResource(R.string.chord_draft_unsynced), "chord_draft_status", error = true)
            FilledTonalButton(onClick = { eventSink(ChordEvent.RetryDraftWrite) }, enabled = !state.readFailed,
                modifier = Modifier.heightIn(min = 48.dp).testTag("chord_retry_draft")) { Text(stringResource(R.string.chord_retry_draft)) }
        }
        state.targetName?.let { Text(stringResource(R.string.chord_target, it), style = MaterialTheme.typography.bodyMedium) }
        ChordSummary(state.analysis, state.soundingSymbol, state.shapeSymbol, state.notes)
        ChordFretboard(state.strings, "chord_editor")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChordContextButton(state, openContext)
            OutlinedButton(onClick = { eventSink(ChordEvent.NewDraft) }, modifier = Modifier.heightIn(min = 48.dp).testTag("chord_new")) {
                Text(stringResource(R.string.chord_new))
            }
        }
        ChordContextError(state)
        if (state.candidates.isNotEmpty()) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.candidates.forEachIndexed { index, candidate ->
                    FilterChip(selected = candidate.selected, onClick = { eventSink(ChordEvent.SelectCandidate(candidate.root, candidate.quality)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("chord_candidate_$index"),
                        label = { Text(if (candidate.omitted.isEmpty()) stringResource(R.string.chord_exact_candidate, candidate.symbol)
                            else stringResource(R.string.chord_omitted_candidate, candidate.symbol, candidate.omitted)) })
                }
            }
        }
        OutlinedTextField(value = state.name, onValueChange = { eventSink(ChordEvent.SetName(it)) },
            modifier = Modifier.fillMaxWidth().testTag("chord_name"), label = { Text(stringResource(R.string.chord_name)) },
            isError = state.nameError, supportingText = if (state.nameError) ({ Text(stringResource(R.string.chord_name_error)) }) else null)
        Button(onClick = { eventSink(ChordEvent.Save) }, enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chord_save")) {
            Text(stringResource(if (state.busy) R.string.chord_saving else R.string.chord_save))
        }
        state.strings.forEach { ChordStringControls(it, eventSink) }
    }
}

@Composable
private fun ChordStringControls(string: ChordStringUi, eventSink: (ChordEvent) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.chord_string_tuning, 6 - string.index, string.tuning), style = MaterialTheme.typography.titleSmall)
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
        if (string.stop == ChordStopUi.Fretted || string.fretError) {
            OutlinedTextField(value = string.fretInput, onValueChange = { eventSink(ChordEvent.SetFret(string.index, it)) },
                modifier = Modifier.fillMaxWidth().testTag("chord_fret_" + string.index),
                label = { Text(stringResource(R.string.chord_relative_fret)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = string.fretError,
                supportingText = if (string.fretError) ({ Text(stringResource(R.string.chord_fret_error)) }) else null)
        }
    }
}

@Composable
private fun ChordCollection(state: ChordUiState, eventSink: (ChordEvent) -> Unit) {
    var deleteId by remember { mutableStateOf<String?>(null) }
    if (state.records.isEmpty() && !state.readFailed) Text(stringResource(R.string.chord_collection_empty), Modifier.testTag("chord_collection_empty"))
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
        style = MaterialTheme.typography.headlineSmall)
    if (analysis == ChordAnalysisUi.Unrecognized) Text(stringResource(R.string.chord_unrecognized_help), style = MaterialTheme.typography.bodyMedium)
    shape?.let { Text(stringResource(R.string.chord_shape_symbol, it), Modifier.testTag("chord_shape_summary"), style = MaterialTheme.typography.bodySmall) }
    if (notes.isNotEmpty()) Text(notes, Modifier.testTag("chord_notes"), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ChordFretboard(strings: List<ChordStringUi>, tag: String) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val fontScale = LocalDensity.current.fontScale
    val stringWidth = 48.dp
    val headerHeight = 44.dp * fontScale
    val fretHeight = 36.dp * fontScale
    val selectedFrets = strings.filter { it.stop == ChordStopUi.Fretted }.map { it.fret }
    val lastFret = maxOf(4, selectedFrets.maxOrNull() ?: 4)
    val firstFret = minOf(selectedFrets.minOrNull() ?: 1, lastFret - 3)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.chord_fretboard_help), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(Modifier.horizontalScroll(rememberScrollState()).testTag(tag + "_fretboard")) {
                Column(Modifier.width(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(headerHeight))
                    for (fret in firstFret..lastFret) {
                        Box(Modifier.height(fretHeight), contentAlignment = Alignment.Center) {
                            Text(fret.toString(), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                strings.forEach { string ->
                    val position = when (string.stop) {
                        ChordStopUi.Muted -> stringResource(R.string.chord_muted)
                        ChordStopUi.Open -> stringResource(R.string.chord_open)
                        ChordStopUi.Fretted -> stringResource(R.string.chord_fret_position, string.fret)
                    }
                    val description = listOfNotNull(stringResource(R.string.chord_string_description, 6 - string.index, position),
                        string.note?.let { stringResource(R.string.chord_tone_description, it) },
                        string.degree?.let { stringResource(R.string.chord_degree_description, it) }).joinToString(", ")
                    Column(Modifier.width(stringWidth).testTag(tag + "_position_" + string.index)
                        .clearAndSetSemantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.height(headerHeight), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly) {
                            Text((6 - string.index).toString(), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(when (string.stop) { ChordStopUi.Muted -> "X"; ChordStopUi.Open -> "O"; ChordStopUi.Fretted -> " " },
                                style = MaterialTheme.typography.titleMedium)
                        }
                        for (fret in firstFret..lastFret) {
                            Box(Modifier.fillMaxWidth().height(fretHeight).drawBehind {
                                drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
                                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                                if (fret == firstFret) drawLine(line, Offset.Zero, Offset(size.width, 0f),
                                    if (firstFret == 1) 2.dp.toPx() else 1.dp.toPx())
                            }, contentAlignment = Alignment.Center) {
                                if (string.stop == ChordStopUi.Fretted && string.fret == fret) {
                                    Box(Modifier.size((26.dp * fontScale).coerceAtMost(44.dp))
                                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraLarge),
                                        contentAlignment = Alignment.Center) {
                                        Text(string.degree ?: "●", color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        Column(Modifier.heightIn(min = 40.dp * fontScale).padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            string.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            string.degree?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChordDropdown(label: String, value: String, options: List<String>, tag: String,
    modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag(tag), singleLine = true)
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
