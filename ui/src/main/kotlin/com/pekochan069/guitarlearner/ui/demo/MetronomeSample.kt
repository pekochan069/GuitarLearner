package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.presentation.contract.BeatAccentUi
import com.pekochan069.guitarlearner.presentation.contract.BeatUnitUi
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.MetronomeConfigUi
import com.pekochan069.guitarlearner.presentation.contract.MetronomeNotice
import com.pekochan069.guitarlearner.presentation.contract.MetronomePlaybackUi
import com.pekochan069.guitarlearner.presentation.contract.MetronomeStopUi
import com.pekochan069.guitarlearner.presentation.contract.MetronomeUiState
import com.pekochan069.guitarlearner.ui.R
import kotlin.math.roundToInt

private val BeatUnitUi.label: Int get() = when (this) {
    BeatUnitUi.Half -> R.string.beat_half
    BeatUnitUi.Quarter -> R.string.beat_quarter
    BeatUnitUi.Eighth -> R.string.beat_eighth
    BeatUnitUi.Sixteenth -> R.string.beat_sixteenth
}

private val BeatUnitUi.symbol: Int get() = when (this) {
    BeatUnitUi.Half -> R.string.note_half
    BeatUnitUi.Quarter -> R.string.note_quarter
    BeatUnitUi.Eighth -> R.string.note_eighth
    BeatUnitUi.Sixteenth -> R.string.note_sixteenth
}

private val BeatAccentUi.label: Int get() = when (this) {
    BeatAccentUi.Accent -> R.string.beat_accent
    BeatAccentUi.Normal -> R.string.beat_normal
    BeatAccentUi.Mute -> R.string.beat_mute
}

private val MetronomeNotice.label: Int get() = when (this) {
    MetronomeNotice.InvalidConfiguration -> R.string.metronome_invalid_configuration
    MetronomeNotice.InvalidName -> R.string.preset_invalid_name
    MetronomeNotice.PresetExists -> R.string.preset_exists
    MetronomeNotice.PresetMissing -> R.string.preset_missing
    MetronomeNotice.ReadFailed -> R.string.metronome_read_failed
    MetronomeNotice.SaveFailed -> R.string.metronome_save_failed
    MetronomeNotice.FocusDenied -> R.string.metronome_focus_denied
    MetronomeNotice.ServiceUnavailable -> R.string.metronome_service_unavailable
    MetronomeNotice.AudioUnavailable -> R.string.metronome_audio_unavailable
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MetronomeSample(state: MetronomeUiState, eventSink: (FoundationEvent) -> Unit): Unit {
    val config = state.config
    val playback = state.playback
    val playing = playback as? MetronomePlaybackUi.Playing
    val active = playing != null || playback == MetronomePlaybackUi.Preparing
    val status = when (playback) {
        MetronomePlaybackUi.Preparing -> R.string.state_preparing
        is MetronomePlaybackUi.Playing -> R.string.state_running
        is MetronomePlaybackUi.Failed -> R.string.state_playback_failed
        is MetronomePlaybackUi.Stopped -> when (playback.reason) {
            MetronomeStopUi.FocusLoss -> R.string.state_focus_interrupted
            MetronomeStopUi.OutputDisconnected -> R.string.state_output_disconnected
            MetronomeStopUi.ServiceEnded -> R.string.state_service_ended
            MetronomeStopUi.User, null -> R.string.state_stopped
        }
    }
    val tempoDescription = pluralStringResource(R.plurals.tempo_bpm, config.bpm, config.bpm)
    val tempoNoteDescription = stringResource(R.string.tempo_note_description, stringResource(config.denominator.label), tempoDescription)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.tempo), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.tempo_value, config.bpm),
                Modifier.testTag("bpm_value"),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.tempo_note, stringResource(config.denominator.symbol), config.bpm),
                Modifier.clearAndSetSemantics { contentDescription = tempoNoteDescription },
                style = MaterialTheme.typography.titleMedium,
            )
            BeatIndicators(playing?.config ?: config, playing?.beatIndex)
            Text(
                stringResource(R.string.metronome_meter, (playing?.config ?: config).numerator, (playing?.config ?: config).denominator.denominator),
                style = MaterialTheme.typography.labelLarge,
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    stringResource(status),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("metronome_status")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.pendingChange) {
                Text(
                    stringResource(R.string.metronome_pending),
                    Modifier.testTag("metronome_pending").semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (playing != null) {
                    Text(
                        stringResource(R.string.metronome_active_config, playing.config.numerator, playing.config.denominator.denominator, playing.config.bpm),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    Button(
        onClick = { eventSink(FoundationEvent.SetRunning(!active)) },
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.LargeContainerHeight),
        contentPadding = ButtonDefaults.LargeContentPadding,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("toggle_metronome"),
    ) {
        Icon(painterResource(if (active) R.drawable.ic_stop else R.drawable.ic_play), null, Modifier.padding(end = 12.dp).size(32.dp))
        Text(stringResource(if (active) R.string.stop_metronome else R.string.start_metronome), style = MaterialTheme.typography.titleLarge)
    }
    val notice = state.notice ?: (playback as? MetronomePlaybackUi.Failed)?.notice
    if (notice != null) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(notice.label),
                    Modifier.testTag("metronome_error").semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (state.notice != null) {
                    TextButton(onClick = { eventSink(FoundationEvent.DismissMetronomeNotice) }) { Text(stringResource(R.string.dismiss_notice)) }
                }
            }
        }
    }
    SectionHeading(R.string.set_tempo)
    val tempoLabel = stringResource(R.string.tempo)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { eventSink(FoundationEvent.AdjustBpm(-1)) },
            enabled = config.bpm > 40,
            modifier = Modifier.size(56.dp).testTag("decrease_bpm"),
        ) { Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.decrease_tempo)) }
        Slider(
            value = config.bpm.toFloat(),
            onValueChange = { eventSink(FoundationEvent.SetBpm(it.roundToInt())) },
            valueRange = 40f..240f,
            steps = 199,
            modifier = Modifier.weight(1f).testTag("tempo_slider").semantics {
                contentDescription = tempoLabel
                stateDescription = tempoDescription
            },
        )
        FilledTonalIconButton(
            onClick = { eventSink(FoundationEvent.AdjustBpm(1)) },
            enabled = config.bpm < 240,
            modifier = Modifier.size(56.dp).testTag("increase_bpm"),
        ) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.increase_tempo)) }
    }
    Text(stringResource(R.string.tempo_range), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SectionHeading(R.string.time_signature)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { eventSink(FoundationEvent.AdjustBeatCount(-1)) },
            enabled = config.numerator > 1,
            modifier = Modifier.size(56.dp).testTag("decrease_beats"),
        ) { Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.decrease_beats)) }
        Text(pluralStringResource(R.plurals.beats_per_bar, config.numerator, config.numerator), Modifier.weight(1f).testTag("beat_count"), style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton(
            onClick = { eventSink(FoundationEvent.AdjustBeatCount(1)) },
            enabled = config.numerator < 16,
            modifier = Modifier.size(56.dp).testTag("increase_beats"),
        ) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.increase_beats)) }
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        BeatUnitUi.entries.forEachIndexed { index, unit ->
            val label = stringResource(unit.label)
            SegmentedButton(
                selected = config.denominator == unit,
                onClick = { eventSink(FoundationEvent.SetBeatUnit(unit)) },
                shape = SegmentedButtonDefaults.itemShape(index, BeatUnitUi.entries.size),
                modifier = Modifier.heightIn(min = 48.dp).testTag("beat_unit_" + unit.denominator).semantics { contentDescription = label },
                icon = {},
            ) { Text(stringResource(R.string.tempo_value, unit.denominator)) }
        }
    }
    Text(stringResource(R.string.beat_unit_description, stringResource(config.denominator.label)), style = MaterialTheme.typography.bodyMedium)
    SectionHeading(R.string.customize_accents)
    Text(stringResource(R.string.accent_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        config.beats.forEachIndexed { index, accent ->
            val description = stringResource(R.string.beat_edit_description, index + 1, stringResource(accent.label))
            FilledTonalButton(
                onClick = { eventSink(FoundationEvent.CycleBeatAccent(index)) },
                modifier = Modifier.heightIn(min = 56.dp).testTag("beat_accent_" + (index + 1)).semantics { contentDescription = description },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.tempo_value, index + 1), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(accent.label), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    SectionHeading(R.string.presets)
    OutlinedTextField(
        value = state.presetName,
        onValueChange = { eventSink(FoundationEvent.SetPresetName(it)) },
        label = { Text(stringResource(R.string.preset_name)) },
        singleLine = true,
        isError = state.notice == MetronomeNotice.InvalidName,
        enabled = !state.savingPreset,
        modifier = Modifier.fillMaxWidth().testTag("preset_name"),
    )
    FilledTonalButton(
        onClick = { eventSink(FoundationEvent.SavePreset) },
        enabled = state.presetName.isNotBlank() && !state.savingPreset,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_preset"),
    ) { Text(stringResource(if (state.savingPreset) R.string.preset_saving else R.string.save_preset)) }
    if (state.presets.isEmpty()) {
        Text(stringResource(R.string.presets_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.presets.forEach { preset ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.preset_configuration, preset.config.numerator, preset.config.denominator.denominator, preset.config.bpm), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val loadDescription = stringResource(R.string.load_preset_description, preset.name)
                    val deleteDescription = stringResource(R.string.delete_preset_description, preset.name)
                    FilledTonalButton(
                        onClick = { eventSink(FoundationEvent.LoadPreset(preset.name)) },
                        enabled = !state.savingPreset,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("load_preset_" + preset.name).semantics { contentDescription = loadDescription },
                    ) { Text(stringResource(R.string.load_preset)) }
                    TextButton(
                        onClick = { eventSink(FoundationEvent.DeletePreset(preset.name)) },
                        enabled = !state.savingPreset,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("delete_preset_" + preset.name).semantics { contentDescription = deleteDescription },
                    ) { Text(stringResource(R.string.delete_preset), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    state.overwriteName?.let { name ->
        AlertDialog(
            onDismissRequest = { eventSink(FoundationEvent.CancelPresetOverwrite) },
            title = { Text(stringResource(R.string.overwrite_preset_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.overwrite_preset_description, name))
                    state.notice?.let { Text(stringResource(it.label), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { eventSink(FoundationEvent.ConfirmPresetOverwrite) },
                    enabled = !state.savingPreset,
                    modifier = Modifier.testTag("confirm_preset_overwrite"),
                ) { Text(stringResource(R.string.overwrite_preset)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { eventSink(FoundationEvent.CancelPresetOverwrite) },
                    enabled = !state.savingPreset,
                    modifier = Modifier.testTag("cancel_preset_overwrite"),
                ) { Text(stringResource(R.string.cancel_action)) }
            },
        )
    }
}

@Composable
private fun BeatIndicators(config: MetronomeConfigUi, beatIndex: Int?): Unit {
    val description = if (beatIndex != null) {
        stringResource(R.string.current_beat_description, beatIndex + 1, config.numerator, stringResource(config.beats[beatIndex].label))
    } else {
        stringResource(R.string.metronome_meter, config.numerator, config.denominator.denominator)
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("beat_indicators").clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        config.beats.forEachIndexed { index, accent ->
            val active = index == beatIndex
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.tempo_value, index + 1), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(accent.label), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    Text(
        if (beatIndex == null) stringResource(R.string.current_beat_ready)
        else stringResource(R.string.current_beat_number, beatIndex + 1, config.numerator),
        Modifier.testTag("current_beat"),
        style = MaterialTheme.typography.labelLarge,
        minLines = 1,
        maxLines = 1,
    )
}
