package com.flxrs.earableassistant.ble

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
}