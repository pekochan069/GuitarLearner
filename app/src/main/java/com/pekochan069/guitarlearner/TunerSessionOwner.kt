package com.pekochan069.guitarlearner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pekochan069.guitarlearner.adapters.AndroidTunerHost
import com.pekochan069.guitarlearner.adapters.TunerStartId

internal class TunerSessionOwner(factory: AndroidTunerHost.Factory) : ViewModel() {
    val host: AndroidTunerHost = factory.create(viewModelScope)
    var launchedPermission: TunerStartId? = null

    override fun onCleared() {
        host.close()
    }

    class Factory(private val hostFactory: AndroidTunerHost.Factory) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(TunerSessionOwner(hostFactory))!!
    }
}
