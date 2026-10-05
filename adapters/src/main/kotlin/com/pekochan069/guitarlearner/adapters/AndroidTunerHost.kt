package com.pekochan069.guitarlearner.adapters

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.GuitarPitchDetector
import com.pekochan069.guitarlearner.domain.Metronome
import com.pekochan069.guitarlearner.domain.MetronomeCommand
import com.pekochan069.guitarlearner.domain.MonotonicNanos
import com.pekochan069.guitarlearner.domain.ToleranceStorageStatus
import com.pekochan069.guitarlearner.domain.Tuner
import com.pekochan069.guitarlearner.domain.TunerFailure
import com.pekochan069.guitarlearner.domain.TunerListening
import com.pekochan069.guitarlearner.domain.TunerRecovery
import com.pekochan069.guitarlearner.domain.TunerRequest
import com.pekochan069.guitarlearner.domain.TunerSettingsPage
import com.pekochan069.guitarlearner.domain.TunerSnapshot
import com.pekochan069.guitarlearner.domain.TuningInput
import com.pekochan069.guitarlearner.domain.TuningMemory
import com.pekochan069.guitarlearner.domain.TuningPolicy
import com.pekochan069.guitarlearner.domain.TuningTolerance
import com.pekochan069.guitarlearner.domain.YinHarmonicDetector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@JvmInline value class TunerStartId internal constructor(val value: Long)
data class TunerPermissionRequest(val id: TunerStartId)

sealed interface TunerPermissionOutcome {
    data object Granted : TunerPermissionOutcome
    data class Denied(val recovery: TunerRecovery) : TunerPermissionOutcome
    data class Failed(val failure: TunerFailure) : TunerPermissionOutcome
}

enum class TunerVisibility { Foreground, ConfigurationContinuation, Background, Locked }

class AndroidTunerHost internal constructor(
    private val ownedScope: CoroutineScope,
    private val metronome: Metronome,
    private val captureFactory: TunerCaptureFactory,
    private val storage: TunerToleranceStore,
    private val permissionGranted: () -> Boolean,
    private val openSettings: (TunerSettingsPage) -> Either<TunerFailure, Unit>,
    private val detector: GuitarPitchDetector,
    private val clock: () -> MonotonicNanos,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val analysis: CoroutineDispatcher = Dispatchers.Default,
) : Tuner, AutoCloseable {
    private val initialTolerance = storage.read()
    private val snapshot = MutableStateFlow(TunerSnapshot(
        tolerance = initialTolerance.getOrNull() ?: TuningTolerance.Normal,
        storage = initialTolerance.fold({ ToleranceStorageStatus.Failed(it) }, { ToleranceStorageStatus.Ready }),
    ))
    override val current: StateFlow<TunerSnapshot> = snapshot.asStateFlow()
    private val permission = MutableStateFlow<TunerPermissionRequest?>(null)
    val permissionRequest: StateFlow<TunerPermissionRequest?> = permission.asStateFlow()
    private val policy = TuningPolicy()
    private var memory = TuningMemory()
    private var nextStartId = 0L
    private var revision = 0L
    private var authority: StartAuthority? = null
    private var preparation: Job? = null
    private var session: CaptureSession? = null
    private var eligible = false
    private var closed = false
    private var shutdownBlocked = false

    private val watchdog = ownedScope.launch(main) {
        while (isActive) {
            delay(50)
            val requested = authority ?: continue
            val now = clock()
            session?.takeIf { it.id == requested.id }?.let { active ->
                active.capture.failure?.let { stop(it); return@let }
                if (snapshot.value.listening is TunerListening.Listening) {
                    memory = policy.reduce(memory, snapshot.value.target, snapshot.value.tolerance, TuningInput.Tick(now))
                    publishFeedback()
                    if (now.value - active.capture.lastReadProgress.value >= TuningPolicy.EXPIRY_NANOS) stop(TunerFailure.NoInput)
                } else if (now.value - requested.phaseAt.value >= 3_000_000_000L) stop(TunerFailure.NoInput)
            }
        }
    }

    override fun submit(request: TunerRequest) {
        if (closed) return
        when (request) {
            TunerRequest.Start -> start()
            TunerRequest.Stop -> stop()
            is TunerRequest.SelectTarget -> if (request.target != snapshot.value.target) {
                snapshot.value = snapshot.value.copy(target = request.target)
                resetMeasurement()
            }
            is TunerRequest.SelectTolerance -> saveTolerance(request.tolerance)
            TunerRequest.ReloadTolerance -> reloadTolerance()
            is TunerRequest.OpenSettings -> {
                stop()
                openSettings(request.page).fold({ stop(it) }, {})
            }
        }
    }

    private fun start() {
        if (!eligible || authority != null || session != null || shutdownBlocked || !ownedScope.isActive) return
        val requested = StartAuthority(TunerStartId(++nextStartId), Phase.StoppingPlayback, clock())
        authority = requested
        resetMeasurement()
        snapshot.value = snapshot.value.copy(listening = TunerListening.Starting)
        preparation = ownedScope.launch(main) {
            metronome.execute(MetronomeCommand.Stop).fold(
                ifLeft = { if (accepts(requested.id)) stop(TunerFailure.MetronomeStopFailed(it)) },
                ifRight = {
                    if (!accepts(requested.id)) return@fold
                    if (!permissionGranted()) {
                        requested.phase = Phase.AwaitingPermission
                        permission.value = TunerPermissionRequest(requested.id)
                        val outcome = requested.permission.await()
                        if (!accepts(requested.id)) return@fold
                        permission.value = null
                        when (outcome) {
                            TunerPermissionOutcome.Granted -> Unit
                            is TunerPermissionOutcome.Denied -> { stop(TunerFailure.PermissionDenied(outcome.recovery)); return@fold }
                            is TunerPermissionOutcome.Failed -> { stop(outcome.failure); return@fold }
                        }
                    }
                    if (!accepts(requested.id)) return@fold
                    if (!permissionGranted()) { stop(TunerFailure.PermissionRevoked); return@fold }
                    requested.phase = Phase.OpeningInput
                    requested.phaseAt = clock()
                    val active = CaptureSession(requested.id, captureFactory.create())
                    session = active
                    ownCleanup(active)
                    active.capture.begin(ownedScope, MeasurementEpoch(revision, clock()))
                    active.capture.ready.await().fold(
                        ifLeft = { if (accepts(active.id)) stop(it) },
                        ifRight = {
                            if (accepts(active.id)) {
                                snapshot.value = snapshot.value.copy(listening = TunerListening.Listening(memory.feedback))
                                analyze(active)
                            }
                        },
                    )
                },
            )
        }
    }

    private suspend fun analyze(active: CaptureSession) {
        while (accepts(active.id)) {
            val observation = active.capture.next()
            if (observation == null) {
                if (accepts(active.id)) stop(active.capture.failure ?: TunerFailure.InputUnavailable)
                return
            }
            when (observation) {
                is CaptureObservation.Failed -> if (accepts(active.id)) stop(observation.failure)
                is CaptureObservation.Samples -> {
                    if (observation.epoch.revision != revision || snapshot.value.storage is ToleranceStorageStatus.Saving) continue
                    val evidence = withContext(analysis) { detector.analyze(observation.pcm, observation.sampleRateHz) }
                    if (!accepts(active.id) || observation.epoch.revision != revision || snapshot.value.storage is ToleranceStorageStatus.Saving) continue
                    memory = policy.reduce(memory, snapshot.value.target, snapshot.value.tolerance,
                        TuningInput.Observe(observation.capturedAt, clock(), evidence))
                    publishFeedback()
                }
            }
        }
    }

    private fun ownCleanup(active: CaptureSession) {
        ownedScope.launch(main, start = CoroutineStart.UNDISPATCHED) {
            try {
                active.stopRequested.await()
            } finally {
                withContext(NonCancellable) {
                    active.capture.requestStop()
                    var result = withTimeoutOrNull(1500) { active.capture.closed.await() }
                    if (result == null) {
                        snapshot.value = snapshot.value.copy(listening = TunerListening.Failed(TunerFailure.ShutdownFailed))
                        active.failure = TunerFailure.ShutdownFailed
                        result = active.capture.closed.await()
                    }
                    result.fold(
                        ifLeft = { shutdownBlocked = true; active.failure = TunerFailure.ShutdownFailed },
                        ifRight = {},
                    )
                    if (session === active) {
                        session = null
                        snapshot.value = snapshot.value.copy(listening = active.failure?.let(TunerListening::Failed) ?: TunerListening.Stopped)
                    }
                }
            }
        }
    }

    private fun accepts(id: TunerStartId): Boolean = !closed && eligible && authority?.id == id

    private fun stop(failure: TunerFailure? = null) {
        authority = null
        permission.value = null
        preparation?.cancel()
        preparation = null
        memory = TuningMemory()
        revision++
        val active = session
        val reportedFailure = if (shutdownBlocked || active?.failure == TunerFailure.ShutdownFailed) {
            TunerFailure.ShutdownFailed
        } else failure
        if (active != null) {
            active.failure = reportedFailure ?: active.failure
            active.capture.requestStop()
            active.stopRequested.complete(Unit)
        }
        snapshot.value = snapshot.value.copy(listening = when {
            reportedFailure != null -> TunerListening.Failed(reportedFailure)
            active != null -> TunerListening.Stopping
            else -> TunerListening.Stopped
        })
    }

    fun permissionResult(id: TunerStartId, outcome: TunerPermissionOutcome) {
        val requested = authority?.takeIf { accepts(id) && it.id == id && it.phase == Phase.AwaitingPermission } ?: return
        requested.permission.complete(outcome)
    }

    fun visibilityChanged(visibility: TunerVisibility) {
        when (visibility) {
            TunerVisibility.Foreground -> eligible = true
            TunerVisibility.ConfigurationContinuation -> Unit
            TunerVisibility.Background, TunerVisibility.Locked -> { eligible = false; stop() }
        }
    }

    private fun resetMeasurement() {
        revision++
        memory = TuningMemory()
        session?.capture?.revise(MeasurementEpoch(revision, clock()))
        publishFeedback()
    }

    private fun publishFeedback() {
        if (snapshot.value.listening is TunerListening.Listening) {
            snapshot.value = snapshot.value.copy(listening = TunerListening.Listening(memory.feedback))
        }
    }

    private fun saveTolerance(tolerance: TuningTolerance) {
        if (snapshot.value.storage is ToleranceStorageStatus.Saving || tolerance == snapshot.value.tolerance) return
        resetMeasurement()
        snapshot.value = snapshot.value.copy(storage = ToleranceStorageStatus.Saving(tolerance))
        ownedScope.launch(main) {
            withContext(NonCancellable) {
                withContext(io) { storage.write(tolerance) }.fold(
                    ifLeft = { snapshot.value = snapshot.value.copy(storage = ToleranceStorageStatus.Failed(it)) },
                    ifRight = {
                        snapshot.value = snapshot.value.copy(tolerance = tolerance, storage = ToleranceStorageStatus.Ready)
                        resetMeasurement()
                    },
                )
            }
        }
    }

    private fun reloadTolerance() {
        if (snapshot.value.storage is ToleranceStorageStatus.Saving) return
        resetMeasurement()
        snapshot.value = snapshot.value.copy(storage = ToleranceStorageStatus.Saving(snapshot.value.tolerance))
        ownedScope.launch(main) {
            withContext(NonCancellable) {
                withContext(io) { storage.read() }.fold(
                    ifLeft = { snapshot.value = snapshot.value.copy(storage = ToleranceStorageStatus.Failed(it)) },
                    ifRight = {
                        snapshot.value = snapshot.value.copy(tolerance = it, storage = ToleranceStorageStatus.Ready)
                        resetMeasurement()
                    },
                )
            }
        }
    }

    override fun close() {
        closed = true
        eligible = false
        stop()
        watchdog.cancel()
    }

    private enum class Phase { StoppingPlayback, AwaitingPermission, OpeningInput }
    private class StartAuthority(val id: TunerStartId, var phase: Phase, var phaseAt: MonotonicNanos,
        val permission: CompletableDeferred<TunerPermissionOutcome> = CompletableDeferred())
    private class CaptureSession(val id: TunerStartId, val capture: TunerCapture,
        val stopRequested: CompletableDeferred<Unit> = CompletableDeferred(), var failure: TunerFailure? = null)

    class Factory(private val application: Application, private val preferences: SharedPreferences, private val metronome: Metronome) {
        private val inputFactory = ExclusiveTunerInputFactory(AndroidTunerInputFactory(application))

        fun create(ownedScope: CoroutineScope): AndroidTunerHost {
            val clock = { MonotonicNanos(System.nanoTime()) }
            return AndroidTunerHost(ownedScope, metronome,
                TunerCaptureFactory { TunerCaptureWorker(inputFactory, clock) },
                TunerToleranceStorage(preferences),
                { application.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
                ::launchSettings, YinHarmonicDetector(), clock)
        }

        private fun launchSettings(page: TunerSettingsPage): Either<TunerFailure, Unit> = try {
            val intent = when (page) {
                TunerSettingsPage.AppPermission -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", application.packageName, null))
                TunerSettingsPage.MicrophonePrivacy -> Intent(Settings.ACTION_PRIVACY_SETTINGS)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            application.startActivity(intent)
            Unit.right()
        } catch (_: ActivityNotFoundException) {
            TunerFailure.SettingsUnavailable.left()
        } catch (_: SecurityException) {
            TunerFailure.SettingsUnavailable.left()
        }
    }
}
