package com.pekochan069.guitarlearner

import android.app.Application
import com.pekochan069.guitarlearner.adapters.AndroidAppearanceHost
import com.pekochan069.guitarlearner.adapters.AndroidChordsHost
import com.pekochan069.guitarlearner.adapters.AndroidMetronomeHost
import com.pekochan069.guitarlearner.adapters.AndroidProgressionsHost
import com.pekochan069.guitarlearner.adapters.AndroidTunerHost
import com.pekochan069.guitarlearner.adapters.AndroidTrainingHost
import com.pekochan069.guitarlearner.domain.Chords
import com.pekochan069.guitarlearner.domain.Metronome
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.ProgressionCommand
import com.pekochan069.guitarlearner.domain.Progressions
import com.pekochan069.guitarlearner.domain.Tuner
import com.pekochan069.guitarlearner.domain.TunerRequest
import com.pekochan069.guitarlearner.domain.Training
import com.pekochan069.guitarlearner.presentation.logic.FoundationPresenter
import com.pekochan069.guitarlearner.ui.FoundationUiFactory
import com.slack.circuit.foundation.Circuit
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.sync.Mutex

class GuitarLearnerApplication : Application() {
    internal var activeTuner: AndroidTunerHost? = null
    private val defaultGraph: AppGraph by lazy {
        val startGate = Mutex()
        lateinit var metronome: AndroidMetronomeHost
        lateinit var progressions: AndroidProgressionsHost
        metronome = AndroidMetronomeHost(
            this, MetronomePlaybackService::class.java, MainActivity::class.java,
            getSharedPreferences("metronome", MODE_PRIVATE),
            startGate = startGate, beforeStart = {
                activeTuner?.submit(TunerRequest.Stop)
                progressions.execute(ProgressionCommand.Stop).fold({ throw IllegalStateException("Progression stop failed: $it") }, {})
            },
        )
        progressions = AndroidProgressionsHost(
            this, ProgressionPlaybackService::class.java, MainActivity::class.java,
            getSharedPreferences("progressions", MODE_PRIVATE),
            startGate = startGate, beforeStart = {
                activeTuner?.submit(TunerRequest.Stop)
                metronome.execute(MetronomeCommand.Stop).fold({ throw IllegalStateException("Metronome stop failed: $it") }, {})
            },
        )
        createGraphFactory<AppGraph.Factory>().create(this, metronome, progressions)
    }
    internal var graphOverride: AppGraph? = null
    val graph: AppGraph get() = graphOverride ?: defaultGraph
}

private abstract class AppScope

@DependencyGraph(AppScope::class)
interface AppGraph {
    val appearanceHost: AndroidAppearanceHost
    val metronomeHost: AndroidMetronomeHost
    val progressionsHost: AndroidProgressionsHost
    val progressions: Progressions
    val tunerHostFactory: AndroidTunerHost.Factory
    val trainingHostFactory: AndroidTrainingHost.Factory
    val chords: Chords

    fun createPresenterFactory(tuner: Tuner, training: Training): FoundationPresenter.Factory =
        FoundationPresenter.Factory(appearanceHost, metronomeHost, tuner, chords, progressions, training, developmentSamplesEnabled = BuildConfig.DEBUG)

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
    fun provideProgressionCapability(host: AndroidProgressionsHost): Progressions = host

    @Provides
    @SingleIn(AppScope::class)
    fun provideChords(application: Application): Chords =
        AndroidChordsHost(application.getSharedPreferences("chords", Application.MODE_PRIVATE))

    @Provides
    @SingleIn(AppScope::class)
    fun provideTunerFactory(application: Application, metronome: Metronome, progressions: Progressions): AndroidTunerHost.Factory =
        AndroidTunerHost.Factory(application, application.getSharedPreferences("tuner", Application.MODE_PRIVATE), metronome, progressions,
            onCreated = { (application as GuitarLearnerApplication).activeTuner = it })

    @Provides
    @SingleIn(AppScope::class)
    fun provideTrainingFactory(application: Application, metronome: Metronome): AndroidTrainingHost.Factory =
        AndroidTrainingHost.Factory(application, application.getSharedPreferences("training", Application.MODE_PRIVATE), metronome)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application, @Provides metronomeHost: AndroidMetronomeHost,
            @Provides progressionsHost: AndroidProgressionsHost): AppGraph
    }
}
