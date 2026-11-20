package com.motodash.unit

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.motodash.telemetry.SystemTelemetryProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class SystemTelemetryProviderTest {

    private fun fixedClock(isoInstant: String): Clock =
        Clock.fixed(Instant.parse(isoInstant), ZoneId.of("UTC"))

    @Test
    fun `formats clock time deterministically in HH_mm even under non-US locale`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val app = ApplicationProvider.getApplicationContext<Application>()
            val provider = SystemTelemetryProvider(app, clock = fixedClock("2026-11-19T08:05:00Z"))
            assertEquals("08:05", provider.telemetryState.value.formattedTime)

            val midnightProvider = SystemTelemetryProvider(app, clock = fixedClock("2026-11-19T23:59:00Z"))
            assertEquals("23:59", midnightProvider.telemetryState.value.formattedTime)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `unconfigured battery defaults to null and double-dash instead of zero percent`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val provider = SystemTelemetryProvider(app, clock = fixedClock("2026-11-19T12:00:00Z"))

        assertNull(provider.telemetryState.value.batteryPercent)
        assertEquals("--%", provider.telemetryState.value.formattedBattery)
    }

    @Test
    fun `reads sticky ACTION_BATTERY_CHANGED intent including scaling zero percent and charging state`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val stickyIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 40)
            putExtra(BatteryManager.EXTRA_SCALE, 50)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
        }
        @Suppress("DEPRECATION")
        app.sendStickyBroadcast(stickyIntent)

        val provider = SystemTelemetryProvider(app, clock = fixedClock("2026-11-19T12:30:00Z"))
        assertEquals(80, provider.telemetryState.value.batteryPercent)
        assertEquals("80%", provider.telemetryState.value.formattedBattery)
        assertTrue(provider.telemetryState.value.isCharging)

        // Verify explicit 0% from ACTION_BATTERY_CHANGED is preserved and values > 100% are clamped
        val zeroIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 0)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        }
        @Suppress("DEPRECATION")
        app.sendStickyBroadcast(zeroIntent)
        provider.refresh()
        assertEquals(0, provider.telemetryState.value.batteryPercent)
        assertEquals("0%", provider.telemetryState.value.formattedBattery)
        assertFalse(provider.telemetryState.value.isCharging)

        val overHundredIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 120)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_FULL)
        }
        @Suppress("DEPRECATION")
        app.sendStickyBroadcast(overHundredIntent)
        provider.refresh()
        assertEquals(100, provider.telemetryState.value.batteryPercent)
        assertEquals("100%", provider.telemetryState.value.formattedBattery)
        assertTrue(provider.telemetryState.value.isCharging)
    }

    @Test
    fun `live broadcast updates StateFlow after start and stop is idempotent`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val provider = SystemTelemetryProvider(
            app,
            clock = fixedClock("2026-11-19T14:15:00Z"),
            timeTickIntervalMs = 5_000L
        )

        // Calling stop before start must not throw
        provider.stop()

        provider.start(externalScope = scope)
        // Duplicate start must be idempotent
        provider.start(externalScope = scope)

        val liveBattery = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 64)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        }
        app.sendBroadcast(liveBattery)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(64, provider.telemetryState.value.batteryPercent)
        assertEquals("64%", provider.telemetryState.value.formattedBattery)

        scope.advanceTimeBy(5_000L)
        scope.runCurrent()
        assertEquals("14:15", provider.telemetryState.value.formattedTime)

        provider.stop()
        provider.stop()
    }

    @Test
    fun `BatteryManager capacity fallback reads 1_to_100 when no sticky broadcast exists`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val batteryManager = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        shadowOf(batteryManager).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, 73)

        val provider = SystemTelemetryProvider(app, clock = fixedClock("2026-11-19T09:00:00Z"))
        assertEquals(73, provider.telemetryState.value.batteryPercent)
        assertEquals("73%", provider.telemetryState.value.formattedBattery)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero or negative timeTickIntervalMs is rejected`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        SystemTelemetryProvider(
            app,
            clock = fixedClock("2026-11-19T09:00:00Z"),
            timeTickIntervalMs = 0L
        )
    }
}
