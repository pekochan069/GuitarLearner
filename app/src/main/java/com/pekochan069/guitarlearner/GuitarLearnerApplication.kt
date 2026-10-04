package com.pekochan069.guitarlearner

import android.app.Application
import com.pekochan069.guitarlearner.adapters.AndroidAppearanceHost
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.domain.AppearanceSettings
import com.pekochan069.guitarlearner.domain.Metronome
import com.pekochan069.guitarlearner.presentation.logic.FoundationPresenter
import com.pekochan069.guitarlearner.ui.FoundationUiFactory
import com.slack.circuit.foundation.Circuit
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

class GuitarLearnerApplication : Application() {
    private val defaultGraph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(this, AndroidMetronomeHost(
            this, MetronomePlaybackService::class.java, MainActivity::class.java,
            getSharedPreferences("metronome", MODE_PRIVATE),
        ))
    }
    internal var graphOverride: AppGraph? = null
    val graph: AppGraph get() = graphOverride ?: defaultGraph
}

private abstract class AppScope

@DependencyGraph(AppScope::class)
interface AppGraph {
    val appearanceHost: AndroidAppearanceHost
    val metronomeHost: AndroidMetronomeHost
    val circuit: Circuit

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppearance(application: Application): AndroidAppearanceHost =
        AndroidAppearanceHost(application.getSharedPreferences("appearance", Application.MODE_PRIVATE))

    @Provides
    fun provideSettings(host: AndroidAppearanceHost): AppearanceSettings = host

    @Provides
    fun provideMetronomeCapability(host: AndroidMetronomeHost): Metronome = host

    @Provides
    fun providePresenterFactory(settings: AppearanceSettings, metronome: Metronome): FoundationPresenter.Factory =
        FoundationPresenter.Factory(settings, metronome)

    @Provides
    @SingleIn(AppScope::class)
    fun provideCircuit(factory: FoundationPresenter.Factory): Circuit = Circuit.Builder()
        .addPresenterFactory(factory)
        .addUiFactory(FoundationUiFactory)
        .build()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application, @Provides metronomeHost: AndroidMetronomeHost): AppGraph
    }
}
