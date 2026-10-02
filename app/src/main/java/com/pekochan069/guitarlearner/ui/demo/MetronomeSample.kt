package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.R
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MetronomeSample(
    bpm: Int,
    running: Boolean,
    onBpmChange: (Int) -> Unit,
    onRunningChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.tempo), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Text(
                stringResource(R.string.tempo_value, bpm),
                Modifier.testTag("bpm_value"),
                style = MaterialTheme.typography.displayLarge,
                color = colors.primary,
            )
            Text(stringResource(R.string.beats_per_minute), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                repeat(4) { beat ->
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = if (running && beat == 0) colors.primary else colors.secondaryContainer,
                    ) {}
                }
            }
            Text(stringResource(R.string.meter_sample), style = MaterialTheme.typography.labelLarge)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (running) colors.primaryContainer else colors.surfaceContainerHighest,
            ) {
                Text(
                    stringResource(if (running) R.string.state_running else R.string.state_stopped),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("metronome_status")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
    SectionHeading(R.string.set_tempo)
    val tempoLabel = stringResource(R.string.tempo)
    val tempoDescription = pluralStringResource(R.plurals.tempo_bpm, bpm, bpm)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = { onBpmChange(bpm - 1) },
            enabled = bpm > 40,
            modifier = Modifier.size(56.dp).testTag("decrease_bpm"),
        ) {
            Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.decrease_tempo))
        }
        Slider(
            value = bpm.toFloat(),
            onValueChange = { onBpmChange(it.roundToInt()) },
            valueRange = 40f..240f,
            steps = 199,
            modifier = Modifier.weight(1f).testTag("tempo_slider").semantics {
                contentDescription = tempoLabel
                stateDescription = tempoDescription
            },
        )
        FilledTonalIconButton(
            onClick = { onBpmChange(bpm + 1) },
            enabled = bpm < 240,
            modifier = Modifier.size(56.dp).testTag("increase_bpm"),
        ) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.increase_tempo))
        }
    }
    Text(
        stringResource(R.string.tempo_range),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
    )
    Button(
        onClick = { onRunningChange(!running) },
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.LargeContainerHeight),
        contentPadding = ButtonDefaults.LargeContentPadding,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("toggle_metronome"),
    ) {
        Icon(
            painterResource(if (running) R.drawable.ic_stop else R.drawable.ic_play),
            null,
            Modifier.padding(end = 12.dp).size(32.dp),
        )
        Text(
            stringResource(if (running) R.string.stop_demo else R.string.start_demo),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    Text(
        stringResource(R.string.metronome_simulation),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
    )
}

@Preview(showBackground = true)
@Composable
private fun MetronomePreview() {
    GuitarLearnerTheme(darkTheme = false) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            MetronomeSample(90, false, {}, {})
        }
    }
}
