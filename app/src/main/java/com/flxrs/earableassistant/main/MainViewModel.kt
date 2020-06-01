package com.flxrs.earableassistant.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.flxrs.earableassistant.data.BluetoothLeRepository

class MainViewModel(repository: BluetoothLeRepository) : ViewModel() {

    val state = repository.state.asLiveData()
    val data = repository.sensorData.asLiveData()
    val motionEvent = repository.motionEvent.asLiveData()
}
