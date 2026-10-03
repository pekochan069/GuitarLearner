package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.ui.R
import com.pekochan069.guitarlearner.presentation.contract.Reading
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme
import kotlin.math.abs

private val Reading.label: Int get() = when (this) {
    Reading.NoSignal -> R.string.reading_no_signal
    Reading.Flat -> R.string.reading_flat
    Reading.InTune -> R.string.reading_in_tune
    Reading.Sharp -> R.string.reading_sharp
}
private val Reading.guidance: Int get() = when (this) {
    Reading.NoSignal -> R.string.guidance_no_signal
    Reading.Flat -> R.string.guidance_flat
    Reading.InTune -> R.string.guidance_in_tune
    Reading.Sharp -> R.string.guidance_sharp
}
private val Reading.cents: Int? get() = when (this) {
    Reading.NoSignal -> null
    Reading.Flat -> -18
    Reading.InTune -> 0
    Reading.Sharp -> 18
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TunerSample(reading: Reading, onReadingChange: (Reading) -> Unit): Unit {
    val cents = reading.cents
    val colors = MaterialTheme.colorScheme
    val needle by animateFloatAsState(
        targetValue = (cents ?: 0) / 50f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "pitchIndicator",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (reading == Reading.InTune) colors.primaryContainer else colors.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.tuner_target),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
            Text(
                stringResource(if (reading == Reading.NoSignal) R.string.note_placeholder else R.string.note_a2),
                style = MaterialTheme.typography.displayLarge,
                color = colors.primary,
            )
            Text(
                stringResource(R.string.reference_frequency),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Canvas(Modifier.fillMaxWidth().height(80.dp).clearAndSetSemantics {}) {
                val left = 12.dp.toPx()
                val width = size.width - left * 2
                val centerY = size.height / 2
                drawLine(colors.outlineVariant, Offset(left, centerY), Offset(left + width, centerY), 2.dp.toPx())
                for (tick in 0..10) {
                    val x = left + width * tick / 10
                    val halfHeight = if (tick == 5) 16.dp.toPx() else 6.dp.toPx()
                    drawLine(colors.outline, Offset(x, centerY - halfHeight), Offset(x, centerY + halfHeight), 2.dp.toPx())
                }
                if (cents != null) {
                    val x = left + width * (needle + 1) / 2
                    drawLine(colors.primary, Offset(x, centerY - 28.dp.toPx()), Offset(x, centerY + 28.dp.toPx()), 4.dp.toPx())
                    drawCircle(colors.primary, 6.dp.toPx(), Offset(x, centerY - 28.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.pitch_low), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.pitch_center), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.pitch_high), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(reading.label),
                Modifier.testTag("tuner_status").semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (cents != null) {
                Text(
                    when {
                        cents < 0 -> pluralStringResource(R.plurals.cents_flat, abs(cents), abs(cents))
                        cents > 0 -> pluralStringResource(R.plurals.cents_sharp, cents, cents)
                        else -> stringResource(R.string.cents_center)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                stringResource(reading.guidance),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    SectionHeading(R.string.try_reading)
    Text(
        stringResource(R.string.reading_description),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Reading.entries.forEach { sample ->
            FilterChip(
                selected = reading == sample,
                onClick = { onReadingChange(sample) },
                label = { Text(stringResource(sample.label)) },
                leadingIcon = if (reading == sample) {
                    { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) }
                } else null,
                modifier = Modifier.heightIn(min = 48.dp).testTag("reading_" + sample.name),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TunerLightPreview() {
    GuitarLearnerTheme(darkTheme = false) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            TunerSample(Reading.InTune, {})
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TunerDarkPreview() {
    GuitarLearnerTheme(darkTheme = true) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            TunerSample(Reading.Sharp, {})
        }
    }
}
