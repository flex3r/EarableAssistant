package com.flxrs.earablecompass.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.invoke
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.flxrs.earablecompass.R
import com.flxrs.earablecompass.ble.BleService
import com.flxrs.earablecompass.ble.ConnectionState
import com.flxrs.earablecompass.ble.EnableBluetoothContract
import com.flxrs.earablecompass.data.MotionEvent
import com.flxrs.earablecompass.databinding.MainFragmentBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel

@ExperimentalCoroutinesApi
class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val viewModel: MainViewModel by viewModel()
    private lateinit var binding: MainFragmentBinding
    private var bleService: BleService? = null
//    private val gyroSeriesX = LineGraphSeries<DataPoint>().apply { color = Color.BLUE }
//    private val gyroSeriesY = LineGraphSeries<DataPoint>().apply { color = Color.GREEN }
//    private val gyroSeriesZ = LineGraphSeries<DataPoint>().apply { color = Color.RED }
//    private var currentX = 0.0

    private val enableBluetoothRegistration = registerForActivityResult(EnableBluetoothContract()) { result ->
        when {
            result -> bleService?.findESenseAndConnect()
            else -> Snackbar.make(binding.root, R.string.bluetooth_disclaimer, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.enable) { enableBluetoothIfDisabled() }
                .show()
        }
    }
    private val requestPermissionsRegistration = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        when {
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
//            with(graph.viewport) {
//                isScalable = true
//                isScrollable = true
//            }
//            graph.addSeries(gyroSeriesX)
//            graph.addSeries(gyroSeriesY)
//            graph.addSeries(gyroSeriesZ)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermissions()
        viewModel.apply {
            data.observe(viewLifecycleOwner) {
//                gyroSeriesX.appendData(DataPoint(currentX, it.x), true, 500)
//                gyroSeriesY.appendData(DataPoint(currentX, it.y), true, 500)
//                gyroSeriesZ.appendData(DataPoint(currentX, it.z), true, 500)
//                currentX++
            }
            motionEvent.observe(viewLifecycleOwner) {
                when (it) {
                    is MotionEvent.Nod, MotionEvent.Shake -> {
                        viewModel.resetEvent()
                        Snackbar.make(binding.root, "${it.msg} detected", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            connectionStatus.observe(viewLifecycleOwner) {
                binding.connectionStatus.text = when (it) {
                    is ConnectionState.Disconnected -> getString(R.string.searching_device)
                    is ConnectionState.Connecting -> getString(R.string.found_device, it.deviceName)
                    is ConnectionState.Connected -> getString(R.string.connected_to_device, it.deviceName)
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
            else -> enableBluetoothRegistration()
        }
    }

    private fun requestPermissions() {
        requestPermissionsRegistration(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.BLUETOOTH_ADMIN, android.Manifest.permission.READ_PHONE_STATE))
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
