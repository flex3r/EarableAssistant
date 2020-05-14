package com.flxrs.earablecompass.data

import android.util.Log
import com.flxrs.earablecompass.ble.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


@ExperimentalCoroutinesApi
class BluetoothLeRepository(private val scope: CoroutineScope) {

    private var offset = Triple(0, 0, 0)
    private val lastEvents = mutableListOf<MotionEvent>()
    private var eventJob: Job? = null
    private var resetJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    private val _sensorData = MutableStateFlow(GyroData())
    val sensorData: StateFlow<GyroData> = _sensorData

    private val _motionEvent = MutableStateFlow<MotionEvent>(MotionEvent.Unknown)
    val motionEvent: StateFlow<MotionEvent> = _motionEvent

    fun setGyroOffset(byteArray: ByteArray?) {
        byteArray?.let { bytes ->
            // factory offset is in +-16g, 1g = 2048
            val offsetX = bytes[9].toInt() shl 8 or (bytes[10].toInt() and 0xff)
            val offsetY = bytes[11].toInt() shl 8 or (bytes[12].toInt() and 0xff)
            val offsetZ = bytes[13].toInt() shl 8 or (bytes[14].toInt() and 0xff)
            offset = Triple(offsetX, offsetY, offsetZ)
        }
    }

    fun updateGyroData(byteArray: ByteArray?) {
        byteArray?.let { bytes ->
            val gyroData = GyroData.fromIMUBytes(bytes)
            val accData = AccelerationData.fromIMUBytes(bytes, offset)
            when(gyroData.event) {
                is MotionEvent.Unknown -> startResetJob()
                else -> matchEvent(gyroData.event)
            }

            _sensorData.value = gyroData
        }
    }

    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun resetEvent() {
        _motionEvent.value = MotionEvent.Unknown
    }

    private fun startResetJob() {
        if (eventJob != null && resetJob == null) {
            // eventJob running and unknown motion detected, start resetJob
            resetJob = scope.launch {
                delay(3000) // wait 3 seconds, then clear all events and cancel all jobs

                lastEvents.clear()
                eventJob?.cancel()
                eventJob = null
                resetJob = null
            }
        }
    }

    private fun matchEvent(event: MotionEvent) {
        lastEvents.add(event)
        eventJob?.cancel() // stop previous event job

        eventJob = scope.launch {
            delay(1000)

            // make sure that all motion events are the same
            if (lastEvents.all { it == event } && lastEvents.size > event.threshold) {
                lastEvents.clear()
                eventJob?.cancel()
                eventJob = null

                _motionEvent.value = event
            }
        }
    }
}