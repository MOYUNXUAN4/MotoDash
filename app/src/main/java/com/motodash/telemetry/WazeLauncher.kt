package com.motodash.telemetry

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object WazeLauncher {
    const val WAZE_URI = "waze://"

    fun buildWazeIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(WAZE_URI))

    fun launchWaze(context: Context): Boolean {
        val intent = buildWazeIntent().apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
