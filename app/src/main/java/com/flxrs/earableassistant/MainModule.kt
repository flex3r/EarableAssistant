package com.flxrs.earableassistant

import com.flxrs.earableassistant.data.BluetoothLeRepository
import com.flxrs.earableassistant.main.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

@ExperimentalCoroutinesApi
val mainModule = module {
    factory { CoroutineScope(Dispatchers.IO + Job()) }
    single { BluetoothLeRepository(get()) }
    viewModel { MainViewModel(get()) }
}