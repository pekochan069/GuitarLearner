package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.presentation.contract.*
import com.pekochan069.guitarlearner.ui.R
import kotlin.math.abs

@Composable
fun TrainingScreen(state: TrainingUiState, eventSink: (TrainingEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("training_screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.settingsSaving) Text(stringResource(R.string.training_saving), Modifier.testTag("training_saving"),
            style = MaterialTheme.typography.bodyMedium)
        state.settingsNotice?.let { notice ->
            TrainingNotice(notice, "training_settings_error")
            TextButton(onClick = { eventSink(TrainingEvent.RetrySettings) }, Modifier.testTag("training_settings_retry")) {
                Text(stringResource(R.string.training_retry))
            }
        }
        state.notice?.let { TrainingNotice(it, "training_notice") }
        when (val stage = state.stage) {
            TrainingStageUi.Setup -> TrainingSetup(state, eventSink)
            is TrainingStageUi.Question -> TrainingQuestion(stage, state.audio, eventSink)
            is TrainingStageUi.Results -> {
                Text(stringResource(R.string.training_results_count, stage.correctCount, stage.rows.size),
                    Modifier.testTag("training_results").semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                stage.rows.forEach { row ->
                    Surface(Modifier.fillMaxWidth().testTag("training_result_${row.number}"), shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.training_result_number, row.number,
                                stringResource(if (row.feedback.correct) R.string.training_correct else R.string.training_incorrect)),
                                style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.training_chosen_answer, row.feedback.chosen.label()), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.training_correct_answer, row.feedback.answer.label()), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Button(onClick = { eventSink(TrainingEvent.Exit) }, Modifier.testTag("training_setup_again")) {
                    Text(stringResource(R.string.training_setup_again))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingSetup(state: TrainingUiState, eventSink: (TrainingEvent) -> Unit) {
    val settings = state.settings
    val enabled = !state.settingsSaving && state.settingsNotice != TrainingNoticeUi.SettingsReadFailed
    Text(stringResource(R.string.training_subject), style = MaterialTheme.typography.titleMedium)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        TrainingSubjectUi.entries.forEach { subject ->
            SegmentedButton(selected = settings.subject == subject, onClick = { eventSink(TrainingEvent.SetSettings(settings.copy(subject = subject))) },
                enabled = enabled, shape = SegmentedButtonDefaults.itemShape(subject.ordinal, TrainingSubjectUi.entries.size),
                modifier = Modifier.testTag("training_subject_${subject.name}")) { Text(stringResource(subject.label)) }
        }
    }
    Text(stringResource(R.string.training_representation), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TrainingRepresentationUi.entries.forEach { representation ->
            FilterChip(selected = settings.representation == representation,
                onClick = { eventSink(TrainingEvent.SetSettings(settings.copy(representation = representation))) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("training_representation_${representation.name}"),
                label = { Text(stringResource(representation.label)) })
        }
    }
    if (settings.subject == TrainingSubjectUi.Interval) {
        Text(stringResource(R.string.training_interval_pool), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TrainingIntervalUi.entries.forEach { interval ->
                FilterChip(selected = interval in settings.intervals, enabled = enabled,
                    onClick = { eventSink(TrainingEvent.SetSettings(settings.copy(intervals = if (interval in settings.intervals)
                        settings.intervals - interval else settings.intervals + interval))) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("training_interval_${interval.name}"),
                    label = { Text(stringResource(interval.label)) })
            }
        }
        if (settings.intervals.isEmpty()) TrainingNotice(TrainingNoticeUi.EmptyIntervalPool, "training_empty_pool")
        if (settings.representation == TrainingRepresentationUi.Listening) {
            Text(stringResource(R.string.training_interval_presentation), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IntervalPresentationUi.entries.forEach { presentation ->
                    FilterChip(selected = settings.intervalPresentation == presentation, enabled = enabled,
                        onClick = { eventSink(TrainingEvent.SetSettings(settings.copy(intervalPresentation = presentation))) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("training_presentation_${presentation.name}"),
                        label = { Text(stringResource(presentation.label)) })
                }
            }
        }
    }
    Button(onClick = { eventSink(TrainingEvent.Start) }, enabled = !state.settingsSaving &&
        (settings.subject != TrainingSubjectUi.Interval || settings.intervals.isNotEmpty()),
        modifier = Modifier.fillMaxWidth().testTag("training_start")) { Text(stringResource(R.string.training_start)) }
}

@Composable
private fun TrainingQuestion(question: TrainingStageUi.Question, audio: TrainingAudioUi, eventSink: (TrainingEvent) -> Unit) {
    Text(stringResource(R.string.training_progress, question.number, 10), Modifier.testTag("training_progress"),
        style = MaterialTheme.typography.titleMedium)
    Text(stringResource(question.settings.subject.label) + " · " + stringResource(question.settings.representation.label),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(if (question.settings.subject == TrainingSubjectUi.Note) R.string.training_note_prompt else R.string.training_interval_prompt),
        Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
    when (question.settings.representation) {
        TrainingRepresentationUi.Listening -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { eventSink(TrainingEvent.Replay(question.key, TrainingSoundUi.Question)) },
                    modifier = Modifier.testTag("training_replay_question")) {
                    Icon(painterResource(R.drawable.ic_play), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.training_replay_question))
                }
                if (question.settings.subject == TrainingSubjectUi.Note) {
                    OutlinedButton(onClick = { eventSink(TrainingEvent.Replay(question.key, TrainingSoundUi.Comparison)) },
                        modifier = Modifier.testTag("training_replay_comparison")) { Text(stringResource(R.string.training_comparison_c4)) }
                }
            }
            when (audio) {
                TrainingAudioUi.Idle -> Unit
                is TrainingAudioUi.Preparing -> Text(stringResource(R.string.training_audio_preparing), Modifier.testTag("training_audio_status"),
                    style = MaterialTheme.typography.bodyMedium)
                is TrainingAudioUi.Playing -> Text(stringResource(if (audio.sound == TrainingSoundUi.Comparison)
                    R.string.training_audio_comparison else R.string.training_audio_question), Modifier.testTag("training_audio_status"),
                    style = MaterialTheme.typography.bodyMedium)
                is TrainingAudioUi.Failed -> TrainingNotice(audio.notice, "training_audio_error")
            }
        }
        TrainingRepresentationUi.Staff -> TrainingStaff(question.staff)
        TrainingRepresentationUi.Fretboard -> TrainingPositions(question.positions, tab = false)
        TrainingRepresentationUi.Tab -> TrainingPositions(question.positions, tab = true)
    }
    if (question.feedback == null) question.choices.chunked(2).forEach { choices ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                OutlinedButton(onClick = { eventSink(TrainingEvent.Answer(question.key, choice)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("training_answer_${choice.tag}")) { Text(choice.label()) }
            }
            if (choices.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    question.feedback?.let { feedback ->
        Surface(Modifier.fillMaxWidth().testTag("training_feedback").semantics { liveRegion = LiveRegionMode.Polite },
            shape = MaterialTheme.shapes.medium,
            color = if (feedback.correct) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (feedback.correct) R.string.training_correct else R.string.training_incorrect),
                    style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.training_chosen_answer, feedback.chosen.label()), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.training_correct_answer, feedback.answer.label()), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Button(onClick = { eventSink(TrainingEvent.Next(question.key)) }, modifier = Modifier.fillMaxWidth().testTag("training_next")) {
            Text(stringResource(if (question.number == 10) R.string.training_show_results else R.string.training_next))
        }
    }
}

@Composable
private fun TrainingNotice(notice: TrainingNoticeUi, tag: String) {
    Text(stringResource(notice.label), Modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun TrainingPositions(positions: List<TrainingPositionUi>, tab: Boolean) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelLarge
    val cell = with(LocalDensity.current) { style.lineHeight.toDp() + 20.dp }.coerceAtLeast(44.dp)
    val measurer = rememberTextMeasurer()
    val initialScroll = with(LocalDensity.current) {
        (cell * ((positions.minOfOrNull { it.fret } ?: 0) - 1).coerceAtLeast(0)).roundToPx()
    }
    val description = positions.mapIndexed { index, position -> stringResource(R.string.training_position_description,
        index + 1, position.stringNumber, position.fret) }.joinToString(". ")
    Text(stringResource(R.string.training_standard_positions), style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant)
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(cell)) {
                Spacer(Modifier.height(cell))
                for (string in 1..6) Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                    Text(string.toString(), Modifier.testTag("training_string_$string"), style = style, color = colors.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).horizontalScroll(rememberScrollState(initial = if (tab) 0 else initialScroll))
                .testTag("training_positions_scroll")) {
                Canvas(Modifier.width(cell * if (tab) 6 else 14).height(cell * 8).testTag(if (tab) "training_tab" else "training_fretboard")
                    .clearAndSetSemantics { contentDescription = description }) {
                    val step = cell.toPx()
                    for (string in 1..6) {
                        val y = step * (string + 0.5f)
                        drawLine(colors.outline, Offset(0f, y), Offset(size.width - step / 2, y), 1.dp.toPx())
                    }
                    if (!tab) {
                        for (fret in 0..12) {
                            val x = step * (fret + 0.5f)
                            drawCentered(measurer, fret.toString(), style.copy(color = colors.onSurfaceVariant), Offset(x, step / 2))
                            if (fret > 0) drawLine(colors.outlineVariant, Offset(x - step / 2, step * 1.5f),
                                Offset(x - step / 2, step * 6.5f), if (fret == 1) 3.dp.toPx() else 1.dp.toPx())
                        }
                    }
                    positions.forEachIndexed { index, position ->
                        val duplicate = positions.size == 2 && positions[0] == positions[1]
                        val x = if (tab) step * (1.5f + index * 2) else step * (position.fret + 0.5f) +
                            if (duplicate) (index * 2 - 1) * step * 0.24f else 0f
                        val y = step * (position.stringNumber + 0.5f)
                        if (tab) {
                            drawRect(colors.surfaceContainer, Offset(x - step / 2, y - step / 2), Size(step, step))
                            drawCentered(measurer, position.fret.toString(), style.copy(color = colors.onSurface), Offset(x, y))
                            drawCentered(measurer, (index + 1).toString(), style.copy(color = colors.primary), Offset(x, step * 7.5f))
                        } else {
                            drawCircle(if (index == 0) colors.primary else colors.tertiary, step * if (duplicate) 0.23f else 0.34f, Offset(x, y))
                            drawCentered(measurer, (index + 1).toString(), style.copy(color = if (index == 0) colors.onPrimary else colors.onTertiary), Offset(x, y))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingStaff(notes: List<TrainingStaffNoteUi>) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelLarge
    val gap = with(LocalDensity.current) { style.fontSize.toDp() }.coerceAtLeast(14.dp)
    val clefStyle = style.copy(fontFamily = FontFamily.Serif, fontSize = with(LocalDensity.current) { (gap * 4).toSp() })
    val measurer = rememberTextMeasurer()
    val description = notes.mapIndexed { index, note -> pluralStringResource(if (note.step < 0) R.plurals.training_staff_description_below
        else R.plurals.training_staff_description, abs(note.step), index + 1, abs(note.step),
        stringResource(when (note.accidental) {
            TrainingAccidentalUi.Natural -> R.string.training_natural
            TrainingAccidentalUi.Sharp -> R.string.training_sharp
            TrainingAccidentalUi.Flat -> R.string.training_flat
        })) }.joinToString(". ")
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Canvas(Modifier.fillMaxWidth().height(gap * 18).testTag("training_staff")
            .clearAndSetSemantics { contentDescription = description }) {
            val spacing = gap.toPx()
            fun y(step: Int): Float = spacing * 12 - step * spacing / 2
            for (line in 0..8 step 2) drawLine(colors.outline, Offset(spacing, y(line)), Offset(size.width - spacing, y(line)), 1.dp.toPx())
            drawCentered(measurer, "𝄞", clefStyle.copy(color = colors.onSurface), Offset(spacing * 2, y(4)))
            drawCentered(measurer, "8", style.copy(color = colors.onSurface), Offset(spacing * 2, y(-3)))
            notes.forEachIndexed { index, note ->
                val x = spacing * 5 + (size.width - spacing * 7) * (index + 1) / (notes.size + 1)
                val noteY = y(note.step)
                val ledger = when {
                    note.step < 0 -> (-2 downTo note.step step 2).toList()
                    note.step > 8 -> (10..note.step step 2).toList()
                    else -> emptyList()
                }
                ledger.forEach { drawLine(colors.onSurface, Offset(x - spacing, y(it)), Offset(x + spacing, y(it)), 1.dp.toPx()) }
                drawOval(colors.onSurface, Offset(x - spacing * 0.48f, noteY - spacing * 0.3f), Size(spacing * 0.96f, spacing * 0.6f))
                val up = note.step < 4
                val stemX = x + if (up) spacing * 0.45f else -spacing * 0.45f
                drawLine(colors.onSurface, Offset(stemX, noteY), Offset(stemX, noteY + if (up) -spacing * 3 else spacing * 3), 1.5.dp.toPx())
                drawCentered(measurer, when (note.accidental) {
                    TrainingAccidentalUi.Natural -> "♮"
                    TrainingAccidentalUi.Sharp -> "♯"
                    TrainingAccidentalUi.Flat -> "♭"
                }, style.copy(color = colors.onSurface), Offset(x - spacing * 1.6f, noteY))
                drawCentered(measurer, (index + 1).toString(), style.copy(color = colors.primary), Offset(x, spacing * 17))
            }
        }
    }
    Text(stringResource(R.string.training_guitar_staff), Modifier.testTag("training_staff_octave"),
        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
}

private fun DrawScope.drawCentered(measurer: TextMeasurer, text: String, style: TextStyle, center: Offset) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2, center.y - layout.size.height / 2))
}

@Composable
private fun TrainingAnswerUi.label(): String = when (this) {
    is TrainingAnswerUi.Note -> when (note) {
        TrainingNoteUi.C -> "C"
        TrainingNoteUi.CSharp -> "C♯ / D♭"
        TrainingNoteUi.D -> "D"
        TrainingNoteUi.EFlat -> "D♯ / E♭"
        TrainingNoteUi.E -> "E"
        TrainingNoteUi.F -> "F"
        TrainingNoteUi.FSharp -> "F♯ / G♭"
        TrainingNoteUi.G -> "G"
        TrainingNoteUi.AFlat -> "G♯ / A♭"
        TrainingNoteUi.A -> "A"
        TrainingNoteUi.BFlat -> "A♯ / B♭"
        TrainingNoteUi.B -> "B"
    }
    is TrainingAnswerUi.Interval -> stringResource(interval.label)
}
private val TrainingAnswerUi.tag: String get() = when (this) {
    is TrainingAnswerUi.Note -> note.name
    is TrainingAnswerUi.Interval -> interval.name
}
private val TrainingSubjectUi.label: Int get() = when (this) {
    TrainingSubjectUi.Note -> R.string.training_notes
    TrainingSubjectUi.Interval -> R.string.training_intervals
}
private val TrainingRepresentationUi.label: Int get() = when (this) {
    TrainingRepresentationUi.Listening -> R.string.training_listening
    TrainingRepresentationUi.Staff -> R.string.training_staff
    TrainingRepresentationUi.Fretboard -> R.string.training_fretboard
    TrainingRepresentationUi.Tab -> R.string.training_tab
}
private val IntervalPresentationUi.label: Int get() = when (this) {
    IntervalPresentationUi.Ascending -> R.string.training_ascending
    IntervalPresentationUi.Descending -> R.string.training_descending
    IntervalPresentationUi.Harmonic -> R.string.training_harmonic
}
private val TrainingIntervalUi.label: Int get() = when (this) {
    TrainingIntervalUi.Unison -> R.string.training_unison
    TrainingIntervalUi.MinorSecond -> R.string.training_minor_second
    TrainingIntervalUi.MajorSecond -> R.string.training_major_second
    TrainingIntervalUi.MinorThird -> R.string.training_minor_third
    TrainingIntervalUi.MajorThird -> R.string.training_major_third
    TrainingIntervalUi.PerfectFourth -> R.string.training_perfect_fourth
    TrainingIntervalUi.Tritone -> R.string.training_tritone
    TrainingIntervalUi.PerfectFifth -> R.string.training_perfect_fifth
    TrainingIntervalUi.MinorSixth -> R.string.training_minor_sixth
    TrainingIntervalUi.MajorSixth -> R.string.training_major_sixth
    TrainingIntervalUi.MinorSeventh -> R.string.training_minor_seventh
    TrainingIntervalUi.MajorSeventh -> R.string.training_major_seventh
    TrainingIntervalUi.Octave -> R.string.training_octave
}
private val TrainingNoticeUi.label: Int get() = when (this) {
    TrainingNoticeUi.EmptyIntervalPool -> R.string.training_empty_pool
    TrainingNoticeUi.SettingsReadFailed -> R.string.training_settings_read_failed
    TrainingNoticeUi.SettingsWriteFailed -> R.string.training_settings_write_failed
    TrainingNoticeUi.MetronomeStopFailed -> R.string.training_metronome_stop_failed
    TrainingNoticeUi.PlaybackFailed -> R.string.training_playback_failed
    TrainingNoticeUi.ShutdownFailed -> R.string.training_shutdown_failed
    TrainingNoticeUi.FocusDenied -> R.string.training_focus_denied
    TrainingNoticeUi.OutputInterrupted -> R.string.training_output_interrupted
}
