package com.pekochan069.guitarlearner

import arrow.core.Either
import com.pekochan069.guitarlearner.domain.ProgressionCommand
import com.pekochan069.guitarlearner.domain.ProgressionFailure
import com.pekochan069.guitarlearner.domain.ProgressionWorkspace
import com.pekochan069.guitarlearner.domain.Progressions
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeProgressions : Progressions {
    override val current = MutableStateFlow(ProgressionWorkspace())
    override suspend fun execute(command: ProgressionCommand): Either<ProgressionFailure, Unit> = error("Unexpected progression command: $command")
}
