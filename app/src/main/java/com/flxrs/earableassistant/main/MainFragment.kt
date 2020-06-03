package com.flxrs.earableassistant.main

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.flxrs.earableassistant.R
import com.flxrs.earableassistant.ble.BleService
import com.flxrs.earableassistant.ble.ConnectionState
import com.flxrs.earableassistant.ble.ScanState
import com.flxrs.earableassistant.data.MotionEvent
import com.flxrs.earableassistant.databinding.MainFragmentBinding
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val viewModel: MainViewModel by viewModel()
    private lateinit var binding: MainFragmentBinding
    private var bleService: BleService? = null

    private val enableBluetoothRegistration = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> bleService?.findESenseAndConnect()
            else -> Snackbar.make(binding.root, R.string.bluetooth_disclaimer, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.enable) { enableBluetoothIfDisabled() }
                .show()
        }
    }
    private val requestPermissionsRegistration = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        when {
            // all permissions granted, start/bind service
            map.all { it.value } -> requireActivity().bindService(Intent(requireContext(), BleService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
            else -> Snackbar.make(binding.root, R.string.permissions_disclaimer, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.accept) { requestPermissions() }
                .show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = MainFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            vm = viewModel
            toggleScanButton.setOnClickListener {
                val state = viewModel.state.value ?: return@setOnClickListener
                when (state.scanState) {
                    ScanState.STARTED -> bleService?.stopScan()
                    ScanState.STOPPED -> when (state.connectionState) {
                        is ConnectionState.Disconnected -> enableBluetoothIfDisabled()
                        else -> bleService?.disconnect()
                    }
                }
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermissions()
        viewModel.apply {
            motionEvent.observe(viewLifecycleOwner) {
                when (it) {
                    is MotionEvent.Nod, is MotionEvent.Shake -> {
                        Snackbar.make(binding.root, getString(R.string.event_detected, it.msg), Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            state.observe(viewLifecycleOwner) {
                binding.connectionStatus.text = when {
                    it.connectionState is ConnectionState.Connected -> getString(R.string.connected_to_device, it.connectionState.deviceName)
                    it.connectionState is ConnectionState.Connecting -> getString(R.string.found_device, it.connectionState.deviceName)
                    it.connectionState is ConnectionState.Disconnected && it.scanState == ScanState.STOPPED -> getString(R.string.scan_to_get_started)
                    else -> getString(R.string.searching_device)
                }
                binding.toggleScanButton.text = when (it.scanState) {
                    ScanState.STARTED -> getString(R.string.stop_scan)
                    ScanState.STOPPED -> when (it.connectionState) {
                        is ConnectionState.Disconnected -> getString(R.string.start_scan)
                        else -> getString(R.string.disconnect)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        bleService?.closeGattConnection()
        requireActivity().unbindService(serviceConnection)
        super.onDestroy()
    }

    private fun enableBluetoothIfDisabled() {
        when (bleService?.isBluetoothEnabled()) {
            true -> bleService?.findESenseAndConnect()
            else -> enableBluetoothRegistration.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun requestPermissions() {
        requestPermissionsRegistration.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.BLUETOOTH_ADMIN,
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.ANSWER_PHONE_CALLS
            )
        )
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bleService = (service as BleService.LocalBinder).service
            enableBluetoothIfDisabled()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
        }
    }
}
