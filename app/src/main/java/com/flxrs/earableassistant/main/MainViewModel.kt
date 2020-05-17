package com.flxrs.earableassistant.main

import androidx.lifecycle.*
import com.flxrs.earableassistant.data.BluetoothLeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class MainViewModel(private val repository: BluetoothLeRepository) : ViewModel() {

    val state = repository.state.asLiveData()
    val data = repository.sensorData.asLiveData()
    val motionEvent = repository.motionEvent.asLiveData()

    fun resetEvent() = repository.resetMotionEvent()
}
