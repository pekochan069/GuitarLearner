package com.pekochan069.guitarlearner.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.MonotonicNanos
import com.pekochan069.guitarlearner.domain.TunerFailure
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class TunerCaptureWorkerTest {
    @Test fun successfulOpenStartsTheNoInputDeadlineAfterAcquisitionCompletes() = runTest {
        val time = AtomicLong(1_000_000_000)
        val entered = CountDownLatch(1)
        val finishOpen = CountDownLatch(1)
        val input = FakeInput(blockRead = true)
        val worker = TunerCaptureWorker(TunerInputFactory {
            entered.countDown()
            check(finishOpen.await(5, TimeUnit.SECONDS))
            input.right()
        }, { MonotonicNanos(time.get()) })
        worker.begin(backgroundScope, MeasurementEpoch(1, MonotonicNanos(time.get())))
        await(entered)
        time.set(2_200_000_000)
        finishOpen.countDown()
        assertEquals(Unit.right(), worker.ready.await())
        await(input.readEntered)
        assertEquals(MonotonicNanos(2_200_000_000), worker.lastReadProgress)
        assertNull(worker.failure)
        worker.requestStop()
        assertEquals(Unit.right(), worker.closed.await())
    }

    @Test fun cancellationWhileAcquiringInputStillInterruptsAndReleasesTheReturnedResourceOnce() = runTest {
        val entered = CountDownLatch(1)
        val returnInput = CountDownLatch(1)
        val input = FakeInput()
        val worker = TunerCaptureWorker(TunerInputFactory {
            entered.countDown()
            check(returnInput.await(5, TimeUnit.SECONDS))
            input.right()
        }, ::now)
        worker.begin(backgroundScope, MeasurementEpoch(1, now()))
        await(entered)
        backgroundScope.cancel()
        worker.requestStop()
        returnInput.countDown()
        assertEquals(Unit.right(), worker.closed.await())
        worker.requestStop()
        assertEquals(1, input.interrupts.get())
        assertEquals(1, input.releases.get())
        assertTrue(worker.ready.await().isLeft())
    }

    @Test fun independentStopInterruptsABlockedReadAfterOwnerScopeCancellation() = runTest {
        val input = FakeInput(blockRead = true)
        val worker = TunerCaptureWorker(TunerInputFactory { input.right() }, ::now)
        worker.begin(backgroundScope, MeasurementEpoch(1, now()))
        assertEquals(Unit.right(), worker.ready.await())
        await(input.readEntered)
        backgroundScope.cancel()
        worker.requestStop()
        assertEquals(Unit.right(), worker.closed.await())
        assertEquals(1, input.interrupts.get())
        assertEquals(1, input.releases.get())
    }

    @Test fun readFailureClearsInputAndAcknowledgesRelease() = runTest {
        val input = FakeInput(readFailure = TunerFailure.InputReadFailed)
        val worker = TunerCaptureWorker(TunerInputFactory { input.right() }, ::now)
        worker.begin(backgroundScope, MeasurementEpoch(1, now()))
        worker.ready.await().fold({ fail("Input did not start: $it") }, {})
        assertEquals(CaptureObservation.Failed(TunerFailure.InputReadFailed), worker.next())
        assertEquals(Unit.right(), worker.closed.await())
        assertEquals(1, input.releases.get())
    }

    @Test fun partialOpenShutdownFailureIsNotAcknowledgedAsSuccessfulClosure() = runTest {
        val worker = TunerCaptureWorker(TunerInputFactory { TunerFailure.ShutdownFailed.left() }, ::now)
        worker.begin(backgroundScope, MeasurementEpoch(1, now()))
        assertEquals(TunerFailure.ShutdownFailed.left(), worker.ready.await())
        assertEquals(TunerFailure.ShutdownFailed.left(), worker.closed.await())
    }

    @Test fun revisionChangeFlushesOldPcmInsteadOfRetaggingAnOverlappingWindow() = runTest {
        val input = WindowInput()
        val worker = TunerCaptureWorker(TunerInputFactory { input.right() }, input::now)
        worker.begin(backgroundScope, MeasurementEpoch(1, input.now()))
        worker.ready.await().fold({ fail("Input did not start: $it") }, {})
        await(input.firstPause)
        val previous = worker.next() as CaptureObservation.Samples
        assertEquals(1L, previous.epoch.revision)
        assertTrue(previous.pcm.all { it == 1000 / 32768f })
        input.marker.set(2000)
        worker.revise(MeasurementEpoch(2, input.now()))
        input.allowRevision.countDown()
        await(input.secondPause)
        val fresh = worker.next() as CaptureObservation.Samples
        assertEquals(2L, fresh.epoch.revision)
        assertTrue("A revision must discard every sample from the previous window", fresh.pcm.all { it == 2000 / 32768f })
        worker.requestStop()
        assertEquals(Unit.right(), worker.closed.await())
    }

    private suspend fun await(latch: CountDownLatch) {
        withContext(Dispatchers.IO) { assertTrue("Native worker did not reach the controlled boundary", latch.await(5, TimeUnit.SECONDS)) }
    }

    private class FakeInput(val blockRead: Boolean = false, val readFailure: TunerFailure? = null) : TunerInput {
        override val sampleRateHz = 48_000
        val readEntered = CountDownLatch(1)
        val releasedRead = CountDownLatch(1)
        val interrupts = AtomicInteger()
        val releases = AtomicInteger()
        override fun read(buffer: ShortArray): Either<TunerFailure, InputRead> {
            readEntered.countDown()
            if (blockRead) check(releasedRead.await(5, TimeUnit.SECONDS))
            return readFailure?.left() ?: InputRead(0, now()).right()
        }
        override fun health(): TunerFailure? = null
        override fun interrupt(): Either<TunerFailure, Unit> {
            interrupts.incrementAndGet()
            releasedRead.countDown()
            return Unit.right()
        }
        override fun release(): Either<TunerFailure, Unit> { releases.incrementAndGet(); return Unit.right() }
    }

    private class WindowInput : TunerInput {
        override val sampleRateHz = 48_000
        private val time = AtomicLong(1_000_000_000)
        private val reads = AtomicInteger()
        val marker = AtomicInteger(1000)
        val firstPause = CountDownLatch(1)
        val secondPause = CountDownLatch(1)
        val allowRevision = CountDownLatch(1)
        private val allowStop = CountDownLatch(1)
        fun now(): MonotonicNanos = MonotonicNanos(time.get())
        override fun read(buffer: ShortArray): Either<TunerFailure, InputRead> {
            when (reads.incrementAndGet()) {
                13 -> { firstPause.countDown(); check(allowRevision.await(5, TimeUnit.SECONDS)) }
                22 -> { secondPause.countDown(); check(allowStop.await(5, TimeUnit.SECONDS)) }
            }
            buffer.fill(marker.get().toShort())
            val captured = time.addAndGet(buffer.size * 1_000_000_000L / sampleRateHz)
            return InputRead(buffer.size, MonotonicNanos(captured)).right()
        }
        override fun health(): TunerFailure? = null
        override fun interrupt(): Either<TunerFailure, Unit> {
            allowRevision.countDown()
            allowStop.countDown()
            return Unit.right()
        }
        override fun release(): Either<TunerFailure, Unit> = Unit.right()
    }

    companion object { private fun now(): MonotonicNanos = MonotonicNanos(System.nanoTime()) }
}
