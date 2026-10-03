package com.pekochan069.guitarlearner.ui

import com.pekochan069.guitarlearner.ui.demo.DesignFoundationApp
import com.pekochan069.guitarlearner.presentation.contract.FoundationScreen
import com.pekochan069.guitarlearner.presentation.contract.FoundationState
import com.slack.circuit.runtime.CircuitContext
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.runtime.ui.Ui
import com.slack.circuit.runtime.ui.ui

object FoundationUiFactory : Ui.Factory {
    override fun create(screen: Screen, context: CircuitContext): Ui<*>? =
        if (screen == FoundationScreen) ui<FoundationState> { state, modifier -> DesignFoundationApp(state, modifier) } else null
}
