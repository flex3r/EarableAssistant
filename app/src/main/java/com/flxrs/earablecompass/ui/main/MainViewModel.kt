package com.flxrs.earablecompass.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.flxrs.earablecompass.ble.ConnectionState
import com.flxrs.earablecompass.data.BluetoothLeRepository
import com.flxrs.earablecompass.data.GyroData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map

@ExperimentalCoroutinesApi
class MainViewModel(private val repository: BluetoothLeRepository) : ViewModel() {

    val isConnected = repository.connectionState.map { it is ConnectionState.Connected }.asLiveData()
    val connectionStatus = repository.connectionState.asLiveData()
    val data: LiveData<GyroData> = repository.sensorData.asLiveData()
    val motionEvent = repository.motionEvent.asLiveData()

    fun resetEvent() = repository.resetEvent()
}
