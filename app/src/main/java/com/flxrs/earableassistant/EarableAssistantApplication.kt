package com.flxrs.earableassistant

import android.app.Application
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

@ExperimentalCoroutinesApi
class EarableAssistantApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(applicationContext)

            modules(mainModule)
        }
    }

}
