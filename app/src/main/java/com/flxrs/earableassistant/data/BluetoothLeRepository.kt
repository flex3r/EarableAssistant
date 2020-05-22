package com.flxrs.earableassistant.data

import com.flxrs.earableassistant.ble.CombinedState
import com.flxrs.earableassistant.ble.ConnectionState
import com.flxrs.earableassistant.ble.ScanState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


@FlowPreview
@ExperimentalCoroutinesApi
class BluetoothLeRepository(private val scope: CoroutineScope) {

    private var offset = Triple(0, 0, 0)
    private val lastEvents = mutableListOf<MotionEvent>()
    private val mutex = Mutex()
    private var eventJob: Job? = null
    private var resetJob: Job? = null

    private val _sensorData = MutableStateFlow(GyroData())
    private val _state = MutableStateFlow(CombinedState())

    val sensorData: StateFlow<GyroData> = _sensorData
    val state: StateFlow<CombinedState> = _state

    private val _motionEvent = ConflatedBroadcastChannel<MotionEvent>(MotionEvent.Unknown)
    val motionEvent: Flow<MotionEvent> = _motionEvent.asFlow()

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
            when (gyroData.event) {
                is MotionEvent.Unknown -> startResetJob()
                else -> {
                    eventJob?.cancel()
                    eventJob = scope.launch {
                        matchEvent(gyroData.event)
                    }
                }
            }

            _sensorData.value = gyroData
        }
    }

    fun setConnectionStatte(state: ConnectionState) {
        _state.value = _state.value.copy(connectionState = state)
    }

    fun setScanState(state: ScanState) {
        _state.value = _state.value.copy(scanState = state)
    }

    fun setState(state: CombinedState) {
        _state.value = state
    }

    private fun startResetJob() {
        if (eventJob != null && resetJob == null) {
            // eventJob running and unknown motion detected, start resetJob
            resetJob = scope.launch {
                delay(3000) // wait 3 seconds, then clear all events and cancel all jobs
                mutex.withLock {
                    lastEvents.clear()
                    eventJob?.cancel()
                    eventJob = null
                    resetJob = null
                }
            }
        }
    }

    private suspend fun matchEvent(event: MotionEvent) {
        mutex.withLock { lastEvents.add(event) }

        delay(1000)
        mutex.withLock {
            // make sure that all motion events are the same
            if (lastEvents.all { it == event } && lastEvents.size > event.threshold) {
                lastEvents.clear()
                eventJob?.cancel()
                eventJob = null
                _motionEvent.offer(event)
            }
        }
    }
}