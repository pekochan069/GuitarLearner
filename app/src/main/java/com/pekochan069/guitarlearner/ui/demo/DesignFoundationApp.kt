package com.pekochan069.guitarlearner.ui.demo

import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.pekochan069.guitarlearner.R
import com.pekochan069.guitarlearner.ThemeMode

private enum class DemoPage(
    @param:StringRes val label: Int,
    @param:StringRes val title: Int,
    @param:StringRes val subtitle: Int,
    @param:DrawableRes val icon: Int,
) {
    Tuner(R.string.page_tuner, R.string.tuner_title, R.string.tuner_subtitle, R.drawable.ic_tuner),
    Metronome(R.string.page_metronome, R.string.metronome_title, R.string.metronome_subtitle, R.drawable.ic_tempo),
    Gallery(R.string.page_gallery, R.string.gallery_title, R.string.gallery_subtitle, R.drawable.ic_gallery),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignFoundationApp(
    themeMode: ThemeMode,
    languageTag: String,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
) {
    var pageName by rememberSaveable { mutableStateOf(DemoPage.Tuner.name) }
    var readingName by rememberSaveable { mutableStateOf(TunerReading.NoSignal.name) }
    var bpm by rememberSaveable { mutableIntStateOf(90) }
    var running by rememberSaveable { mutableStateOf(false) }
    var gallerySelected by rememberSaveable { mutableStateOf(true) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val page = DemoPage.valueOf(pageName)

    Scaffold(
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
                    onClick = { settingsOpen = true },
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
                    DemoPage.entries.forEach { destination ->
                        Tab(
                            selected = page == destination,
                            onClick = { pageName = destination.name },
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
                    DemoPage.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = { pageName = destination.name },
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
                            stringResource(R.string.demo_notice),
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (page) {
                        DemoPage.Tuner -> TunerSample(
                            TunerReading.valueOf(readingName),
                            onReadingChange = { readingName = it.name },
                        )
                        DemoPage.Metronome -> MetronomeSample(
                            bpm = bpm,
                            running = running,
                            onBpmChange = { bpm = it },
                            onRunningChange = { running = it },
                        )
                        DemoPage.Gallery -> ComponentGallery(
                            selected = gallerySelected,
                            onSelectedChange = { gallerySelected = it },
                        )
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }) {
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
                        onClick = { settingsOpen = false },
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
                Text(
                    stringResource(R.string.appearance),
                    Modifier.padding(horizontal = 24.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(
                                    selected = themeMode == mode,
                                    role = Role.RadioButton,
                                    onClick = { onThemeChange(mode) },
                                )
                                .testTag("theme_" + mode.name)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = null)
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
                        "" to R.string.language_system,
                        "ko" to R.string.language_korean,
                        "en" to R.string.language_english,
                    ).forEach { (tag, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(
                                    selected = languageTag == tag,
                                    role = Role.RadioButton,
                                    onClick = { onLanguageChange(tag) },
                                )
                                .testTag("language_" + tag)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = languageTag == tag, onClick = null)
                            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionHeading(@StringRes title: Int) {
    Text(
        stringResource(title),
        Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
}
