package com.pekochan069.guitarlearner.presentation.logic

import arrow.core.Either
import com.pekochan069.guitarlearner.domain.ProgressionCommand
import com.pekochan069.guitarlearner.domain.ProgressionFailure
import com.pekochan069.guitarlearner.domain.ProgressionWorkspace
import com.pekochan069.guitarlearner.domain.Progressions
import kotlinx.coroutines.flow.MutableStateFlow

internal class ControlledProgressions : Progressions {
    override val current = MutableStateFlow(ProgressionWorkspace())
    val commands = mutableListOf<ProgressionCommand>()
    var result: Either<ProgressionFailure, Unit> = Either.Right(Unit)
    var onCommand: (suspend (ProgressionCommand) -> Unit)? = null
    override suspend fun execute(command: ProgressionCommand): Either<ProgressionFailure, Unit> {
        commands += command
        onCommand?.invoke(command)
        return result
    }
}
