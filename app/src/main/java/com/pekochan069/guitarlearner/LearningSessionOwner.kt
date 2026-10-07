package com.pekochan069.guitarlearner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pekochan069.guitarlearner.adapters.AndroidLearningHost

internal class LearningSessionOwner(factory: AndroidLearningHost.Factory) : ViewModel() {
    val host: AndroidLearningHost = factory.create(viewModelScope)
    override fun onCleared() { host.close() }

    class Factory(private val hostFactory: AndroidLearningHost.Factory) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(LearningSessionOwner(hostFactory))!!
    }
}
