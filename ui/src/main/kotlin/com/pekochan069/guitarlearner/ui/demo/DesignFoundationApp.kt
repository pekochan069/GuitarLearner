package com.pekochan069.guitarlearner.ui.demo

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.ui.R
import com.pekochan069.guitarlearner.presentation.contract.AppearanceNotice
import com.pekochan069.guitarlearner.presentation.contract.DevelopmentSample
import com.pekochan069.guitarlearner.presentation.contract.FeatureCategory
import com.pekochan069.guitarlearner.presentation.contract.FeatureId
import com.pekochan069.guitarlearner.presentation.contract.FoundationDestination
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.FoundationState
import com.pekochan069.guitarlearner.presentation.contract.LanguageOption
import com.pekochan069.guitarlearner.presentation.contract.SettingsStatus
import com.pekochan069.guitarlearner.presentation.contract.ThemeOption
import com.pekochan069.guitarlearner.presentation.contract.TrainingStageUi
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion

private val FoundationDestination.title: Int? get() = when (this) {
    FoundationDestination.Home -> null
    is FoundationDestination.Feature -> when (id) {
        FeatureId.Metronome -> R.string.metronome_title
        FeatureId.Chords -> R.string.chord_title
        FeatureId.Tuner -> R.string.tuner_title
        FeatureId.Training -> R.string.training_title
    }
    is FoundationDestination.Sample -> id.title
}
private val DevelopmentSample.title: Int get() = when (this) {
    DevelopmentSample.Gallery -> R.string.gallery_title
}
private val FeatureCategory.label: Int get() = when (this) {
    FeatureCategory.Tools -> R.string.category_tools
    FeatureCategory.Training -> R.string.category_training
    FeatureCategory.Learning -> R.string.category_learning
}
private val ThemeOption.label: Int get() = when (this) {
    ThemeOption.System -> R.string.theme_system
    ThemeOption.Light -> R.string.theme_light
    ThemeOption.Dark -> R.string.theme_dark
}
private val LanguageOption.testTag: String get() = when (this) {
    LanguageOption.System -> "language_"
    LanguageOption.Korean -> "language_ko"
    LanguageOption.English -> "language_en"
}
private val AppearanceNotice.label: Int get() = when (this) {
    AppearanceNotice.ReadFailed -> R.string.appearance_read_failed
    AppearanceNotice.SaveFailed -> R.string.appearance_save_failed
    AppearanceNotice.ApplyFailed -> R.string.appearance_apply_failed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignFoundationApp(
    state: FoundationState,
    modifier: Modifier = Modifier,
): Unit {
    val destination = state.destination
    val metronome = destination == FoundationDestination.Feature(FeatureId.Metronome)
    val chords = destination == FoundationDestination.Feature(FeatureId.Chords)
    val contentSpacing = when { metronome -> 12.dp; chords -> 16.dp; else -> 24.dp }
    val homeScroll = rememberScrollState()
    val preferencesEnabled = state.settingsStatus != SettingsStatus.Saving
    val trainingPage = if (destination == FoundationDestination.Feature(FeatureId.Training)) when (val stage = state.training.stage) {
        TrainingStageUi.Setup -> "setup"
        is TrainingStageUi.Question -> stage.key
        is TrainingStageUi.Results -> "results"
    } else null

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (chords) R.string.chord_title else R.string.app_name),
                        modifier = if (chords) Modifier.testTag("destination_title").semantics { heading() } else Modifier,
                        style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    if (destination != FoundationDestination.Home) {
                        IconButton(onClick = { state.eventSink(FoundationEvent.NavigateBack) },
                            modifier = Modifier.testTag("navigate_up")) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back_to_home))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { state.eventSink(FoundationEvent.SetSettingsOpen(true)) },
                        modifier = Modifier.testTag("settings")) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.preferences))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            key(destination, trainingPage) {
                Column(
                    Modifier.widthIn(max = 680.dp).fillMaxWidth()
                        .verticalScroll(if (destination == FoundationDestination.Home) homeScroll else rememberScrollState())
                        .testTag(if (destination == FoundationDestination.Home) "home_scroll" else "feature_scroll")
                        .padding(contentSpacing),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing),
                ) {
                    destination.title?.takeUnless { chords }?.let { title ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(title),
                                Modifier.testTag("destination_title").semantics { heading() },
                                style = if (metronome) MaterialTheme.typography.headlineMedium
                                    else MaterialTheme.typography.headlineLarge,
                            )
                            if (destination is FoundationDestination.Sample) {
                                Text(
                                    stringResource(when (destination.id) {
                                        DevelopmentSample.Gallery -> R.string.gallery_subtitle
                                    }),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (!metronome) CompactMetronomeControl(state.metronome, state.eventSink)
                    if (destination is FoundationDestination.Sample) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                stringResource(R.string.gallery_demo_notice),
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when (destination) {
                        FoundationDestination.Home -> HomeCatalog(state)
                        is FoundationDestination.Feature -> when (destination.id) {
                            FeatureId.Metronome -> MetronomeSample(
                                state = state.metronome,
                                eventSink = state.eventSink,
                            )
                            FeatureId.Chords -> ChordTool(state.chords) { state.eventSink(FoundationEvent.Chord(it)) }
                            FeatureId.Tuner -> TunerScreen(state.tuner, state.eventSink)
                            FeatureId.Training -> TrainingScreen(state.training) { state.eventSink(FoundationEvent.Training(it)) }
                        }
                        is FoundationDestination.Sample -> when (destination.id) {
                            DevelopmentSample.Gallery -> ComponentGallery(
                                selected = state.gallerySelected,
                                onSelectedChange = { state.eventSink(FoundationEvent.SetGallerySelected(it)) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.settingsOpen) {
        ModalBottomSheet(onDismissRequest = { state.eventSink(FoundationEvent.SetSettingsOpen(false)) }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.preferences),
                        Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(
                        onClick = { state.eventSink(FoundationEvent.SetSettingsOpen(false)) },
                        modifier = Modifier.testTag("close_settings"),
                    ) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close))
                    }
                }
                Text(
                    stringResource(R.string.preferences_description),
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val status = state.settingsStatus
                if (status is SettingsStatus.Failed) {
                    Text(
                        stringResource(status.notice.label),
                        Modifier.padding(horizontal = 24.dp).testTag("appearance_error")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    stringResource(R.string.appearance),
                    Modifier.padding(horizontal = 24.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(Modifier.selectableGroup()) {
                    ThemeOption.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(
                                    selected = state.theme == mode,
                                    enabled = preferencesEnabled,
                                    role = Role.RadioButton,
                                    onClick = { state.eventSink(FoundationEvent.SelectTheme(mode)) },
                                )
                                .testTag("theme_" + mode.name)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.theme == mode, onClick = null, enabled = preferencesEnabled)
                            Text(stringResource(mode.label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (state.developmentSamples.isNotEmpty()) {
                    Text(stringResource(R.string.development_samples),
                        Modifier.padding(horizontal = 24.dp).testTag("development_samples").semantics { heading() },
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.development_samples_description),
                        Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.developmentSamples.forEach { sample ->
                        FilledTonalButton(
                            onClick = { state.eventSink(FoundationEvent.OpenSample(sample)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                                .heightIn(min = 48.dp).testTag("sample_" + sample.name),
                        ) {
                            Icon(painterResource(when (sample) {
                                DevelopmentSample.Gallery -> R.drawable.ic_gallery
                            }), null, Modifier.padding(end = 8.dp))
                            Text(stringResource(sample.title))
                        }
                    }
                }
                Text(
                    stringResource(R.string.language),
                    Modifier.padding(horizontal = 24.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(Modifier.selectableGroup()) {
                    listOf(
                        LanguageOption.System to R.string.language_system,
                        LanguageOption.Korean to R.string.language_korean,
                        LanguageOption.English to R.string.language_english,
                    ).forEach { (language, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(
                                    selected = state.language == language,
                                    enabled = preferencesEnabled,
                                    role = Role.RadioButton,
                                    onClick = { state.eventSink(FoundationEvent.SelectLanguage(language)) },
                                )
                                .testTag(language.testTag)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.language == language, onClick = null, enabled = preferencesEnabled)
                            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCatalog(state: FoundationState) {
    state.featureGroups.forEach { group ->
        Column(Modifier.fillMaxWidth().testTag("category_" + group.category.name),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(group.category.label)
            group.features.chunked(2).forEach { features ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    features.forEach { feature ->
                        Card(
                            onClick = { state.eventSink(FoundationEvent.OpenFeature(feature)) },
                            modifier = Modifier.weight(1f).testTag("feature_" + feature.name),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = if (feature == FeatureId.Chords) {
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                            } else {
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            },
                        ) {
                            Row(Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(when (feature) {
                                    FeatureId.Metronome -> R.drawable.ic_tempo
                                    FeatureId.Tuner -> R.drawable.ic_tuner
                                    FeatureId.Chords -> R.drawable.ic_chords
                                    FeatureId.Training -> R.drawable.ic_play
                                }), null, Modifier.size(32.dp))
                                Text(stringResource(when (feature) {
                                    FeatureId.Metronome -> R.string.metronome_title
                                    FeatureId.Tuner -> R.string.tuner_title
                                    FeatureId.Chords -> R.string.chord_title
                                    FeatureId.Training -> R.string.training_title
                                }), Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    if (features.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun SectionHeading(@StringRes title: Int): Unit {
    Text(
        stringResource(title),
        Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
}
