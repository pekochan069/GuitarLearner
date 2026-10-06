package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pekochan069.guitarlearner.presentation.contract.*
import com.pekochan069.guitarlearner.ui.R
import kotlin.math.abs

private data class ProgressionDrag(val origin: Int, val destination: Int, val pointerY: Float, val grabY: Float, val height: Int) {
    fun preview(steps: List<ProgressionStepUi>): List<ProgressionStepUi> = steps.toMutableList().apply {
        add(destination, removeAt(origin))
    }
}

@Composable
internal fun ProgressionSequence(state: ProgressionUiState, eventSink: (ProgressionEvent) -> Unit, modifier: Modifier,
    header: @Composable () -> Unit) {
    val list = rememberLazyListState()
    val enabled = !state.busy && !state.readFailed && state.sheet == ProgressionSheetUi.None
    var drag by remember(state.steps, enabled) { mutableStateOf<ProgressionDrag?>(null) }
    val currentSink by rememberUpdatedState(eventSink)
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    val speed = with(LocalDensity.current) { 600.dp.toPx() }
    fun move(pointerY: Float) {
        val current = drag ?: return
        val center = pointerY - current.grabY + current.height / 2f
        val closest = list.layoutInfo.visibleItemsInfo.filter { it.key is Int }.minByOrNull { abs(center - it.offset - it.size / 2f) }
        val destination = closest?.let { it.index - 1 - if (state.name.isNotBlank()) 1 else 0 } ?: current.destination
        drag = current.copy(pointerY = pointerY, destination = destination)
    }
    fun scroll(elapsedMillis: Long) {
        val current = drag ?: return
        val layout = list.layoutInfo
        val fraction = when {
            current.pointerY < layout.viewportStartOffset + edge -> -((layout.viewportStartOffset + edge - current.pointerY) / edge).coerceIn(0f, 1f)
            current.pointerY > layout.viewportEndOffset - edge -> ((current.pointerY - layout.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
        if (fraction != 0f && (if (fraction > 0) list.canScrollForward else list.canScrollBackward)) {
            val delta = (fraction * speed * elapsedMillis.coerceIn(0, 50) / 1000).toInt()
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset + delta)
        }
    }
    LazyColumn(modifier.testTag("progression_list"), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "header") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { header() } }
        if (state.name.isNotBlank()) item(key = "name") {
            Text(state.name, Modifier.testTag("progression_draft_name"), style = MaterialTheme.typography.titleSmall)
        }
        if (state.steps.isEmpty()) item(key = "empty") {
            Text(stringResource(R.string.progression_empty), Modifier.testTag("progression_empty"), style = MaterialTheme.typography.bodyMedium)
        }
        items(drag?.preview(state.steps) ?: state.steps, key = { it.index }) { step ->
            val dragged = drag?.origin == step.index
            val playing = state.playingIndex == step.index && state.transport == ProgressionTransportUi.Playing
            val title = step.title()
            val movingDescription = stringResource(R.string.progression_reordering, title)
            val playingDescription = stringResource(R.string.progression_playing)
            val earlier = stringResource(R.string.progression_up)
            val later = stringResource(R.string.progression_down)
            Surface(Modifier.zIndex(if (dragged) 1f else 0f).graphicsLayer {
                val current = drag
                translationY = if (current?.origin == step.index) {
                    current.pointerY - current.grabY - (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == step.index }?.offset ?: 0)
                } else 0f
            }, shape = MaterialTheme.shapes.large, shadowElevation = if (dragged) 8.dp else 0.dp,
                color = when { dragged -> MaterialTheme.colorScheme.primaryContainer
                    step.index == state.playingIndex -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerLow }) {
                ListItem(headlineContent = { Text(title) }, supportingContent = { Text(step.noteLabel()) },
                    trailingContent = {
                        IconButton(onClick = { currentSink(ProgressionEvent.Remove(step.index)) }, enabled = enabled && drag == null,
                            modifier = Modifier.size(48.dp).testTag("progression_quick_remove_${step.index}")) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.progression_remove_named, title, step.index + 1))
                        }
                    }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().pointerInput(state.steps, enabled) {
                        if (enabled && state.steps.size > 1) awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == step.index } ?: return@awaitEachGesture
                            drag = ProgressionDrag(step.index, step.index, item.offset + held.position.y, held.position.y, item.size)
                            var completed: ProgressionDrag? = null
                            var time = held.uptimeMillis
                            try {
                                while (drag != null) {
                                    val event = withTimeoutOrNull(16) { awaitPointerEvent() }
                                    val change = event?.changes?.firstOrNull { it.id == held.id }
                                    if (event != null) {
                                        if (change == null || change.isConsumed || event.changes.any { it.id != held.id && it.pressed }) break
                                        val amount = change.positionChange().y
                                        change.consume()
                                        if (!change.pressed) { completed = drag; break }
                                        move((drag?.pointerY ?: break) + amount)
                                    } else drag?.let { move(it.pointerY) }
                                    val now = change?.uptimeMillis ?: time + 16
                                    scroll(now - time)
                                    time = now
                                }
                            } finally {
                                drag = null
                            }
                            completed?.takeIf { it.origin != it.destination }?.let {
                                currentSink(ProgressionEvent.Move(it.origin, it.destination - it.origin))
                            }
                        }
                    }.clickable(enabled = drag == null, role = Role.Button) { currentSink(ProgressionEvent.OpenStep(step.index)) }
                        .testTag("progression_step_${step.index}").semantics {
                            selected = step.index == state.selectedIndex
                            if (dragged) stateDescription = movingDescription else if (playing) stateDescription = playingDescription
                            if (enabled) customActions = buildList {
                                if (step.index > 0) add(CustomAccessibilityAction(earlier) { currentSink(ProgressionEvent.Move(step.index, -1)); true })
                                if (step.index < state.steps.lastIndex) add(CustomAccessibilityAction(later) { currentSink(ProgressionEvent.Move(step.index, 1)); true })
                            }
                        })
            }
        }
    }
}
