package com.pekochan069.guitarlearner.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.MonotonicNanos
import com.pekochan069.guitarlearner.domain.TunerFailure
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class MeasurementEpoch(val revision: Long, val changedAt: MonotonicNanos)
internal data class InputRead(val count: Int, val capturedThrough: MonotonicNanos)

internal interface TunerInput {
    val sampleRateHz: Int
    fun read(buffer: ShortArray): Either<TunerFailure, InputRead>
    fun health(): TunerFailure?
    fun interrupt(): Either<TunerFailure, Unit>
    fun release(): Either<TunerFailure, Unit>
}

internal fun interface TunerInputFactory { fun open(): Either<TunerFailure, TunerInput> }
internal fun interface TunerCaptureFactory { fun create(): TunerCapture }

internal sealed interface CaptureObservation {
    data class Samples(val epoch: MeasurementEpoch, val capturedAt: MonotonicNanos, val pcm: FloatArray, val sampleRateHz: Int) : CaptureObservation
    data class Failed(val failure: TunerFailure) : CaptureObservation
}

internal interface TunerCapture {
    val ready: CompletableDeferred<Either<TunerFailure, Unit>>
    val closed: CompletableDeferred<Either<TunerFailure, Unit>>
    val lastReadProgress: MonotonicNanos
    val failure: TunerFailure?
    fun begin(scope: CoroutineScope, epoch: MeasurementEpoch)
    fun revise(epoch: MeasurementEpoch)
    suspend fun next(): CaptureObservation?
    fun requestStop()
}

internal class TunerCaptureWorker(
    private val inputFactory: TunerInputFactory,
    private val clock: () -> MonotonicNanos,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val control: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "TunerStop") },
) : TunerCapture {
    override val ready = CompletableDeferred<Either<TunerFailure, Unit>>()
    override val closed = CompletableDeferred<Either<TunerFailure, Unit>>()
    @Volatile override var lastReadProgress = clock()
        private set
    override val failure: TunerFailure? get() = inputFailure.get()
    private val inputFailure = AtomicReference<TunerFailure?>(null)
    private val stopping = AtomicBoolean(false)
    private val interrupting = AtomicBoolean(false)
    private val input = AtomicReference<TunerInput?>(null)
    private val epoch = AtomicReference(MeasurementEpoch(0, clock()))
    private val interrupted = CompletableDeferred<Either<TunerFailure, Unit>>()
    private val observations = Channel<CaptureObservation>(1, BufferOverflow.DROP_OLDEST)
    private var began = false

    override fun begin(scope: CoroutineScope, epoch: MeasurementEpoch) {
        check(!began)
        began = true
        this.epoch.set(epoch)
        val worker = scope.launch(io) {
            try {
                currentCoroutineContext().ensureActive()
                inputFactory.open().fold(
                    ifLeft = { report(it); ready.complete(it.left()) },
                    ifRight = { opened ->
                        input.set(opened)
                        if (stopping.get()) requestStop() else {
                            lastReadProgress = clock()
                            ready.complete(Unit.right())
                            consume(opened)
                        }
                    },
                )
            } finally {
                withContext(NonCancellable) {
                    val opened = input.get()
                    val result = if (opened == null) {
                        if (failure == TunerFailure.ShutdownFailed) TunerFailure.ShutdownFailed.left() else Unit.right()
                    } else {
                        requestStop()
                        val stopped = interrupted.await()
                        val released = withContext(io) { opened.release() }
                        released.fold({ it.left() }, { stopped })
                    }
                    observations.close()
                    ready.complete(TunerFailure.InputUnavailable.left())
                    closed.complete(result)
                    control.shutdown()
                }
            }
        }
        worker.invokeOnCompletion {
            if (input.get() == null && !closed.isCompleted) {
                ready.complete(TunerFailure.InputUnavailable.left())
                observations.close()
                closed.complete(Unit.right())
                control.shutdown()
            }
        }
    }

    private suspend fun consume(opened: TunerInput): Unit = coroutineScope {
        val monitor = launch(io) {
            while (isActive && !stopping.get()) {
                opened.health()?.let(::report)
                delay(100)
            }
        }
        try {
            val buffer = ShortArray(1024)
            val window = FloatArray(8192)
            var filled = 0
            var hop = 0
            var observedEpoch = epoch.get()
            while (isActive && !stopping.get() && failure == null) {
                val selected = epoch.get()
                if (selected != observedEpoch) {
                    filled = 0
                    hop = 0
                    observedEpoch = selected
                }
                val reading = opened.read(buffer)
                val read = reading.fold({ report(it); null }, { it }) ?: break
                if (read.count == 0) { delay(5); continue }
                lastReadProgress = clock()
                val count = read.count
                val retained = filled.coerceAtMost(window.size - count)
                if (filled + count > window.size) window.copyInto(window, 0, filled - retained, filled)
                for (index in 0 until count) window[retained + index] = buffer[index] / 32768f
                filled = retained + count
                hop += count
                val startsAt = read.capturedThrough.value - (window.size - 1) * 1_000_000_000L / opened.sampleRateHz
                if (filled == window.size && hop >= 960 && startsAt >= observedEpoch.changedAt.value) {
                    hop = 0
                    observations.trySend(CaptureObservation.Samples(observedEpoch, read.capturedThrough, window.copyOf(), opened.sampleRateHz))
                }
            }
        } finally {
            monitor.cancel()
        }
    }

    private fun report(failure: TunerFailure) {
        inputFailure.compareAndSet(null, failure)
        observations.trySend(CaptureObservation.Failed(failure))
    }

    override fun revise(epoch: MeasurementEpoch) { this.epoch.set(epoch) }
    override suspend fun next(): CaptureObservation? = observations.receiveCatching().getOrNull()

    override fun requestStop() {
        stopping.set(true)
        val opened = input.get() ?: return
        if (interrupting.compareAndSet(false, true)) control.execute { interrupted.complete(opened.interrupt()) }
    }
}
