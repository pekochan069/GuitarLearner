package com.pekochan069.guitarlearner

import android.app.Application
import com.pekochan069.guitarlearner.adapters.AndroidAppearanceHost
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.presentation.logic.FoundationPresenter
import com.pekochan069.guitarlearner.ui.FoundationUiFactory
import com.slack.circuit.foundation.Circuit
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

class GuitarLearnerApplication : Application() {
    val graph: AppGraph by lazy { createGraphFactory<AppGraph.Factory>().create(this) }
}

private abstract class AppScope

@DependencyGraph(AppScope::class)
interface AppGraph {
    val appearanceHost: AndroidAppearanceHost
    val circuit: Circuit

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppearance(application: Application): AndroidAppearanceHost =
        AndroidAppearanceHost(application.getSharedPreferences("appearance", Application.MODE_PRIVATE))

    @Provides
    fun provideSettings(host: AndroidAppearanceHost): AppearanceSettings = host

    @Provides
    fun providePresenterFactory(settings: AppearanceSettings): FoundationPresenter.Factory =
        FoundationPresenter.Factory(settings)

    @Provides
    @SingleIn(AppScope::class)
    fun provideCircuit(factory: FoundationPresenter.Factory): Circuit = Circuit.Builder()
        .addPresenterFactory(factory)
        .addUiFactory(FoundationUiFactory)
        .build()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
