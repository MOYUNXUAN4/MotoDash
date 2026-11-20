package com.motodash

import android.app.Application
import android.os.Build
import org.maplibre.android.MapLibre

class MotoDashApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initMapLibre()
    }

    private fun initMapLibre() {
        try {
            MapLibre.getInstance(this)
        } catch (e: UnsatisfiedLinkError) {
            if (!isRobolectricEnvironment()) {
                throw e
            }
        }
    }

    private fun isRobolectricEnvironment(): Boolean {
        val fingerprint = Build.FINGERPRINT
        return fingerprint == null || fingerprint.equals("robolectric", ignoreCase = true)
    }
}
