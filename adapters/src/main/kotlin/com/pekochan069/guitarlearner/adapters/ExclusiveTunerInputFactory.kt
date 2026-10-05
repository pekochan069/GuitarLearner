package com.pekochan069.guitarlearner.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.pekochan069.guitarlearner.domain.TunerFailure
import java.util.concurrent.atomic.AtomicReference

internal class ExclusiveTunerInputFactory(private val source: TunerInputFactory) : TunerInputFactory {
    private val state = AtomicReference<State>(State.Available)

    override fun open(): Either<TunerFailure, TunerInput> {
        val lease = State.Leased()
        if (!state.compareAndSet(State.Available, lease)) {
            return (if (state.get() == State.Blocked) TunerFailure.ShutdownFailed else TunerFailure.InputUnavailable).left()
        }
        return source.open().fold(
            ifLeft = {
                state.compareAndSet(lease, if (it == TunerFailure.ShutdownFailed) State.Blocked else State.Available)
                it.left()
            },
            ifRight = { opened ->
                object : TunerInput by opened {
                    override fun interrupt(): Either<TunerFailure, Unit> = opened.interrupt().fold(
                        ifLeft = { state.compareAndSet(lease, State.Blocked); it.left() },
                        ifRight = { Unit.right() },
                    )
                    override fun release(): Either<TunerFailure, Unit> = opened.release().fold(
                        ifLeft = { state.compareAndSet(lease, State.Blocked); it.left() },
                        ifRight = { state.compareAndSet(lease, State.Available); Unit.right() },
                    )
                }.right()
            },
        )
    }

    private sealed interface State {
        data object Available : State
        class Leased : State
        data object Blocked : State
    }
}
