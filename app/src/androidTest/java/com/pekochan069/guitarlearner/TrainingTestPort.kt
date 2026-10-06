package com.pekochan069.guitarlearner

import com.pekochan069.guitarlearner.domain.Training
import com.pekochan069.guitarlearner.domain.TrainingRequest
import com.pekochan069.guitarlearner.domain.TrainingSnapshot
import kotlinx.coroutines.flow.MutableStateFlow

internal class TrainingTestPort : Training {
    override val current = MutableStateFlow(TrainingSnapshot())
    override fun submit(request: TrainingRequest) = Unit
}
