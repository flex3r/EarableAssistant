package com.flxrs.earableassistant.ble

data class CombinedState(val connectionState: ConnectionState = ConnectionState.Disconnected, val scanState: ScanState = ScanState.STOPPED) {
    private val isScanning = scanState == ScanState.STARTED
    val isConnected = connectionState is ConnectionState.Connected
    val isScanningOrConnecting = (isScanning && !isConnected) || connectionState is ConnectionState.Connecting
}