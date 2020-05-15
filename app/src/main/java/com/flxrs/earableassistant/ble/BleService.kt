package com.flxrs.earableassistant.ble

import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import com.flxrs.earableassistant.data.BluetoothLeRepository
import kotlinx.coroutines.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*

@ExperimentalCoroutinesApi
class BleService : Service(), KoinComponent {

    private val binder = LocalBinder()
    private val repository: BluetoothLeRepository by inject()
    private val scope: CoroutineScope by inject()
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val earableCompassBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    if (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0) == BluetoothDevice.BOND_BONDED) {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                        device.connect()
                    }
                }
                else -> return
            }
        }
    }
    private val characteristics = mutableMapOf<UUID, BluetoothGattCharacteristic>()

    inner class LocalBinder(val service: BleService = this@BleService) : Binder()

    override fun onBind(intent: Intent?): IBinder? = binder
    override fun onUnbind(intent: Intent?): Boolean {
        closeGattConnection()
        return super.onUnbind(intent)
    }

    override fun onCreate() {
        registerReceiver(earableCompassBroadcastReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    override fun onDestroy() {
        scope.cancel()
        closeGattConnection()
    }

    fun findESenseAndConnect() = scope.launch {
        bluetoothGatt?.disconnect()
        bluetoothAdapter.bluetoothLeScanner.startScan(scanCallback)
        repository.setScanState(ScanState.STARTED)
    }

    fun stopScan() {
        bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
        repository.setScanState(ScanState.STOPPED)
    }

    fun isBluetoothEnabled() = bluetoothAdapter.isEnabled

    fun closeGattConnection() {
        bluetoothGatt = bluetoothGatt?.run {
            characteristics[dataServiceUUID]?.let {
                it.value = disableData
                writeCharacteristic(it)
            }
            characteristics.clear()

            disconnect()
            close()
            null
        }
    }

    private fun BluetoothDevice.connect() {
        connectGatt(this@BleService, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (device.name?.startsWith("eSense") == true) {
                    bluetoothAdapter.bluetoothLeScanner.stopScan(this)
                    repository.setScanState(ScanState.STOPPED)
                    if (!device.createBond()) {
                        device.connect()
                    }
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            gatt?.apply {
                services.forEach { service ->
                    service.characteristics.forEach { characteristic ->
                        characteristics[characteristic.uuid] = characteristic
                    }
                }
                scope.launch {
                    characteristics[dataEnableCharacteristicUUID]?.let {
                        it.value = enableData
                        writeCharacteristic(it)
                    }

                    delay(500)
                    readCharacteristic(characteristics[accOffsetUUID])

                    delay(500)
                    setCharacteristicNotification(characteristics[dataCharacteristicUUID], true)
                }

            }
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            gatt ?: return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt.discoverServices()
                    bluetoothGatt = gatt
                    repository.setConnectionStatte(ConnectionState.Connected(gatt.device.name))
                }
                BluetoothProfile.STATE_CONNECTING -> repository.setConnectionStatte(ConnectionState.Connecting(gatt.device.name))
                else -> {
                    repository.setConnectionStatte(ConnectionState.Disconnected)
                    findESenseAndConnect()
                }
            }

        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic?.uuid == dataCharacteristicUUID) {
                repository.updateGyroData(characteristic?.value)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (characteristic != null && characteristic.uuid == accOffsetUUID && status == BluetoothGatt.GATT_SUCCESS) {
                repository.setGyroOffset(characteristic.value)
            }
        }
    }

    companion object {
        private val dataServiceUUID = UUID.fromString("0000ff06-0000-1000-8000-00805f9b34fb")
        private val dataEnableCharacteristicUUID = UUID.fromString("0000ff07-0000-1000-8000-00805f9b34fb")
        private val dataCharacteristicUUID = UUID.fromString("0000ff08-0000-1000-8000-00805f9b34fb")
        private val accOffsetUUID = UUID.fromString("0000ff0d-0000-1000-8000-00805f9b34fb")
        private val enableData = byteArrayOf(0x53, 0x35, 0x02, 0x01, 0x32)
        private val disableData = byteArrayOf(0x53, 0x02, 0x02, 0x00, 0x00)
    }
}