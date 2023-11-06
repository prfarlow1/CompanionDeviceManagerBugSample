package com.peterfarlow.companiondeviceservicesample

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class CompanionDeviceServiceSampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val startTime = Instant.now()
        val appLifecycle = ProcessLifecycleOwner.get()
        appLifecycle.lifecycleScope.launch {
            while (true) {
                val now = Instant.now()
                val aliveDuration = Duration.between(startTime, now)
                Log.d(TAG, "I'm alive (for ${aliveDuration.seconds} seconds)")
                delay(5.seconds)
            }
        }
    }
}
