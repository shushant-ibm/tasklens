package dev.shushant.tasklens.android.collectors

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import dev.shushant.tasklens.core.AppState
import dev.shushant.tasklens.core.EventSeverity
import dev.shushant.tasklens.core.EventSource
import dev.shushant.tasklens.core.EventType
import dev.shushant.tasklens.core.NetworkTransport
import dev.shushant.tasklens.core.TaskLensEvent
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicReference

interface AndroidCollector {
    fun start(sink: (TaskLensEvent) -> Unit)
    fun stop()
}

class NetworkMonitor(
    private val context: Context,
    private val scope: CoroutineScope
) : AndroidCollector {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val lastNetworkState = AtomicReference<String?>(null)

    // ACCESS_NETWORK_STATE is declared in the module's AndroidManifest.xml.
    // Lint cannot always resolve library manifests, so suppress the false-positive here.
    @SuppressLint("MissingPermission")
    override fun start(sink: (TaskLensEvent) -> Unit) {
        val cm = connectivityManager ?: return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                checkAndEmit(network, isConnected = true, sink = sink)
            }

            override fun onLost(network: Network) {
                checkAndEmit(network, isConnected = false, sink = sink)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                checkAndEmit(network, isConnected = true, caps = capabilities, sink = sink)
            }
        }
        networkCallback = callback

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)

            // Initial snapshot
            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                checkAndEmit(activeNetwork, isConnected = true, sink = sink)
            } else {
                sink(
                    TaskLensEvent(
                        type = EventType.NETWORK_CHANGED,
                        source = EventSource.SYSTEM_BROADCAST,
                        attributes = mapOf("connected" to "false", "transport" to "NONE")
                    )
                )
            }
        } catch (t: Throwable) {
            // Failure isolation
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkAndEmit(
        network: Network,
        isConnected: Boolean,
        caps: NetworkCapabilities? = null,
        sink: (TaskLensEvent) -> Unit
    ) {
        val cm = connectivityManager ?: return
        val capabilities = caps ?: cm.getNetworkCapabilities(network)

        val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false
        val isMetered = !(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ?: true)
        val transport = when {
            capabilities == null -> NetworkTransport.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransport.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkTransport.BLUETOOTH
            else -> NetworkTransport.OTHER
        }

        val stateKey = "$isConnected-$isValidated-$isMetered-$transport"
        if (lastNetworkState.getAndSet(stateKey) != stateKey) {
            sink(
                TaskLensEvent(
                    type = EventType.NETWORK_CHANGED,
                    source = EventSource.SYSTEM_BROADCAST,
                    severity = if (isConnected) EventSeverity.INFO else EventSeverity.WARNING,
                    attributes = mapOf(
                        "connected" to isConnected.toString(),
                        "validated" to isValidated.toString(),
                        "metered" to isMetered.toString(),
                        "transport" to transport.name
                    )
                )
            )
        }
    }

    override fun stop() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (t: Throwable) {}
        }
        networkCallback = null
    }
}

class BatteryMonitor(
    private val context: Context
) : AndroidCollector {

    private var receiver: BroadcastReceiver? = null
    private var lastLevel: Int = -1
    private var lastCharging: Boolean? = null

    override fun start(sink: (TaskLensEvent) -> Unit) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else level

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

                // Emit if charging state flipped or level changed by >= 2%
                if (lastCharging != isCharging || Math.abs(pct - lastLevel) >= 2) {
                    lastLevel = pct
                    lastCharging = isCharging
                    sink(
                        TaskLensEvent(
                            type = EventType.BATTERY_CHANGED,
                            source = EventSource.SYSTEM_BROADCAST,
                            attributes = mapOf(
                                "level" to pct.toString(),
                                "charging" to isCharging.toString()
                            )
                        )
                    )
                }
            }
        }
        receiver = r
        try {
            context.registerReceiver(r, filter)
        } catch (t: Throwable) {}
    }

    override fun stop() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (t: Throwable) {}
        }
        receiver = null
    }
}

class PowerManagerMonitor(
    private val context: Context
) : AndroidCollector {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var receiver: BroadcastReceiver? = null

    override fun start(sink: (TaskLensEvent) -> Unit) {
        val pm = powerManager ?: return

        // minSdk = 26, so M-level guards are unnecessary; call the APIs directly.
        fun emitPowerState() {
            val isPowerSave = pm.isPowerSaveMode
            val isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
            val isDeviceIdle = pm.isDeviceIdleMode

            sink(
                TaskLensEvent(
                    type = EventType.POWER_MODE_CHANGED,
                    source = EventSource.SYSTEM_BROADCAST,
                    attributes = mapOf(
                        "power_save" to isPowerSave.toString(),
                        "ignoring_optimizations" to isIgnoringBatteryOptimizations.toString(),
                        "device_idle" to isDeviceIdle.toString()
                    )
                )
            )
        }

        // Initial check
        emitPowerState()

        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }

        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                emitPowerState()
            }
        }
        receiver = r
        try {
            context.registerReceiver(r, filter)
        } catch (t: Throwable) {}
    }

    override fun stop() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (t: Throwable) {}
        }
        receiver = null
    }
}

class AppStateMonitor(
    private val application: Application
) : AndroidCollector {

    private var activityCount = 0
    private var isChangingConfig = false

    override fun start(sink: (TaskLensEvent) -> Unit) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                if (activityCount == 0 && !isChangingConfig) {
                    sink(
                        TaskLensEvent(
                            type = EventType.APP_STATE_CHANGED,
                            source = EventSource.APPLICATION,
                            attributes = mapOf("state" to AppState.FOREGROUND.name)
                        )
                    )
                }
                activityCount++
            }

            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                isChangingConfig = activity.isChangingConfigurations
                activityCount--
                if (activityCount == 0 && !isChangingConfig) {
                    sink(
                        TaskLensEvent(
                            type = EventType.APP_STATE_CHANGED,
                            source = EventSource.APPLICATION,
                            attributes = mapOf("state" to AppState.BACKGROUND.name)
                        )
                    )
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    override fun stop() = Unit
}
