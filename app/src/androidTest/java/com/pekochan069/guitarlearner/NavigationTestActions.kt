package com.pekochan069.guitarlearner

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.pekochan069.guitarlearner.presentation.contract.DevelopmentSample

internal fun SemanticsNodeInteractionsProvider.openMetronome() {
    if (onAllNodesWithTag("navigate_up").fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag("navigate_up").performClick()
    }
    onNodeWithTag("feature_Metronome").performScrollTo().performClick()
}

internal fun SemanticsNodeInteractionsProvider.openDevelopmentSample(sample: DevelopmentSample) {
    onNodeWithTag("settings").performClick()
    onNodeWithTag("sample_" + sample.name).performScrollTo().performClick()
}
