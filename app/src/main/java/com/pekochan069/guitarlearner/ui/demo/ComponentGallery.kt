package com.pekochan069.guitarlearner.ui.demo

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.R
import com.pekochan069.guitarlearner.ui.theme.GuitarLearnerTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ComponentGallery(selected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val corner by animateDpAsState(
        targetValue = if (selected) 32.dp else 8.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "galleryShape",
    )
    SectionHeading(R.string.controls)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(selected, role = Role.Checkbox, onValueChange = onSelectedChange)
                    .testTag("gallery_selection"),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(selected, onCheckedChange = null)
                Text(stringResource(R.string.selected_control), style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(selected, role = Role.Switch, onValueChange = onSelectedChange),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(selected, onCheckedChange = null)
                Text(
                    stringResource(if (selected) R.string.switch_on else R.string.switch_off),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.disabled_control))
            }
            val tempoError = stringResource(R.string.tempo_error)
            OutlinedTextField(
                value = stringResource(R.string.invalid_tempo),
                onValueChange = {},
                readOnly = true,
                isError = true,
                label = { Text(stringResource(R.string.tempo)) },
                supportingText = { Text(tempoError) },
                modifier = Modifier.fillMaxWidth().semantics { error(tempoError) },
            )
        }
    }
    SectionHeading(R.string.type_and_notation)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.primaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.musical_symbols), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.symbol_names), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.typography_description),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    SectionHeading(R.string.color_and_shape)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        listOf(
            Triple(R.string.color_primary, colors.primary, colors.onPrimary),
            Triple(R.string.color_secondary, colors.secondaryContainer, colors.onSecondaryContainer),
            Triple(R.string.color_tertiary, colors.tertiaryContainer, colors.onTertiaryContainer),
        ).forEachIndexed { index, (label, container, content) ->
            Surface(
                modifier = Modifier.widthIn(min = 128.dp).weight(1f).heightIn(min = 96.dp),
                color = container,
                contentColor = content,
                shape = when (index) {
                    0 -> MaterialTheme.shapes.small
                    1 -> MaterialTheme.shapes.large
                    else -> MaterialTheme.shapes.extraLarge
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            when (index) {
                                0 -> R.string.shape_small
                                1 -> R.string.shape_large
                                else -> R.string.shape_extra_large
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    SectionHeading(R.string.expressive_motion)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(corner),
        color = colors.tertiaryContainer,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (selected) R.string.motion_rounded else R.string.motion_square),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.motion_description), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { onSelectedChange(!selected) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.try_motion))
            }
        }
    }
}

@Preview(showBackground = true, locale = "ko")
@Composable
private fun GalleryPreview() {
    GuitarLearnerTheme(darkTheme = false) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            ComponentGallery(true, {})
        }
    }
}
