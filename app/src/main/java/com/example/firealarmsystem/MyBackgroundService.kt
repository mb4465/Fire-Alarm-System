package com.example.firealarmsystem

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat // Import ServiceCompat

class MyBackgroundService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "MyBackgroundServiceChannel"
        const val NOTIFICATION_ID = 1 // ID for the persistent FGS notification
        const val ALERT_NOTIFICATION_CHANNEL_ID = "FireAlarmAlertChannel"
        const val ALERT_NOTIFICATION_ID = 2 // ID for actual alert notifications
        private const val TAG = "MyBackgroundService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannels() // Create both channels here
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build the persistent foreground service notification
        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Fantom App is Active")
            .setContentText("Monitoring for alerts in the background.")
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your app's icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification non-dismissable
            .setPriority(NotificationCompat.PRIORITY_LOW) // Good practice for persistent FGS notification
            .build()

        // Promote the service to foreground
        try {
            // For API 29 (Q) and above, foregroundServiceType is relevant.
            // For API 34 (U) and above, it's explicitly required in startForeground().
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0 // For Q to T, this parameter is optional/ignored by older startForeground.
                    // ServiceCompat handles it gracefully by choosing the right underlying method.
                }

                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    foregroundServiceType
                )
            } else {
                // For older APIs, just use the standard startForeground method
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Foreground service started successfully.")

        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                // This exception occurs on Android 12+ if you try to start FGS from background
                Log.e(TAG, "Foreground Service Start Not Allowed: ${e.message}. Stopping service.", e)
                Toast.makeText(this, "Failed to start background monitoring: Permission denied or app in background.", Toast.LENGTH_LONG).show()
                stopSelf() // Stop the service as it cannot run as foreground
            } else {
                Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            }
        }

        // --- Your actual monitoring logic would go here ---
        // For demonstration, we'll simulate a fire alarm after 10 seconds.
        // In a real app, this would be your Firebase listener, sensor readings, etc.
        Thread {
            try {
                Log.d(TAG, "Simulating background monitoring...")
                Thread.sleep(10000) // Wait 10 seconds
                sendFireAlarmNotification("FIRE ALERT!", "Immediate danger detected! Evacuate now!")
                Log.d(TAG, "Simulated fire alarm notification sent.")
            } catch (e: InterruptedException) {
                Log.e(TAG, "Monitoring thread interrupted.", e)
            }
        }.start()

        // If your service is killed, this tells the system to restart it
        // and redeliver the last intent.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for the persistent foreground service notification
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Fantom Background Service",
                NotificationManager.IMPORTANCE_LOW // Use LOW for the persistent background indicator
            ).apply {
                description = "Shows that Fantom is actively monitoring in the background."
            }

            // Channel for actual fire alarm alerts
            val alertChannel = NotificationChannel(
                ALERT_NOTIFICATION_CHANNEL_ID,
                "Fantom Fire Alarm Alerts",
                NotificationManager.IMPORTANCE_HIGH // Use HIGH for urgent alerts (sound, vibrate, heads-up)
            ).apply {
                description = "Urgent notifications for fire alarm events."
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(1000, 1000, 1000, 1000) // Example vibration pattern
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
            Log.d(TAG, "Notification channels created.")
        }
    }

    private fun sendFireAlarmNotification(title: String, message: String) {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Notification disappears when user taps it
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Match channel priority
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
        Log.d(TAG, "Fire alarm notification sent with ID: $ALERT_NOTIFICATION_ID")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        // Clean up resources, stop any ongoing monitoring threads
    }
}