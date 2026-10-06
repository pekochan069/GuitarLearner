package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.presentation.contract.*
import com.pekochan069.guitarlearner.ui.R
import java.math.BigDecimal

@Composable
fun ProgressionTool(state: ProgressionUiState, eventSink: (ProgressionEvent) -> Unit, metronomeStatus: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val shortViewport = maxHeight < 300.dp
        val wideViewport = maxWidth >= 600.dp
        val playbackHeight = maxHeight * 0.5f
        val showingPlayback = state.transport in listOf(ProgressionTransportUi.Playing, ProgressionTransportUi.Paused)
        Column(Modifier.fillMaxSize().padding(16.dp).testTag("progression_tool"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!shortViewport) ProgressionSequenceHeader(state, eventSink)
            if (showingPlayback && wideViewport) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProgressionSequence(state, eventSink, Modifier.weight(1f).fillMaxHeight()) {
                        if (shortViewport) ProgressionSequenceHeader(state, eventSink)
                        metronomeStatus()
                        ProgressionWarnings(state, eventSink)
                    }
                    ProgressionPlaybackPane(state, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                if (showingPlayback) ProgressionPlaybackPane(state, Modifier.fillMaxWidth().height(playbackHeight))
                ProgressionSequence(state, eventSink, Modifier.fillMaxWidth().weight(1f)) {
                    if (shortViewport) ProgressionSequenceHeader(state, eventSink)
                    metronomeStatus()
                    ProgressionWarnings(state, eventSink)
                }
            }
        }
    }
    ProgressionSheets(state, eventSink)
}

@Composable
private fun ProgressionSequenceHeader(state: ProgressionUiState, eventSink: (ProgressionEvent) -> Unit) {
    TextButton(onClick = { eventSink(ProgressionEvent.OpenSheet(ProgressionSheetUi.Settings)) }, modifier = Modifier.testTag("progression_settings")) {
        Text(if (state.invalidSettings) stringResource(R.string.progression_settings_error)
            else "${stringResource(R.string.progression_bpm_summary, state.bpm)} · ${state.numerator}/${state.denominator.denominator}",
            color = if (state.invalidSettings) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.progression_settings), Modifier.size(18.dp))
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { eventSink(ProgressionEvent.OpenEditor()) }, enabled = !state.busy && !state.readFailed,
            modifier = Modifier.heightIn(min = 48.dp).testTag("progression_add_chord")) { Text(stringResource(R.string.progression_add_chord)) }
        OutlinedButton(onClick = { eventSink(ProgressionEvent.AddRest) }, enabled = !state.busy && !state.readFailed,
            modifier = Modifier.heightIn(min = 48.dp).testTag("progression_add_rest")) { Text(stringResource(R.string.progression_add_rest)) }
    }
}

@Composable
private fun ProgressionPlaybackPane(state: ProgressionUiState, modifier: Modifier) {
    val step = state.steps.getOrNull(state.playingIndex)
    Surface(modifier.testTag("progression_playback_pane"), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                step == null -> Text(stringResource(R.string.progression_count_in, (state.countInBeat ?: 0) + 1, state.numerator),
                    Modifier.testTag("progression_active_count_in"), style = MaterialTheme.typography.titleLarge)
                step.rest -> Text(step.title(), Modifier.testTag("progression_active_rest"), style = MaterialTheme.typography.titleLarge)
                else -> Column(Modifier.testTag("progression_active_chord"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(step.title(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("progression_active_name"))
                    ChordFretboard(step.strings, "progression_active", state.editor.capo, showLegend = false)
                }
            }
        }
    }
}

@Composable
internal fun ProgressionAppBarActions(state: ProgressionUiState, eventSink: (ProgressionEvent) -> Unit, preferences: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { eventSink(ProgressionEvent.OpenSheet(ProgressionSheetUi.Save)) },
        enabled = state.steps.isNotEmpty() && !state.busy && !state.readFailed && !state.invalidSettings,
        modifier = Modifier.testTag("progression_open_save")) {
        Icon(painterResource(R.drawable.ic_save), stringResource(R.string.progression_save))
    }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("progression_actions")) {
            Icon(painterResource(R.drawable.ic_more), stringResource(R.string.progression_actions))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.progression_settings)) }, onClick = {
                expanded = false; eventSink(ProgressionEvent.OpenSheet(ProgressionSheetUi.Settings))
            }, modifier = Modifier.testTag("progression_context"))
            DropdownMenuItem(text = { Text(stringResource(R.string.progression_collection)) }, onClick = {
                expanded = false; eventSink(ProgressionEvent.OpenSheet(ProgressionSheetUi.Collection))
            }, modifier = Modifier.testTag("progression_collection"))
            DropdownMenuItem(text = { Text(stringResource(R.string.progression_new)) }, enabled = !state.busy && !state.readFailed, onClick = {
                expanded = false; eventSink(ProgressionEvent.NewDraft)
            }, modifier = Modifier.testTag("progression_new"))
            DropdownMenuItem(text = { Text(stringResource(R.string.preferences)) }, onClick = { expanded = false; preferences() },
                modifier = Modifier.testTag("settings"))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressionTransportBar(state: ProgressionUiState, eventSink: (ProgressionEvent) -> Unit) {
    val height = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.lineHeight.toDp() * 2 + 32.dp }.coerceAtLeast(80.dp)
    Column {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        when {
            state.transport == ProgressionTransportUi.Paused -> ProgressionStatus(stringResource(R.string.progression_paused), "progression_paused")
            state.countInBeat != null -> ProgressionStatus(stringResource(R.string.progression_count_in, (state.countInBeat ?: 0) + 1, state.numerator), "progression_count_in")
            state.transport == ProgressionTransportUi.Preparing -> ProgressionStatus(stringResource(R.string.progression_preparing), "progression_preparing")
        }
        if (state.pendingChange) ProgressionStatus(stringResource(R.string.progression_pending), "progression_pending")
        }
        FlexibleBottomAppBar(modifier = Modifier.testTag("progression_transport"), expandedHeight = height,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val paused = state.transport == ProgressionTransportUi.Paused
            val playing = state.transport == ProgressionTransportUi.Playing
            val tag = when { playing -> "progression_pause"; paused -> "progression_resume"; else -> "progression_play" }
            Button(onClick = { eventSink(when { playing -> ProgressionEvent.Pause; paused -> ProgressionEvent.Resume; else -> ProgressionEvent.Play() }) },
                enabled = playing || (state.canPlay && state.transport != ProgressionTransportUi.Preparing),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(tag), contentPadding = PaddingValues(8.dp)) {
                Text(stringResource(when { playing -> R.string.progression_pause; paused -> R.string.progression_resume; else -> R.string.progression_play }),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            OutlinedButton(onClick = { eventSink(ProgressionEvent.Stop) },
                enabled = state.transport in listOf(ProgressionTransportUi.Playing, ProgressionTransportUi.Paused, ProgressionTransportUi.Preparing),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("progression_stop"), contentPadding = PaddingValues(8.dp)) {
                Text(stringResource(R.string.progression_stop), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            FilterChip(state.loop, { eventSink(ProgressionEvent.SetLoop(!state.loop)) }, label = { Text(stringResource(R.string.progression_loop_short)) },
                enabled = !state.busy && !state.readFailed, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("progression_loop"))
        }
    }
}

@Composable
private fun ProgressionWarnings(state: ProgressionUiState, eventSink: (ProgressionEvent) -> Unit) {
    state.notice?.let { ProgressionStatus(stringResource(it.label), "progression_error", true) }
    state.stopReason?.takeUnless { it == MetronomeStopUi.User }?.let {
        ProgressionStatus(stringResource(when (it) { MetronomeStopUi.FocusLoss -> R.string.progression_focus_lost
            MetronomeStopUi.OutputDisconnected -> R.string.progression_output_removed; else -> R.string.progression_service_ended }), "progression_interruption", true)
    }
    if (state.invalidSettings) {
        ProgressionStatus(stringResource(R.string.progression_settings_invalid), "progression_context_error", true)
        TextButton(onClick = { eventSink(ProgressionEvent.OpenSheet(ProgressionSheetUi.Settings)) }, modifier = Modifier.testTag("progression_fix_settings")) {
            Text(stringResource(R.string.progression_settings))
        }
    }
    if (state.readFailed) {
        ProgressionStatus(stringResource(R.string.progression_read_failed), "progression_read_failed", true)
        FilledTonalButton(onClick = { eventSink(ProgressionEvent.RetryStorageRead) }, modifier = Modifier.testTag("progression_retry_read")) { Text(stringResource(R.string.chord_retry_read)) }
    }
    if (state.unsynced) {
        ProgressionStatus(stringResource(R.string.progression_unsynced), "progression_unsynced", true)
        FilledTonalButton(onClick = { eventSink(ProgressionEvent.RetryDraftWrite) }, enabled = !state.readFailed && !state.busy,
            modifier = Modifier.testTag("progression_retry_draft")) { Text(stringResource(R.string.chord_retry_draft)) }
    }
}

@Composable internal fun ProgressionStepUi.title(): String = when {
    rest -> stringResource(R.string.progression_rest)
    shape != null && sounding != null && shape != sounding -> "$shape → $sounding"
    else -> shape ?: sounding ?: name.ifBlank { stringResource(R.string.chord_unrecognized) }
}

@Composable internal fun NoteValueUi.label(): String = stringResource(when (this) {
    NoteValueUi.Whole -> R.string.progression_whole; NoteValueUi.Half -> R.string.beat_half; NoteValueUi.Quarter -> R.string.beat_quarter
    NoteValueUi.Eighth -> R.string.beat_eighth; NoteValueUi.Sixteenth -> R.string.beat_sixteenth; NoteValueUi.ThirtySecond -> R.string.progression_thirty_second
})
@Composable internal fun ProgressionStepUi.durationLabel(unit: BeatUnitUi): String {
    val count = BigDecimal(unit.denominator).divide(BigDecimal(duration.denominator)).multiply(if (dotted) BigDecimal("1.5") else BigDecimal.ONE)
    val note = if (dotted) stringResource(R.string.progression_dotted_note, duration.label()) else duration.label()
    return stringResource(R.string.progression_duration_summary, count.stripTrailingZeros().toPlainString(), note)
}
@Composable internal fun ProgressionStepUi.noteLabel(): String = if (dotted) stringResource(R.string.progression_dotted_note, duration.label()) else duration.label()
@Composable internal fun ProgressionStatus(value: String, tag: String, error: Boolean = false) {
    Text(value, Modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}
internal val ProgressionNotice.label: Int get() = when (this) {
    ProgressionNotice.InvalidInput -> R.string.chord_invalid_input; ProgressionNotice.InvalidName -> R.string.chord_name_error
    ProgressionNotice.EmptyShape -> R.string.chord_empty; ProgressionNotice.InvalidTie -> R.string.progression_invalid_tie
    ProgressionNotice.EmptyProgression -> R.string.progression_empty; ProgressionNotice.RecordMissing -> R.string.chord_record_missing
    ProgressionNotice.ReadFailed -> R.string.progression_read_failed; ProgressionNotice.WriteFailed -> R.string.progression_write_failed
    ProgressionNotice.FocusDenied -> R.string.progression_focus_denied; ProgressionNotice.ServiceUnavailable -> R.string.progression_service_failed
    ProgressionNotice.AudioUnavailable -> R.string.progression_audio_failed
    ProgressionNotice.ShapeChanged -> R.string.progression_shape_changed
}
