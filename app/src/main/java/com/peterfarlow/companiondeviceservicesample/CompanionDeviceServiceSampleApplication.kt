package com.peterfarlow.companiondeviceservicesample

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class CompanionDeviceServiceSampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val appLifecycle = ProcessLifecycleOwner.get()
        appLifecycle.lifecycleScope.launch {
            while (true) {
                Log.d(TAG, "I'm alive")
                delay(5.seconds)
            }
        }
    }
}
