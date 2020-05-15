package com.flxrs.earableassistant.ui.main

import androidx.lifecycle.*
import com.flxrs.earableassistant.data.BluetoothLeRepository
import com.flxrs.earableassistant.data.GyroData
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class MainViewModel(private val repository: BluetoothLeRepository) : ViewModel() {

    val state = repository.state.asLiveData()
    val data: LiveData<GyroData> = repository.sensorData.asLiveData()
    val motionEvent = repository.motionEvent.asLiveData()

    fun resetEvent() = repository.resetMotionEvent()
}
