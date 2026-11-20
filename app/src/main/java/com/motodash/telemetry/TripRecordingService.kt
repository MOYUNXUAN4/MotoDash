package com.motodash.telemetry

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.motodash.MainActivity

class TripRecordingService : Service() {
    private lateinit var session: TripSession
    private var foregroundReady = false

    override fun onCreate() {
        super.onCreate()
        session = TripSession.get(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Trip recording", NotificationManager.IMPORTANCE_LOW)
        )
        val contentIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = PendingIntent.getService(this, 1, Intent(this, TripRecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("MotoDash trip recording")
            .setContentText("Recording continues while using Waze. Tap to return.")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Finish trip", stopIntent)
            .build()
        try {
            ServiceCompat.startForeground(this, 1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            foregroundReady = true
        } catch (error: RuntimeException) {
            android.util.Log.w("TripService", "Unable to enter foreground", error)
            android.widget.Toast.makeText(this, "Unable to start trip recording. Check location permission and retry.", android.widget.Toast.LENGTH_LONG).show()
            session.serviceStopped()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundReady || intent?.action == ACTION_STOP || !session.locationTracker.hasLocationPermission()) {
            session.finish()
            stopSelf()
        } else session.beginFromService()
        // A killed process recovers its checkpoint as a finished trip; no invisible location restart.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        session.serviceStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "trip_recording"
        private const val ACTION_STOP = "com.motodash.STOP_TRIP"
    }
}
