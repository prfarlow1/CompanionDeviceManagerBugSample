package com.peterfarlow.companiondeviceservicesample

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.peterfarlow.companiondeviceservicesample.ui.theme.CompaniondeviceservicesampleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.coroutines.resume

const val TAG = "CDS"

class MainActivity : AppCompatActivity() {

    private val cdm = CdmStuff(this, activityResultRegistry).also {
        lifecycle.addObserver(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompaniondeviceservicesampleTheme {
                CdmTest {
                    cdm.invokeAssociationRequest()
                }
            }
        }
    }
}

@Composable
fun CdmTest(onDoCdm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = onDoCdm) {
            Text(
                text = "Click to start cdm",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black
            )
        }
    }
}

class CdmStuff(
    private val activity: AppCompatActivity,
    private val registry: ActivityResultRegistry
) : DefaultLifecycleObserver {

    private lateinit var connectHandler: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(owner: LifecycleOwner) {
        connectHandler = registry.register(
            "intentSender1",
            owner,
            ActivityResultContracts.StartIntentSenderForResult()
        ) { activityResult ->
            when (activityResult.resultCode) {
                Activity.RESULT_OK -> {
                    Log.d(TAG, "result okay")
                    val result = activityResult.data?.let {
                        IntentCompat.getParcelableExtra(it, CompanionDeviceManager.EXTRA_DEVICE, ScanResult::class.java)
                    } ?: return@register
                    handleConnectionWithDevice(result.device)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getCdm().startObservingDevicePresence(result.device.address)
                    } else {
                        throw IllegalStateException("Can't run this demo on phones that don't support startObservingDevicePresence")
                    }
                }

                Activity.RESULT_CANCELED -> {
                    Log.e(TAG, "result canceled")
                }
            }
        }
    }

    fun invokeAssociationRequest() {
        val deviceFilter =
            BluetoothLeDeviceFilter.Builder().setNamePattern(Pattern.compile("^WHOOP.*")).build()
        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .build()
        val deviceManager = getCdm()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            deviceManager.associate(pairingRequest, activity.mainExecutor, callback)
        } else {
            deviceManager.associate(pairingRequest, callbackOld, Handler(activity.mainLooper))
        }
    }

    private val callback = object : CompanionDeviceManager.Callback() {
        override fun onAssociationPending(intentSender: IntentSender) {
            connectHandler.launch(IntentSenderRequest.Builder(intentSender).build())
        }

        @RequiresApi(34)
        override fun onAssociationCreated(associationInfo: AssociationInfo) {
            val device = requireNotNull(associationInfo.associatedDevice?.bleDevice?.device)
            Log.d(TAG, "onAssociationCreated")
            handleConnectionWithDevice(device)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getCdm().startObservingDevicePresence(device.address)
            } else {
                throw IllegalStateException("Can't run this demo on phones that don't support startObservingDevicePresence")
            }
        }

        override fun onFailure(error: CharSequence?) {
            Log.d(TAG, "cdm failed $error")
        }

    }

    private val callbackOld = object : CompanionDeviceManager.Callback() {
        override fun onAssociationPending(intentSender: IntentSender) {
            Log.d(TAG, "onAssociationPending")
            connectHandler.launch(IntentSenderRequest.Builder(intentSender).build())
        }

        override fun onFailure(error: CharSequence?) {
            Log.d(TAG, "cdm failed $error")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getPermission() {
        val permissionStatus =
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            return
        }
        throw IllegalStateException("Please pre-grant this app bluetooth connect permission in system settings")
    }

    private fun handleConnectionWithDevice(bluetoothDevice: BluetoothDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            activity.lifecycleScope.launch {
                connect(bluetoothDevice)
            }
        } else {
            activity.lifecycleScope.launch {
                getPermission()
                connect(bluetoothDevice)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connect(bluetoothDevice: BluetoothDevice) {
        withContext(Dispatchers.Main) {
            Log.d(TAG, "connecting to device")
            suspendCancellableCoroutine { continuation ->
                bluetoothDevice.connectGatt(activity, true, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt?,
                        status: Int,
                        newState: Int
                    ) {
                        if (status == 0 && newState == 2) {
                            Log.d(TAG, "connected")
                            try {
                                continuation.resume(Unit)
                            } catch (e: Exception) {
                                Log.e(TAG, "error in coroutine", e)
                            }
                        } else {
                            Log.d(TAG, "not connected with newState $newState")
                            try {
                                continuation.resume(Unit)
                            } catch (e: Exception) {
                                Log.e(TAG, "error in coroutine", e)
                            }
                        }
                    }
                })
            }
            val bonded = bluetoothDevice.createBond()
            Log.d(TAG, "bonded=$bonded")
        }
    }

    private fun getCdm() = activity.getSystemService(CompanionDeviceManager::class.java)
        ?: throw IllegalStateException("no cdm")
}
