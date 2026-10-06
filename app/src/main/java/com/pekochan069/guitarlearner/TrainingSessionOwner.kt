package com.pekochan069.guitarlearner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pekochan069.guitarlearner.adapters.AndroidTrainingHost

internal class TrainingSessionOwner(factory: AndroidTrainingHost.Factory) : ViewModel() {
    val host: AndroidTrainingHost = factory.create(viewModelScope)
    override fun onCleared() { host.close() }

    class Factory(private val hostFactory: AndroidTrainingHost.Factory) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(TrainingSessionOwner(hostFactory))!!
    }
}
