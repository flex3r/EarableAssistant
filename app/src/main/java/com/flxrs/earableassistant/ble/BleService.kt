package com.flxrs.earableassistant.ble

import android.annotation.SuppressLint
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
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import com.flxrs.earableassistant.data.BluetoothLeRepository
import com.flxrs.earableassistant.data.MotionEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*

@FlowPreview
@Suppress("DEPRECATION")
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

    private val telecomManager: TelecomManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }

    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

            val state = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                else -> TelephonyManager.CALL_STATE_RINGING
            }

            if (lastCallState != state) {
                lastCallState = state
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> bluetoothGatt?.enableIMUData()
                    else -> bluetoothGatt?.disableIMUData()
                }
            }
        }
    }

    inner class LocalBinder(val service: BleService = this@BleService) : Binder()

    override fun onBind(intent: Intent?): IBinder? = binder
    override fun onUnbind(intent: Intent?): Boolean {
        closeGattConnection()
        return super.onUnbind(intent)
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        registerReceiver(earableCompassBroadcastReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        registerReceiver(callReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))

        scope.launch {
            repository.motionEvent.collect {
                if (telecomManager.isInCall) {
                    when (it) {
                        is MotionEvent.Nod -> {
                            Log.i(TAG, "Got MotionEvent.Nod, accepting call")
                            telecomManager.acceptRingingCall()
                            bluetoothGatt?.disableIMUData()
                        }
                        is MotionEvent.Shake -> {
                            Log.i(TAG, "Got MotionEvent.Shake, rejecting call")
                            telecomManager.endCall()
                            bluetoothGatt?.disableIMUData()
                        }
                    }
                }
            }
        }
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
            disableIMUData()
            characteristics.clear()

            disconnect()
            close()
            null
        }
    }

    private fun BluetoothDevice.connect(): BluetoothGatt = connectGatt(this@BleService, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
    private fun BluetoothGatt.enableIMUData() {
        Log.i(TAG, "Starting IMU data capture..")
        characteristics[IMU_CONFIG_CHARACTERISTIC_UUID]?.let {
            it.value = ENABLE_IMU_BYTES
            writeCharacteristic(it)
        }
    }

    private fun BluetoothGatt.disableIMUData() {
        Log.i(TAG, "Disabling IMU data capture..")
        characteristics[IMU_CONFIG_CHARACTERISTIC_UUID]?.let {
            it.value = DISABLE_IMU_BYTES
            writeCharacteristic(it)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (device.name?.startsWith("eSense") == true) {
                    bluetoothAdapter.bluetoothLeScanner.stopScan(this)
                    repository.setState(CombinedState(ConnectionState.Connecting(device.name), ScanState.STOPPED))
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
                    readCharacteristic(characteristics[ACC_OFFSET_UUID])

                    // add delay to make sure that device is not busy
                    delay(100)
                    setCharacteristicNotification(characteristics[IMU_DATA_CHARACTERISTIC_UUID], true)
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
            if (characteristic?.uuid == IMU_DATA_CHARACTERISTIC_UUID) {
                repository.updateGyroData(characteristic?.value)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (characteristic != null && characteristic.uuid == ACC_OFFSET_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                repository.setGyroOffset(characteristic.value)
            }
        }
    }

    companion object {
        private val TAG = BleService::class.java.simpleName

        private val DATA_SERVICE_UUID = UUID.fromString("0000ff06-0000-1000-8000-00805f9b34fb")
        private val IMU_CONFIG_CHARACTERISTIC_UUID = UUID.fromString("0000ff07-0000-1000-8000-00805f9b34fb")
        private val IMU_DATA_CHARACTERISTIC_UUID = UUID.fromString("0000ff08-0000-1000-8000-00805f9b34fb")
        private val ACC_OFFSET_UUID = UUID.fromString("0000ff0d-0000-1000-8000-00805f9b34fb")
        private val ENABLE_IMU_BYTES = byteArrayOf(0x53, 0x35, 0x02, 0x01, 0x32)
        private val DISABLE_IMU_BYTES = byteArrayOf(0x53, 0x02, 0x02, 0x00, 0x00)

        private var lastCallState = TelephonyManager.CALL_STATE_IDLE
    }
}