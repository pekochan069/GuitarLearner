package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.GuitarStringUi
import com.pekochan069.guitarlearner.presentation.contract.ToleranceUi
import com.pekochan069.guitarlearner.presentation.contract.TunerActionUi
import com.pekochan069.guitarlearner.presentation.contract.TunerFeedbackUi
import com.pekochan069.guitarlearner.presentation.contract.TunerJudgmentUi
import com.pekochan069.guitarlearner.presentation.contract.TunerListeningUi
import com.pekochan069.guitarlearner.presentation.contract.TunerNoticeUi
import com.pekochan069.guitarlearner.presentation.contract.TunerTargetUi
import com.pekochan069.guitarlearner.presentation.contract.TunerUiState
import com.pekochan069.guitarlearner.ui.R
import kotlin.math.roundToInt

@Composable
fun TunerScreen(state: TunerUiState, eventSink: (FoundationEvent) -> Unit) {
    val active = state.listening == TunerListeningUi.Starting || state.listening is TunerListeningUi.Listening
    val failure = state.listening as? TunerListeningUi.Failed
    TunerReading(state)
    if (failure == null || TunerActionUi.Retry in failure.actions) {
        Button(
            onClick = { eventSink(if (active) FoundationEvent.StopTuner else FoundationEvent.StartTuner) },
            enabled = state.listening != TunerListeningUi.Stopping,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(if (active) "tuner_stop" else "tuner_start"),
        ) {
            Text(stringResource(when {
                active -> R.string.tuner_stop
                state.listening == TunerListeningUi.Stopping -> R.string.tuner_stopping
                failure != null -> R.string.tuner_retry
                else -> R.string.tuner_start
            }))
        }
    }
    if (failure != null) {
        Text(stringResource(failure.notice.label), Modifier.testTag("tuner_failure"),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            failure.actions.filter { it != TunerActionUi.Retry }.forEach { action ->
                TextButton(onClick = { eventSink(FoundationEvent.OpenTunerSettings(action)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("tuner_recovery_" + action.name)) {
                    Text(stringResource(when (action) {
                        TunerActionUi.AppSettings -> R.string.tuner_app_settings
                        TunerActionUi.PrivacySettings -> R.string.tuner_privacy_settings
                        TunerActionUi.Retry -> R.string.tuner_retry
                    }))
                }
            }
        }
    }
    SectionHeading(R.string.tuner_target)
    Text(stringResource(R.string.tuner_target_description), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = state.target == TunerTargetUi.Automatic,
            onClick = { eventSink(FoundationEvent.SelectTunerTarget(TunerTargetUi.Automatic)) },
            label = { Text(stringResource(R.string.tuner_auto)) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("tuner_auto"))
        GuitarStringUi.entries.forEach { target ->
            FilterChip(selected = state.target == TunerTargetUi.Manual(target),
                onClick = { eventSink(FoundationEvent.SelectTunerTarget(TunerTargetUi.Manual(target))) },
                label = { Text(stringResource(R.string.tuner_string_option, target.number, target.note + target.octave)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("tuner_string_" + target.name))
        }
    }
    SectionHeading(R.string.tuner_tolerance)
    Text(stringResource(R.string.tuner_tolerance_description), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToleranceUi.entries.forEach { tolerance ->
            FilterChip(selected = state.tolerance == tolerance,
                enabled = !state.savingTolerance && state.preferenceNotice != TunerNoticeUi.ReadFailed,
                onClick = { eventSink(FoundationEvent.SelectTunerTolerance(tolerance)) },
                label = { Text(pluralStringResource(R.plurals.tuner_tolerance_option, tolerance.cents, tolerance.cents)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("tuner_tolerance_" + tolerance.cents))
        }
    }
    if (state.savingTolerance) Text(stringResource(R.string.tuner_saving), Modifier.testTag("tuner_saving"))
    state.preferenceNotice?.let { notice ->
        Text(stringResource(notice.label), Modifier.testTag("tuner_preference_failure")
            .semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
        if (notice == TunerNoticeUi.ReadFailed) {
            TextButton(onClick = { eventSink(FoundationEvent.ReloadTunerTolerance) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.tuner_reload))
            }
        }
    }
    Text(stringResource(R.string.tuner_one_string_guidance), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TunerReading(state: TunerUiState) {
    val feedback = (state.listening as? TunerListeningUi.Listening)?.feedback
    val measured = feedback as? TunerFeedbackUi.Measured
    val selected = (state.target as? TunerTargetUi.Manual)?.string ?: measured?.string
    val colors = MaterialTheme.colorScheme
    val inTune = measured?.judgment == TunerJudgmentUi.InTune
    val needle = animateFloatAsState(targetValue = ((measured?.cents ?: 0.0) / 50).toFloat().coerceIn(-1f, 1f),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "pitchIndicator")
    val status = when (state.listening) {
        TunerListeningUi.Stopped -> R.string.tuner_stopped
        TunerListeningUi.Starting -> R.string.tuner_starting
        TunerListeningUi.Stopping -> R.string.tuner_stopping
        is TunerListeningUi.Failed -> R.string.tuner_unavailable
        is TunerListeningUi.Listening -> when (feedback) {
            TunerFeedbackUi.PluckOneString -> R.string.tuner_pluck
            TunerFeedbackUi.Uncertain -> R.string.tuner_uncertain
            TunerFeedbackUi.WrongOctave -> R.string.tuner_wrong_octave
            is TunerFeedbackUi.Measured -> when (feedback.judgment) {
                TunerJudgmentUi.Low -> R.string.tuner_low
                TunerJudgmentUi.High -> R.string.tuner_high
                TunerJudgmentUi.Settling -> R.string.tuner_settling
                TunerJudgmentUi.InTune -> R.string.tuner_in_tune
            }
            null -> R.string.tuner_pluck
        }
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (state.target == TunerTargetUi.Automatic) R.string.tuner_detected_string else R.string.tuner_selected_string),
                style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            val noteDescription = selected?.let { stringResource(R.string.tuner_string_option, it.number, it.note + it.octave) }
            Text(selected?.let { it.note + when (it.octave) { 2 -> "₂"; 3 -> "₃"; else -> "₄" } }
                ?: stringResource(R.string.note_placeholder),
                Modifier.fillMaxWidth().testTag("tuner_note").semantics { if (noteDescription != null) contentDescription = noteDescription },
                style = MaterialTheme.typography.displayLarge, color = colors.primary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.tuner_string_number,
                selected?.number?.toString() ?: stringResource(R.string.note_placeholder)),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.tuner_reference), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(64.dp).clearAndSetSemantics {}) {
                val left = 12.dp.toPx()
                val width = size.width - left * 2
                val centerY = size.height / 2
                drawLine(colors.outlineVariant, Offset(left, centerY), Offset(left + width, centerY), 2.dp.toPx())
                for (tick in 0..10) {
                    val x = left + width * tick / 10
                    val halfHeight = if (tick == 5) 12.dp.toPx() else 5.dp.toPx()
                    drawLine(colors.outline, Offset(x, centerY - halfHeight), Offset(x, centerY + halfHeight), 2.dp.toPx())
                }
                if (measured != null) {
                    val x = left + width * (needle.value + 1) / 2
                    drawLine(colors.primary, Offset(x, centerY - 24.dp.toPx()), Offset(x, centerY + 24.dp.toPx()), 4.dp.toPx())
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.pitch_low), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.pitch_center), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.pitch_high), style = MaterialTheme.typography.bodySmall)
            }
            TunerStatus(status, inTune)
            Text(measured?.let { stringResource(R.string.tuner_cents, it.cents.roundToInt().toDouble()) }
                ?: stringResource(R.string.tuner_cents_placeholder),
                Modifier.fillMaxWidth().testTag(if (measured != null) "tuner_cents" else "tuner_cents_placeholder"),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TunerStatus(status: Int, inTune: Boolean) {
    val labels = listOf(R.string.tuner_stopped, R.string.tuner_starting, R.string.tuner_stopping,
        R.string.tuner_unavailable, R.string.tuner_pluck, R.string.tuner_uncertain, R.string.tuner_wrong_octave,
        R.string.tuner_low, R.string.tuner_high, R.string.tuner_settling, R.string.tuner_in_tune).map { stringResource(it) }
    val style = MaterialTheme.typography.headlineSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = remember(labels, constraints.maxWidth, style, measurer) {
            labels.maxOf { measurer.measure(it, style, constraints = constraints.copy(minWidth = 0, minHeight = 0)).size.height }
        }
        Box(Modifier.fillMaxWidth().height(with(density) { height.toDp() }), contentAlignment = Alignment.Center) {
            Text(stringResource(status), Modifier.fillMaxWidth().testTag("tuner_status")
                .semantics { liveRegion = LiveRegionMode.Polite }, style = style, textAlign = TextAlign.Center,
                color = if (inTune) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

private val TunerNoticeUi.label: Int get() = when (this) {
    TunerNoticeUi.PermissionDenied -> R.string.tuner_permission_denied
    TunerNoticeUi.PermissionRevoked -> R.string.tuner_permission_revoked
    TunerNoticeUi.MicBlocked -> R.string.tuner_mic_blocked
    TunerNoticeUi.InputFailed -> R.string.tuner_input_failed
    TunerNoticeUi.NoInput -> R.string.tuner_no_input
    TunerNoticeUi.ShutdownFailed -> R.string.tuner_shutdown_failed
    TunerNoticeUi.MetronomeStopFailed -> R.string.tuner_metronome_stop_failed
    TunerNoticeUi.SettingsFailed -> R.string.tuner_settings_failed
    TunerNoticeUi.ReadFailed -> R.string.tuner_read_failed
    TunerNoticeUi.SaveFailed -> R.string.tuner_save_failed
}
