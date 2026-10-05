package com.pekochan069.guitarlearner.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.MonotonicNanos
import com.pekochan069.guitarlearner.domain.TunerFailure
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusiveTunerInputFactoryTest {
    @Test fun aSuccessorCannotOpenWhileReleaseIsBlockedAndAnOldAcknowledgmentCannotFreeItsLease() = runTest {
        val releases = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val opens = AtomicInteger()
        val factory = ExclusiveTunerInputFactory(TunerInputFactory {
            val number = opens.incrementAndGet()
            FakeInput {
                if (number == 1) { releases.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }
                Unit.right()
            }.right()
        })
        val first = requireNotNull(factory.open().getOrNull())
        val releasing = async(Dispatchers.IO) { first.release() }
        withContext(Dispatchers.IO) { assertTrue(releases.await(5, TimeUnit.SECONDS)) }
        assertEquals(TunerFailure.InputUnavailable.left(), factory.open())
        assertEquals(1, opens.get())
        finish.countDown()
        assertEquals(Unit.right(), releasing.await())
        val successor = requireNotNull(factory.open().getOrNull())
        assertEquals(Unit.right(), first.release())
        assertEquals(TunerFailure.InputUnavailable.left(), factory.open())
        assertEquals(2, opens.get())
        assertEquals(Unit.right(), successor.release())
        val next = requireNotNull(factory.open().getOrNull())
        assertEquals(3, opens.get())
        assertEquals(Unit.right(), next.release())
    }

    @Test fun aFailedReleaseBlocksEveryNewOwner() {
        val input = FakeInput { TunerFailure.ShutdownFailed.left() }
        val factory = ExclusiveTunerInputFactory(TunerInputFactory { input.right() })
        val opened = requireNotNull(factory.open().getOrNull())
        assertEquals(TunerFailure.ShutdownFailed.left(), opened.release())
        assertEquals(TunerFailure.ShutdownFailed.left(), factory.open())
    }

    @Test fun aFailedInterruptRemainsBlockedEvenWhenReleaseReturnsSuccessfully() {
        val input = FakeInput(interruptFailure = TunerFailure.ShutdownFailed) { Unit.right() }
        val factory = ExclusiveTunerInputFactory(TunerInputFactory { input.right() })
        val opened = requireNotNull(factory.open().getOrNull())
        assertEquals(TunerFailure.ShutdownFailed.left(), opened.interrupt())
        assertEquals(Unit.right(), opened.release())
        assertEquals(TunerFailure.ShutdownFailed.left(), factory.open())
    }

    @Test fun unacknowledgedPartialAcquisitionCleanupAlsoBlocksEveryNewOwner() {
        var attempts = 0
        val factory = ExclusiveTunerInputFactory(TunerInputFactory {
            attempts++
            TunerFailure.ShutdownFailed.left()
        })
        assertEquals(TunerFailure.ShutdownFailed.left(), factory.open())
        assertEquals(TunerFailure.ShutdownFailed.left(), factory.open())
        assertEquals(1, attempts)
    }

    @Test fun acknowledgedOpenFailureAllowsAnotherExplicitAttempt() {
        var attempts = 0
        val factory = ExclusiveTunerInputFactory(TunerInputFactory {
            if (++attempts == 1) TunerFailure.InputUnavailable.left() else FakeInput { Unit.right() }.right()
        })
        assertEquals(TunerFailure.InputUnavailable.left(), factory.open())
        val opened = requireNotNull(factory.open().getOrNull())
        assertEquals(2, attempts)
        assertEquals(Unit.right(), opened.release())
    }

    private class FakeInput(private val interruptFailure: TunerFailure? = null,
        private val closing: () -> Either<TunerFailure, Unit>) : TunerInput {
        override val sampleRateHz = 48_000
        override fun read(buffer: ShortArray): Either<TunerFailure, InputRead> = InputRead(0, MonotonicNanos(0)).right()
        override fun health(): TunerFailure? = null
        override fun interrupt(): Either<TunerFailure, Unit> = interruptFailure?.left() ?: Unit.right()
        override fun release(): Either<TunerFailure, Unit> = closing()
    }
}
