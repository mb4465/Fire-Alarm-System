package com.example.firealarmsystem

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.concurrent.Executors

class MyBackgroundService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "MyBackgroundServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_CHANNEL_ID = "FireAlarmAlertChannel"
        const val ALERT_NOTIFICATION_ID = 2
        private const val TAG = "MyBackgroundService"
        private const val SHARED_PREFS_NAME = "FireAlarmPrefs"
        private const val KEY_MAC_ADDRESS = "macAddress"
    }

    private lateinit var database: com.google.firebase.database.DatabaseReference
    private var firebaseListener: ValueEventListener? = null
    private var macAddress: String? = null
    private var isFireDetected = false
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannels()
        database = Firebase.database.reference
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        // 1. FIRST start foreground service IMMEDIATELY
        startForegroundServiceNotification()

        // 2. THEN load data and start monitoring in background thread
        executor.execute {
            loadMacAddressAndStartMonitoring()
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build the persistent foreground service notification
        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Fire Alarm System Active")
            .setContentText("Starting monitoring...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }

            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )
            Log.d(TAG, "Foreground service started successfully.")

        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "Foreground Service Start Not Allowed: ${e.message}. Stopping service.", e)
                Toast.makeText(this, "Failed to start background monitoring", Toast.LENGTH_LONG).show()
                stopSelf()
            } else {
                Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            }
        }
    }

    private fun loadMacAddressAndStartMonitoring() {
        // Get MAC address from SharedPreferences
        val sharedPref = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        macAddress = sharedPref.getString(KEY_MAC_ADDRESS, null)

        if (macAddress.isNullOrEmpty()) {
            Log.e(TAG, "MAC address not found in SharedPreferences")
            stopSelf()
            return
        }

        // Update notification to show monitoring is active
        updateServiceNotification("Monitoring for fire alerts")

        // Start Firebase monitoring
        startFirebaseMonitoring()
    }

    private fun updateServiceNotification(message: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Fire Alarm System Active")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Update the existing notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startFirebaseMonitoring() {
        if (macAddress.isNullOrEmpty()) {
            Log.e(TAG, "Cannot start Firebase monitoring - MAC address is null")
            return
        }

        // Remove any existing listener
        firebaseListener?.let {
            database.child("/customers/$macAddress/Zones").removeEventListener(it)
        }

        firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    var fireDetected = false

                    for (zoneSnapshot in snapshot.children) {
                        val zoneStatus = zoneSnapshot.child("status").getValue(Int::class.java)
                        if (zoneStatus == 0) { // 0 = FIRE status
                            fireDetected = true
                            break
                        }
                    }

                    // Only trigger notification if fire is detected and it's a new event
                    if (fireDetected && !isFireDetected) {
                        sendFireAlarmNotification("FIRE ALERT!", "Immediate danger detected! Evacuate now!")
                    }

                    isFireDetected = fireDetected
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e(TAG, "Firebase monitoring error: ${error.message}")
                updateServiceNotification("Monitoring error - restart app")
            }
        }

        database.child("/customers/$macAddress/Zones").addValueEventListener(firebaseListener!!)
        Log.d(TAG, "Firebase monitoring started for customer: $macAddress")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Service notification channel
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that the fire alarm system is actively monitoring in the background."
            }

            // Fire alert channel
            val alertChannel = NotificationChannel(
                ALERT_NOTIFICATION_CHANNEL_ID,
                "Fire Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent notifications for fire alarm events."
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
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
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
        Log.d(TAG, "Fire alarm notification sent: $message")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        // Clean up Firebase listener
        firebaseListener?.let {
            database.child("/customers/$macAddress/Zones").removeEventListener(it)
        }
        executor.shutdownNow()
    }
}