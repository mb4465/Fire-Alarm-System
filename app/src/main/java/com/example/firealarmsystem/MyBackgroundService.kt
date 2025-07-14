package com.example.firealarmsystem

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
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
        const val SERVICE_CHANNEL_ID = "BackgroundServiceChannel"
        const val ALERT_CHANNEL_ID = "FireAlarmAlertChannel"
        const val SERVICE_NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002
        private const val TAG = "MyBackgroundService"

        // Use same preferences as LoginActivity
        const val PREF_NAME = "login_pref"
        const val KEY_MAC_ADDRESS = "macAddress"
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

        // 1. First try to get MAC from intent extras
        macAddress = intent?.getStringExtra("MAC_ADDRESS")

        // 2. If not in intent, try SharedPreferences
        if (macAddress.isNullOrEmpty()) {
            val sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            macAddress = sharedPref.getString(KEY_MAC_ADDRESS, null)
            Log.d(TAG, "Loaded MAC from SharedPreferences: $macAddress")
        }

        if (macAddress.isNullOrEmpty()) {
            Log.e(TAG, "MAC address not found in intent or SharedPreferences")
            // Don't stop - keep service running to retry later
            return START_STICKY
        }

        startForegroundServiceNotification()
        executor.execute { startFirebaseMonitoring() }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notification = buildServiceNotification("Monitoring for fire alerts...")
        try {
            val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                SERVICE_NOTIFICATION_ID,
                notification,
                foregroundType
            )
            Log.d(TAG, "Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "Foreground start failed: ${e.message}")
            stopSelf()
        }
    }

    private fun buildServiceNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("Fire Alarm System")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun startFirebaseMonitoring() {
        val mac = macAddress ?: return

        Log.d(TAG, "Starting Firebase monitoring for MAC: $mac")

        // Remove any existing listener
        firebaseListener?.let {
            database.child("/customers/$mac/Zones").removeEventListener(it)
        }

        firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var fireDetected = false
                var fireZoneId = 0

                snapshot.children.forEach { zone ->
                    val zoneId = zone.child("id").getValue(Int::class.java) ?: 0
                    val status = zone.child("status").getValue(Int::class.java) ?: -1

                    Log.d(TAG, "Zone $zoneId status: $status")

                    if (status == 0) { // 0 = FIRE status
                        fireDetected = true
                        fireZoneId = zoneId
                        Log.d(TAG, "FIRE DETECTED in Zone $zoneId")
                    }
                }

                when {
                    fireDetected && !isFireDetected -> {
                        sendFireAlarmNotification(
                            "FIRE ALERT!",
                            "Fire detected in Zone $fireZoneId! Evacuate now!"
                        )
                        isFireDetected = true
                    }
                    !fireDetected && isFireDetected -> {
                        cancelFireAlarmNotification()
                        isFireDetected = false
                        Log.d(TAG, "Fire status cleared")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase error: ${error.message}")
                // Don't stop service - keep trying to reconnect
            }
        }

        // Use keepSynced to ensure data stays updated
        database.child("/customers/$mac").keepSynced(true)
        database.child("/customers/$mac/Zones").addValueEventListener(firebaseListener!!)
        Log.d(TAG, "Firebase listener attached")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Service status channel
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Service Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service status notifications"
                enableVibration(false)
                setSound(null, null)
            }.also { channel ->
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }

            // Fire alert channel - CRITICAL IMPORTANCE
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Fire Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical fire alarm notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    null
                )
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }.also { channel ->
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }
        }
    }

    private fun sendFireAlarmNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.fire)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true) // Critical for Android 12+
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(ALERT_NOTIFICATION_ID, notification)

        Log.d(TAG, "FIRE NOTIFICATION SENT: $message")
    }

    private fun cancelFireAlarmNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(ALERT_NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service stopping")
        firebaseListener?.let {
            macAddress?.let { mac ->
                database.child("/customers/$mac/Zones").removeEventListener(it)
            }
        }
        executor.shutdownNow()
        super.onDestroy()
    }
}