package com.motodash.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class SystemTelemetryState(
    val formattedTime: String = "--:--",
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false
) {
    val formattedBattery: String
        get() = batteryPercent?.let { "$it%" } ?: "--%"
}

class SystemTelemetryProvider(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val timeTickIntervalMs: Long = 10_000L
) {
    private val appContext = context.applicationContext ?: context
    private val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private val _telemetryState = MutableStateFlow(SystemTelemetryState(formattedTime = formatCurrentTime()))
    val telemetryState: StateFlow<SystemTelemetryState> = _telemetryState.asStateFlow()

    private val lifecycleLock = Any()
    private var internalScope: CoroutineScope? = null
    private var tickerJob: Job? = null

    @Volatile
    private var isStarted = false

    private val telemetryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> updateFromBatteryIntent(intent)
                Intent.ACTION_TIME_TICK,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED -> refreshTimeOnly()
            }
        }
    }

    init {
        require(timeTickIntervalMs > 0L) {
            "timeTickIntervalMs must be > 0, was $timeTickIntervalMs"
        }
        refresh()
    }

    fun start(externalScope: CoroutineScope? = null) {
        synchronized(lifecycleLock) {
            if (isStarted) return
            isStarted = true

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(
                appContext,
                telemetryReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            refresh()

            val scopeToUse = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
                internalScope = it
            }
            tickerJob = scopeToUse.launch {
                while (isActive) {
                    delay(timeTickIntervalMs)
                    refreshTimeOnly()
                }
            }
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            if (!isStarted) return
            isStarted = false
            tickerJob?.cancel()
            tickerJob = null
            internalScope?.cancel()
            internalScope = null
            try {
                appContext.unregisterReceiver(telemetryReceiver)
            } catch (_: IllegalArgumentException) {
                // Receiver already unregistered
            }
        }
    }

    fun refresh() {
        val stickyBattery = try {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: SecurityException) {
            null
        }
        if (stickyBattery != null) {
            updateFromBatteryIntent(stickyBattery)
        } else {
            val fallbackCap = readBatteryManagerCapacity()
            _telemetryState.update { current ->
                current.copy(
                    formattedTime = formatCurrentTime(),
                    batteryPercent = fallbackCap
                )
            }
        }
    }

    private fun refreshTimeOnly() {
        _telemetryState.update { current ->
            current.copy(formattedTime = formatCurrentTime())
        }
    }

    private fun updateFromBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val percent = if (level >= 0 && scale > 0) {
            ((level.toFloat() * 100f) / scale.toFloat()).roundToInt().coerceIn(0, 100)
        } else {
            readBatteryManagerCapacity()
        }

        _telemetryState.update {
            SystemTelemetryState(
                formattedTime = formatCurrentTime(),
                batteryPercent = percent,
                isCharging = isCharging
            )
        }
    }

    private fun readBatteryManagerCapacity(): Int? {
        val cap = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: return null
        return if (cap in 1..100) cap else null
    }

    private fun formatCurrentTime(): String =
        LocalTime.now(clock).format(TIME_FORMATTER)

    companion object {
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    }
}
