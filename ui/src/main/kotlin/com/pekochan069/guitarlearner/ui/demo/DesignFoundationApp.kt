package com.pekochan069.guitarlearner.ui.demo

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pekochan069.guitarlearner.ui.R
import com.pekochan069.guitarlearner.presentation.contract.AppearanceNotice
import com.pekochan069.guitarlearner.presentation.contract.FoundationEvent
import com.pekochan069.guitarlearner.presentation.contract.FoundationState
import com.pekochan069.guitarlearner.presentation.contract.LanguageOption
import com.pekochan069.guitarlearner.presentation.contract.Page
import com.pekochan069.guitarlearner.presentation.contract.SettingsStatus
import com.pekochan069.guitarlearner.presentation.contract.ThemeOption
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion

private val Page.label: Int get() = when (this) {
    Page.Tuner -> R.string.page_tuner
    Page.Metronome -> R.string.page_metronome
    Page.Gallery -> R.string.page_gallery
}
private val Page.title: Int get() = when (this) {
    Page.Tuner -> R.string.tuner_title
    Page.Metronome -> R.string.metronome_title
    Page.Gallery -> R.string.gallery_title
}
private val Page.subtitle: Int get() = when (this) {
    Page.Tuner -> R.string.tuner_subtitle
    Page.Metronome -> R.string.metronome_subtitle
    Page.Gallery -> R.string.gallery_subtitle
}
private val Page.icon: Int get() = when (this) {
    Page.Tuner -> R.drawable.ic_tuner
    Page.Metronome -> R.drawable.ic_tempo
    Page.Gallery -> R.drawable.ic_gallery
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
    val page = state.page
    val preferencesEnabled = state.settingsStatus != SettingsStatus.Saving

    Scaffold(
        modifier = modifier,
        topBar = {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { state.eventSink(FoundationEvent.SetSettingsOpen(true)) },
                    modifier = Modifier.testTag("settings"),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_settings),
                        stringResource(R.string.preferences),
                    )
                }
            }
        },
        bottomBar = {
            if (LocalDensity.current.fontScale > 1.3f) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = page.ordinal,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    edgePadding = 8.dp,
                ) {
                    Page.entries.forEach { destination ->
                        Tab(
                            selected = page == destination,
                            onClick = { state.eventSink(FoundationEvent.SelectPage(destination)) },
                            modifier = Modifier.testTag("page_" + destination.name),
                        ) {
                            Row(
                                Modifier.heightIn(min = 64.dp).padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(destination.icon), null, Modifier.size(24.dp))
                                Text(stringResource(destination.label))
                            }
                        }
                    }
                }
            } else {
                NavigationBar {
                    Page.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = { state.eventSink(FoundationEvent.SelectPage(destination)) },
                            icon = { Icon(painterResource(destination.icon), null) },
                            label = { Text(stringResource(destination.label)) },
                            modifier = Modifier.testTag("page_" + destination.name),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            key(page) {
                Column(
                    Modifier.widthIn(max = 680.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(page.title),
                            Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            stringResource(page.subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            stringResource(when (page) {
                                Page.Tuner -> R.string.tuner_demo_notice
                                Page.Metronome -> R.string.metronome_playback_notice
                                Page.Gallery -> R.string.gallery_demo_notice
                            }),
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (page) {
                        Page.Tuner -> TunerSample(
                            state.reading,
                            onReadingChange = { state.eventSink(FoundationEvent.SetReading(it)) },
                        )
                        Page.Metronome -> MetronomeSample(
                            state = state.metronome,
                            eventSink = state.eventSink,
                        )
                        Page.Gallery -> ComponentGallery(
                            selected = state.gallerySelected,
                            onSelectedChange = { state.eventSink(FoundationEvent.SetGallerySelected(it)) },
                        )
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
internal fun SectionHeading(@StringRes title: Int): Unit {
    Text(
        stringResource(title),
        Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
}
