package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.presentation.contract.*
import com.pekochan069.guitarlearner.ui.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LearningScreen(state: LearningUiState, eventSink: (LearningEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("learning_screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.saving) LearningStatus(stringResource(R.string.learning_saving), "learning_save_status")
        state.saveNotice?.let { notice ->
            LearningStatus(stringResource(notice.label), "learning_save_error", error = true)
            OutlinedButton(onClick = { eventSink(LearningEvent.RetrySave) }, modifier = Modifier.testTag("learning_retry_save")) {
                Text(stringResource(R.string.learning_retry_save))
            }
        }
        when (val page = state.page) {
            is LearningUiPage.Topics -> LearningTopics(page, eventSink)
            is LearningUiPage.Courses -> page.courses.forEach { course ->
                Card(onClick = { eventSink(LearningEvent.OpenCourse(course.id)) },
                    modifier = Modifier.fillMaxWidth().testTag("learning_course_${course.id.name}"), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(course.id.title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(course.id.goal), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.learning_course_progress, course.completed, course.total), style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onClick = { eventSink(LearningEvent.ContinueCourse(course.id)) },
                            modifier = Modifier.fillMaxWidth().testTag("learning_course_continue_${course.id.name}")) {
                            Text(stringResource(course.action, stringResource(course.entryLesson.title)))
                        }
                        OutlinedButton(onClick = { eventSink(LearningEvent.OpenCourse(course.id)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.learning_course_view))
                        }
                    }
                }
            }
            is LearningUiPage.CourseOverview -> LearningCourseOverview(page.course, eventSink)
            is LearningUiPage.Lesson -> LearningLesson(state, page, eventSink)
        }
    }
}

@Composable
private fun LearningTopics(page: LearningUiPage.Topics, eventSink: (LearningEvent) -> Unit) {
    page.lastViewed?.let { lesson ->
        FilledTonalButton(onClick = { eventSink(LearningEvent.Resume) }, modifier = Modifier.fillMaxWidth().testTag("learning_resume")) {
            Text(stringResource(R.string.learning_resume, stringResource(lesson.title)))
        }
    }
    listOf(true, false).forEach { theory ->
        Text(stringResource(if (theory) R.string.learning_theory_course else R.string.learning_technique_course),
            Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        page.lessons.filter { it.theory == theory }.forEach { row ->
            LearningRow(stringResource(row.id.title), "learning_lesson_${row.id.name}", row.completed,
                goal = stringResource(row.id.goal)) { eventSink(LearningEvent.OpenLesson(row.id)) }
        }
    }
}

@Composable
private fun LearningRow(title: String, tag: String, completed: Boolean, goal: String, onClick: () -> Unit) {
    val completion = stringResource(if (completed) R.string.learning_completed else R.string.learning_not_completed)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag).semantics {
        stateDescription = completion
    }, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(goal, style = MaterialTheme.typography.bodyMedium)
            if (completed) Text(stringResource(R.string.learning_completed), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LearningCourseOverview(course: LearningCourseUi, eventSink: (LearningEvent) -> Unit) {
    Text(stringResource(course.id.title), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(course.id.goal), Modifier.testTag("learning_course_goal"), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.learning_course_progress, course.completed, course.total),
        Modifier.testTag("learning_course_progress"), style = MaterialTheme.typography.titleMedium)
    LinearProgressIndicator(progress = { if (course.total == 0) 0f else course.completed.toFloat() / course.total }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { eventSink(LearningEvent.ContinueCourse(course.id)) },
        modifier = Modifier.fillMaxWidth().testTag("learning_course_continue")) {
        Text(stringResource(course.action, stringResource(course.entryLesson.title)))
    }
    Text(stringResource(R.string.learning_course_unlocked), style = MaterialTheme.typography.bodyMedium)
    course.lessons.forEachIndexed { index, row ->
        LearningRow(stringResource(R.string.learning_course_lesson, index + 1, stringResource(row.id.title)),
            "learning_lesson_${row.id.name}", row.completed, stringResource(row.id.goal)) { eventSink(LearningEvent.OpenLesson(row.id)) }
    }
}

@Composable
private fun LearningLesson(state: LearningUiState, page: LearningUiPage.Lesson, eventSink: (LearningEvent) -> Unit) {
    val lesson = page.id
    val course = page.context as? LearningLessonContextUi.Course
    course?.let {
        Text(stringResource(R.string.learning_course_step, stringResource(it.id.title), it.step, it.total),
            Modifier.testTag("learning_course_step"), style = MaterialTheme.typography.titleMedium)
    }
    Text(stringResource(lesson.title), Modifier.testTag("learning_lesson_title").semantics { heading() },
        style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(lesson.goal), Modifier.testTag("learning_lesson_goal"), style = MaterialTheme.typography.titleMedium)
    if (lesson == LessonUi.CircleOfFifths) LearningTheory(state, LearningConceptUi.CircleOfFifths, eventSink)
    Text(stringResource(lesson.explanation), Modifier.testTag("learning_explanation"), style = MaterialTheme.typography.bodyLarge)
    lesson.tab?.let { tab ->
        Text(stringResource(R.string.learning_tab_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(tab), Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("learning_tab").padding(vertical = 8.dp),
            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(lesson.practice!!), Modifier.testTag("learning_practice"), style = MaterialTheme.typography.bodyLarge)
    }
    if (lesson != LessonUi.CircleOfFifths) lesson.concept?.let { LearningTheory(state, it, eventSink) }
    if (page.completed) Text(stringResource(R.string.learning_completed), Modifier.testTag("learning_completed"),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    else Button(onClick = { eventSink(LearningEvent.Complete) }, modifier = Modifier.fillMaxWidth().testTag("learning_complete")) {
        Text(stringResource(R.string.learning_mark_complete))
    }
    Text(stringResource(R.string.learning_completion_meaning), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (course != null) {
        course.next?.let { next ->
            val label = stringResource(R.string.learning_next_lesson, stringResource(next.title))
            if (page.completed) Button(onClick = { eventSink(LearningEvent.OpenLesson(next)) },
                modifier = Modifier.fillMaxWidth().testTag("learning_next")) { Text(label) }
            else OutlinedButton(onClick = { eventSink(LearningEvent.OpenLesson(next)) },
                modifier = Modifier.fillMaxWidth().testTag("learning_next")) { Text(label) }
        }
        if (course.next == null) {
            if (page.completed) Button(onClick = { eventSink(LearningEvent.OpenCourse(course.id)) },
                modifier = Modifier.fillMaxWidth().testTag("learning_course_overview")) { Text(stringResource(R.string.learning_course_overview)) }
            else OutlinedButton(onClick = { eventSink(LearningEvent.OpenCourse(course.id)) },
                modifier = Modifier.fillMaxWidth().testTag("learning_course_overview")) { Text(stringResource(R.string.learning_course_overview)) }
        }
        course.previous?.let { previous ->
            OutlinedButton(onClick = { eventSink(LearningEvent.OpenLesson(previous)) }, modifier = Modifier.fillMaxWidth().testTag("learning_previous")) {
                Text(stringResource(R.string.learning_previous_lesson, stringResource(previous.title)))
            }
        }
    }
    LearningLinks(state, eventSink)
}

@Composable
private fun LearningTheory(state: LearningUiState, concept: LearningConceptUi, eventSink: (LearningEvent) -> Unit) {
    if (concept == LearningConceptUi.CircleOfFifths) LearningCircle(state, eventSink)
    if (concept != LearningConceptUi.CircleOfFifths) {
        LearningChoice(stringResource(R.string.learning_root), state.root, state.roots, "learning_root",
            optionTags = state.roots.map { "learning_key_$it" }) { eventSink(LearningEvent.SetRoot(it)) }
    }
    when (concept) {
        LearningConceptUi.NotesIntervals -> LearningChoice(stringResource(R.string.learning_interval),
            stringResource(state.interval.learningLabel), TrainingIntervalUi.entries.map { stringResource(it.learningLabel) }, "learning_interval") {
            eventSink(LearningEvent.SetInterval(TrainingIntervalUi.entries[it]))
        }
        LearningConceptUi.Scales -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LearningScaleUi.entries.forEach { scale ->
                FilterChip(selected = state.scale == scale, onClick = { eventSink(LearningEvent.SetScale(scale)) },
                    modifier = Modifier.testTag("learning_scale_${scale.name}"), label = { Text(stringResource(scale.label)) })
            }
        }
        LearningConceptUi.Chords -> LearningChoice(stringResource(R.string.learning_chord_quality), state.chord.learningLabel(),
            state.chordChoices.map { it.learningLabel() }, "learning_chord") { eventSink(LearningEvent.SetChord(state.chordChoices[it])) }
        LearningConceptUi.Progressions -> LearningChoice(stringResource(R.string.learning_progression), state.progression.symbol,
            LearningProgressionUi.entries.map { it.symbol }, "learning_progression") { eventSink(LearningEvent.SetProgression(LearningProgressionUi.entries[it])) }
        LearningConceptUi.DiatonicFunctions, LearningConceptUi.CircleOfFifths -> Unit
    }
    if (state.chords.isNotEmpty()) {
        state.chords.forEachIndexed { index, chord ->
            FilterChip(selected = state.chordIndex == index, onClick = { eventSink(LearningEvent.SelectChord(index)) },
                modifier = Modifier.testTag("learning_chord_step_$index"), label = {
                    Text("${chord.roman} · ${chord.symbol} · ${stringResource(chord.function.label)}")
                })
        }
    }
    Text(state.notes.joinToString(" · ") { "${it.name} (${it.degree})" }, Modifier.testTag("learning_notes"),
        style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.learning_degrees), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    LearningFretboard(state.frets)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrainingInstrumentUi.entries.forEach { instrument ->
            FilterChip(selected = state.instrument == instrument, onClick = { eventSink(LearningEvent.SetInstrument(instrument)) },
                modifier = Modifier.testTag("learning_instrument_${instrument.name}"), label = {
                    Text(stringResource(if (instrument == TrainingInstrumentUi.Piano) R.string.training_piano else R.string.training_guitar))
                })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { eventSink(LearningEvent.Listen) }, modifier = Modifier.testTag("learning_listen")) {
            Text(stringResource(R.string.learning_listen))
        }
        if (state.audio == LearningAudioUi.Preparing || state.audio == LearningAudioUi.Playing) {
            OutlinedButton(onClick = { eventSink(LearningEvent.Stop) }, modifier = Modifier.testTag("learning_stop")) {
                Text(stringResource(R.string.learning_stop))
            }
        }
    }
    when (state.audio) {
        LearningAudioUi.Idle -> Unit
        LearningAudioUi.Preparing -> LearningStatus(stringResource(R.string.training_audio_preparing), "learning_audio_status")
        LearningAudioUi.Playing -> LearningStatus(stringResource(R.string.learning_playing), "learning_audio_status")
        LearningAudioUi.Failed -> {
            LearningStatus(stringResource(state.audioNotice?.label ?: R.string.learning_playback_failed), "learning_audio_error", error = true)
            OutlinedButton(onClick = { eventSink(LearningEvent.Listen) }, modifier = Modifier.testTag("learning_retry_audio")) {
                Text(stringResource(R.string.learning_retry_audio))
            }
        }
    }
}

@Composable
private fun LearningFretboard(frets: List<LearningFretUi>) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelLarge
    val cell = with(LocalDensity.current) { (style.fontSize.toDp() * 4).coerceAtLeast(60.dp) }
    Text(stringResource(R.string.learning_standard_tuning), style = MaterialTheme.typography.bodySmall)
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Column(Modifier.horizontalScroll(rememberScrollState()).testTag("learning_fretboard").padding(8.dp)) {
            Row {
                Text("", Modifier.size(cell))
                for (fret in 0..12) Text(fret.toString(), Modifier.width(cell).heightIn(min = 24.dp), textAlign = TextAlign.Center, style = style)
            }
            Column(Modifier.drawBehind {
                for (row in 0..5) drawLine(colors.outline, Offset(cell.toPx(), (row + 0.5f) * cell.toPx()),
                    Offset(size.width, (row + 0.5f) * cell.toPx()), 1.dp.toPx())
                for (fret in 1..13) drawLine(colors.outlineVariant, Offset((fret + 1) * cell.toPx(), 0f),
                    Offset((fret + 1) * cell.toPx(), size.height), 1.dp.toPx())
            }) {
                for (string in 1..6) Row {
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) { Text(string.toString(), style = style) }
                    for (fret in 0..12) {
                        val note = frets.firstOrNull { it.stringNumber == string && it.fret == fret }
                        val description = note?.let { stringResource(R.string.learning_fret_description, string, fret, it.name, it.degree) }
                        Box(Modifier.size(cell).then(if (description != null) Modifier.clearAndSetSemantics { contentDescription = description }
                            else Modifier.clearAndSetSemantics {}), contentAlignment = Alignment.Center) {
                            if (note != null) Box(Modifier.size(cell - 4.dp).background(
                                if (note.degree == "1") colors.primary else colors.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${note.name}\n${note.degree}", style = style, textAlign = TextAlign.Center,
                                    color = if (note.degree == "1") colors.onPrimary else colors.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningCircle(state: LearningUiState, eventSink: (LearningEvent) -> Unit) {
    val selectedIndex = state.circle.indexOfFirst { it.tonic == state.root }
    val selected = state.circle.getOrNull(selectedIndex)
    val colors = MaterialTheme.colorScheme
    val nodeSize = with(LocalDensity.current) { MaterialTheme.typography.titleMedium.fontSize.toDp() * 3 }.coerceAtLeast(48.dp)
    Text(stringResource(R.string.learning_circle_description), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.learning_circle_rings), style = MaterialTheme.typography.labelLarge,
        color = colors.onSurfaceVariant)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val diameter = maxWidth.coerceAtMost(420.dp).coerceAtLeast(nodeSize * 6.5f)
        val scrollable = diameter > maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (scrollable) Text(stringResource(R.string.learning_circle_scroll), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("learning_circle"), contentAlignment = Alignment.Center) {
                Box(Modifier.size(diameter).drawBehind {
                    val hole = size.minDimension * 0.18f
                    val step = 360f / state.circle.size.coerceAtLeast(1)
                    state.circle.indices.forEach { index ->
                        drawArc(if (index == selectedIndex) colors.primaryContainer else colors.surfaceContainerLow,
                            -90f - step / 2 + index * step, step, useCenter = true)
                    }
                    drawCircle(colors.outlineVariant, style = Stroke(1.dp.toPx()))
                    drawCircle(colors.outlineVariant, size.minDimension * 0.35f, style = Stroke(1.dp.toPx()))
                    state.circle.indices.forEach { index ->
                        val angle = Math.toRadians((-90f - step / 2 + index * step).toDouble())
                        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                        drawLine(colors.outlineVariant, center + direction * hole,
                            center + direction * (size.minDimension / 2), 1.dp.toPx())
                    }
                    drawCircle(colors.surface, hole)
                    drawCircle(colors.outlineVariant, hole, style = Stroke(1.dp.toPx()))
                }) {
                    Column(Modifier.align(Alignment.Center).width(diameter * 0.30f).clearAndSetSemantics {},
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.root, style = MaterialTheme.typography.headlineMedium, color = colors.primary)
                        Text(stringResource(R.string.learning_circle_major), style = MaterialTheme.typography.labelMedium)
                    }
                    state.circle.forEachIndexed { index, key ->
                        val angle = Math.PI * 2 * index / state.circle.size - Math.PI / 2
                        val center = diameter / 2
                        val radius = center - nodeSize / 2
                        val active = index == selectedIndex
                        val rootIndex = state.roots.indexOf(key.tonic)
                        val label = stringResource(R.string.learning_circle_key, key.tonic, key.relativeMinor) + ", " +
                            stringResource(R.string.learning_circle_signature, key.signature)
                        Surface(selected = active, onClick = { eventSink(LearningEvent.SetRoot(rootIndex)) }, enabled = rootIndex >= 0,
                            modifier = Modifier.absoluteOffset(center + radius * cos(angle).toFloat() - nodeSize / 2,
                                center + radius * sin(angle).toFloat() - nodeSize / 2).size(nodeSize)
                                .testTag("learning_circle_${key.tonic}").semantics { contentDescription = label },
                            shape = CircleShape, color = if (active) colors.primary else colors.surfaceContainerLow,
                            contentColor = if (active) colors.onPrimary else colors.onSurface) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(key.tonic, style = MaterialTheme.typography.titleMedium)
                                Text(key.signature, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Box(Modifier.absoluteOffset(center + diameter * 0.27f * cos(angle).toFloat() - nodeSize / 2,
                            center + diameter * 0.27f * sin(angle).toFloat() - nodeSize / 4)
                            .width(nodeSize).height(nodeSize / 2).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                            Text("${key.relativeMinor}m", style = MaterialTheme.typography.labelMedium,
                                color = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    selected?.let { key ->
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.learning_circle_pair, key.tonic, key.relativeMinor),
                    Modifier.testTag("learning_circle_pair"), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.learning_circle_signature, key.signature),
                    Modifier.testTag("learning_circle_signature"), style = MaterialTheme.typography.bodyMedium)
                Text(if (key.alteredNotes.isEmpty()) stringResource(R.string.learning_circle_naturals) else key.alteredNotes.joinToString(" · "),
                    Modifier.testTag("learning_circle_altered_notes"), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.learning_circle_neighbors), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val previous = state.circle[(selectedIndex + state.circle.size - 1) % state.circle.size]
            val next = state.circle[(selectedIndex + 1) % state.circle.size]
            OutlinedButton(onClick = { eventSink(LearningEvent.SetRoot(state.roots.indexOf(previous.tonic))) },
                modifier = Modifier.weight(1f).testTag("learning_circle_previous")) {
                Text(stringResource(R.string.learning_circle_counterclockwise, previous.tonic), textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = { eventSink(LearningEvent.SetRoot(state.roots.indexOf(next.tonic))) },
                modifier = Modifier.weight(1f).testTag("learning_circle_next")) {
                Text(stringResource(R.string.learning_circle_clockwise, next.tonic), textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LearningChoice(label: String, value: String, options: List<String>, tag: String,
    optionTags: List<String> = options.indices.map { "${tag}_option_$it" }, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag(tag), singleLine = true)
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(index) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(optionTags[index]))
            }
        }
    }
}

@Composable
private fun LearningLinks(state: LearningUiState, eventSink: (LearningEvent) -> Unit) {
    if (state.trainingLinks.isEmpty() && state.toolLinks.isEmpty()) return
    Text(stringResource(R.string.learning_practice_links), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.trainingLinks.forEach { exercise ->
            OutlinedButton(onClick = { eventSink(LearningEvent.OpenTraining(exercise)) }, modifier = Modifier.testTag("learning_link_${exercise.name}")) {
                Text(stringResource(exercise.learningLabel))
            }
        }
        state.toolLinks.forEach { tool ->
            OutlinedButton(onClick = { eventSink(LearningEvent.OpenTool(tool)) }, modifier = Modifier.testTag("learning_link_${tool.name}")) {
                Text(stringResource(when (tool) {
                    FeatureId.Metronome -> R.string.metronome_title
                    FeatureId.Tuner -> R.string.tuner_title
                    FeatureId.Chords -> R.string.chord_title
                    FeatureId.Progressions -> R.string.progression_title
                    FeatureId.Training -> R.string.training_title
                    FeatureId.Learning -> R.string.learning_topics
                    FeatureId.LearningCourses -> R.string.learning_courses
                }))
            }
        }
    }
}

@Composable
private fun LearningStatus(message: String, tag: String, error: Boolean = false) {
    Text(message, Modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

private val CourseUi.title: Int get() = when (this) { CourseUi.Theory -> R.string.learning_theory_course; CourseUi.Technique -> R.string.learning_technique_course }
private val CourseUi.goal: Int get() = when (this) { CourseUi.Theory -> R.string.learning_theory_course_goal; CourseUi.Technique -> R.string.learning_technique_course_goal }
private val LearningCourseUi.action: Int get() = when {
    reviewing -> R.string.learning_course_review
    completed == 0 -> R.string.learning_course_start
    else -> R.string.learning_course_continue
}
private val LearningScaleUi.label: Int get() = when (this) {
    LearningScaleUi.Major -> R.string.learning_major_scale; LearningScaleUi.NaturalMinor -> R.string.learning_natural_minor
}
private val LearningProgressionUi.symbol: String get() = when (this) {
    LearningProgressionUi.OneFourFiveOne -> "I–IV–V–I"; LearningProgressionUi.OneFiveSixFour -> "I–V–vi–IV"; LearningProgressionUi.TwoFiveOne -> "ii–V–I"
}
private val LearningFunctionUi.label: Int get() = when (this) {
    LearningFunctionUi.Tonic -> R.string.learning_tonic; LearningFunctionUi.Predominant -> R.string.learning_predominant; LearningFunctionUi.Dominant -> R.string.learning_dominant
}
private val LearningNoticeUi.label: Int get() = when (this) {
    LearningNoticeUi.ReadFailed -> R.string.learning_read_failed; LearningNoticeUi.WriteFailed -> R.string.learning_write_failed
    LearningNoticeUi.PlaybackFailed -> R.string.learning_playback_failed; LearningNoticeUi.ShutdownFailed -> R.string.training_shutdown_failed
    LearningNoticeUi.FocusDenied -> R.string.training_focus_denied; LearningNoticeUi.OutputInterrupted -> R.string.training_output_interrupted
    LearningNoticeUi.MetronomeStopFailed -> R.string.training_metronome_stop_failed
}
private val LessonUi.concept: LearningConceptUi? get() = when (this) {
    LessonUi.NotesIntervals -> LearningConceptUi.NotesIntervals; LessonUi.Scales -> LearningConceptUi.Scales
    LessonUi.ChordConstruction -> LearningConceptUi.Chords; LessonUi.DiatonicFunctions -> LearningConceptUi.DiatonicFunctions
    LessonUi.BasicProgressions -> LearningConceptUi.Progressions; LessonUi.CircleOfFifths -> LearningConceptUi.CircleOfFifths
    else -> null
}
private val LessonUi.title: Int get() = when (this) {
    LessonUi.NotesIntervals -> R.string.learning_notes_title; LessonUi.Scales -> R.string.learning_scales_title
    LessonUi.ChordConstruction -> R.string.learning_chords_title; LessonUi.DiatonicFunctions -> R.string.learning_diatonic_title
    LessonUi.BasicProgressions -> R.string.learning_progressions_title; LessonUi.CircleOfFifths -> R.string.learning_circle_title
    LessonUi.Strumming -> R.string.learning_strumming_title; LessonUi.AlternatePicking -> R.string.learning_picking_title
    LessonUi.HammerOnPullOff -> R.string.learning_hammer_title; LessonUi.Slide -> R.string.learning_slide_title
    LessonUi.Bending -> R.string.learning_bending_title; LessonUi.Vibrato -> R.string.learning_vibrato_title; LessonUi.PalmMute -> R.string.learning_palm_title
}
private val LessonUi.explanation: Int get() = when (this) {
    LessonUi.NotesIntervals -> R.string.learning_notes_text; LessonUi.Scales -> R.string.learning_scales_text
    LessonUi.ChordConstruction -> R.string.learning_chords_text; LessonUi.DiatonicFunctions -> R.string.learning_diatonic_text
    LessonUi.BasicProgressions -> R.string.learning_progressions_text; LessonUi.CircleOfFifths -> R.string.learning_circle_text
    LessonUi.Strumming -> R.string.learning_strumming_text; LessonUi.AlternatePicking -> R.string.learning_picking_text
    LessonUi.HammerOnPullOff -> R.string.learning_hammer_text; LessonUi.Slide -> R.string.learning_slide_text
    LessonUi.Bending -> R.string.learning_bending_text; LessonUi.Vibrato -> R.string.learning_vibrato_text; LessonUi.PalmMute -> R.string.learning_palm_text
}
private val LessonUi.goal: Int get() = when (this) {
    LessonUi.NotesIntervals -> R.string.learning_notes_goal; LessonUi.Scales -> R.string.learning_scales_goal
    LessonUi.ChordConstruction -> R.string.learning_chords_goal; LessonUi.DiatonicFunctions -> R.string.learning_diatonic_goal
    LessonUi.BasicProgressions -> R.string.learning_progressions_goal; LessonUi.CircleOfFifths -> R.string.learning_circle_goal
    LessonUi.Strumming -> R.string.learning_strumming_goal; LessonUi.AlternatePicking -> R.string.learning_picking_goal
    LessonUi.HammerOnPullOff -> R.string.learning_hammer_goal; LessonUi.Slide -> R.string.learning_slide_goal
    LessonUi.Bending -> R.string.learning_bending_goal; LessonUi.Vibrato -> R.string.learning_vibrato_goal; LessonUi.PalmMute -> R.string.learning_palm_goal
}
private val LessonUi.tab: Int? get() = when (this) {
    LessonUi.Strumming -> R.string.learning_strumming_tab; LessonUi.AlternatePicking -> R.string.learning_picking_tab
    LessonUi.HammerOnPullOff -> R.string.learning_hammer_tab; LessonUi.Slide -> R.string.learning_slide_tab
    LessonUi.Bending -> R.string.learning_bending_tab; LessonUi.Vibrato -> R.string.learning_vibrato_tab; LessonUi.PalmMute -> R.string.learning_palm_tab
    else -> null
}
private val LessonUi.practice: Int? get() = when (this) {
    LessonUi.Strumming -> R.string.learning_strumming_practice; LessonUi.AlternatePicking -> R.string.learning_picking_practice
    LessonUi.HammerOnPullOff -> R.string.learning_hammer_practice; LessonUi.Slide -> R.string.learning_slide_practice
    LessonUi.Bending -> R.string.learning_bending_practice; LessonUi.Vibrato -> R.string.learning_vibrato_practice; LessonUi.PalmMute -> R.string.learning_palm_practice
    else -> null
}
@Composable private fun ChordQualityUi.learningLabel(): String = if (this == ChordQualityUi.Major) stringResource(R.string.chord_major) else symbol
private val TrainingExerciseUi.learningLabel: Int get() = when (this) {
    TrainingExerciseUi.NoteListening -> R.string.training_note_listening; TrainingExerciseUi.IntervalListening -> R.string.training_interval_listening
    TrainingExerciseUi.StaffNote -> R.string.training_staff_reading; TrainingExerciseUi.FretboardNote -> R.string.training_fretboard_notes; TrainingExerciseUi.TabNote -> R.string.training_tab_reading
}
private val TrainingIntervalUi.learningLabel: Int get() = when (this) {
    TrainingIntervalUi.Unison -> R.string.training_unison; TrainingIntervalUi.MinorSecond -> R.string.training_minor_second
    TrainingIntervalUi.MajorSecond -> R.string.training_major_second; TrainingIntervalUi.MinorThird -> R.string.training_minor_third
    TrainingIntervalUi.MajorThird -> R.string.training_major_third; TrainingIntervalUi.PerfectFourth -> R.string.training_perfect_fourth
    TrainingIntervalUi.Tritone -> R.string.training_tritone; TrainingIntervalUi.PerfectFifth -> R.string.training_perfect_fifth
    TrainingIntervalUi.MinorSixth -> R.string.training_minor_sixth; TrainingIntervalUi.MajorSixth -> R.string.training_major_sixth
    TrainingIntervalUi.MinorSeventh -> R.string.training_minor_seventh; TrainingIntervalUi.MajorSeventh -> R.string.training_major_seventh; TrainingIntervalUi.Octave -> R.string.training_octave
}
