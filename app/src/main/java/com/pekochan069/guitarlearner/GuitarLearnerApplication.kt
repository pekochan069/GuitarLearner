package com.pekochan069.guitarlearner

import android.app.Application
import com.pekochan069.guitarlearner.adapters.AndroidAppearanceHost
import com.pekochan069.guitarlearner.adapters.AndroidChordsHost
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.adapters.AndroidTunerHost
import com.pekochan069.guitarlearner.adapters.AndroidTrainingHost
import com.pekochan069.guitarlearner.adapters.AndroidLearningHost
import com.pekochan069.guitarlearner.domain.Chords
import com.pekochan069.guitarlearner.domain.Metronome
import com.pekochan069.guitarlearner.domain.Tuner
import com.pekochan069.guitarlearner.domain.Training
import com.pekochan069.guitarlearner.domain.Learning
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
    val tunerHostFactory: AndroidTunerHost.Factory
    val trainingHostFactory: AndroidTrainingHost.Factory
    val learningHostFactory: AndroidLearningHost.Factory
    val chords: Chords

    fun createPresenterFactory(tuner: Tuner, training: Training, learning: Learning): FoundationPresenter.Factory =
        FoundationPresenter.Factory(appearanceHost, metronomeHost, tuner, chords, training, learning, developmentSamplesEnabled = BuildConfig.DEBUG)

    fun createCircuit(factory: FoundationPresenter.Factory): Circuit = Circuit.Builder()
        .addPresenterFactory(factory)
        .addUiFactory(FoundationUiFactory)
        .build()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppearance(application: Application): AndroidAppearanceHost =
        AndroidAppearanceHost(application.getSharedPreferences("appearance", Application.MODE_PRIVATE))

    @Provides
    fun provideMetronomeCapability(host: AndroidMetronomeHost): Metronome = host

    @Provides
    @SingleIn(AppScope::class)
    fun provideChords(application: Application): Chords =
        AndroidChordsHost(application.getSharedPreferences("chords", Application.MODE_PRIVATE))

    @Provides
    @SingleIn(AppScope::class)
    fun provideTunerFactory(application: Application, metronome: Metronome): AndroidTunerHost.Factory =
        AndroidTunerHost.Factory(application, application.getSharedPreferences("tuner", Application.MODE_PRIVATE), metronome)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTrainingFactory(application: Application, metronome: Metronome): AndroidTrainingHost.Factory =
        AndroidTrainingHost.Factory(application, application.getSharedPreferences("training", Application.MODE_PRIVATE), metronome)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLearningFactory(application: Application, metronome: Metronome): AndroidLearningHost.Factory =
        AndroidLearningHost.Factory(application, application.getSharedPreferences("learning", Application.MODE_PRIVATE), metronome)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application, @Provides metronomeHost: AndroidMetronomeHost): AppGraph
    }
}
