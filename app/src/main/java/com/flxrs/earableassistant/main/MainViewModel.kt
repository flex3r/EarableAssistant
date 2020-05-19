package com.flxrs.earableassistant.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.flxrs.earableassistant.data.BluetoothLeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@FlowPreview
@ExperimentalCoroutinesApi
class MainViewModel(private val repository: BluetoothLeRepository) : ViewModel() {

    val state = repository.state.asLiveData()
    val data = repository.sensorData.asLiveData()
    val motionEvent = repository.motionEvent.asLiveData()
}
