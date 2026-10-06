package com.pekochan069.guitarlearner

import com.pekochan069.guitarlearner.domain.Learning
import com.pekochan069.guitarlearner.domain.LearningRequest
import com.pekochan069.guitarlearner.domain.LearningSnapshot
import com.pekochan069.guitarlearner.domain.LearningTarget
import kotlinx.coroutines.flow.MutableStateFlow

internal class LearningTestPort : Learning {
    override val current = MutableStateFlow(LearningSnapshot())
    override fun submit(request: LearningRequest) {
        when (request) {
            is LearningRequest.Activate -> (request.target as? LearningTarget.Lesson)?.id?.let {
                current.value = current.value.copy(progress = current.value.progress.copy(lastViewed = it))
            }
            is LearningRequest.Complete -> current.value = current.value.copy(progress = current.value.progress.copy(completed = current.value.progress.completed + request.lesson))
            else -> Unit
        }
    }
}
