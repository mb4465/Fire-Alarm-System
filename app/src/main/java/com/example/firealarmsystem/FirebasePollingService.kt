package com.example.firealarmsystem

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.Timer
import java.util.TimerTask

class FirebasePollingService : Service() {

    private var timer: Timer? = null
    private var timerTask: TimerTask? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var database: DatabaseReference
    private var macAddress: String? = null
    private lateinit var sharedPreferences: SharedPreferences

    // To keep track of notified fire alarms to prevent spamming
    private val notifiedFireZoneIds = mutableSetOf<String>()

    companion object {
        const val TAG = "FirebasePollingService"
        const val POLLING_INTERVAL_SECONDS = 15 // Check every 15 seconds
        const val NOTIFICATION_CHANNEL_ID = "FirebasePollingChannel"
        const val START_SERVICE_ACTION = "START_POLLING_SERVICE"
        const val STOP_SERVICE_ACTION = "STOP_POLLING_SERVICE"
        const val EXTRA_MAC_ADDRESS = "macAddress"

        private const val PREF_NAME = "login_pref" // Same as LoginActivity
        private const val KEY_MAC_ADDRESS = "macAddress" // Same as LoginActivity
        private const val FIRE_STATUS_CODE = 1 // Assuming 1 means FIRE
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        database = Firebase.database.reference
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action: ${intent?.action}")

        if (intent?.action == STOP_SERVICE_ACTION) {
            Log.d(TAG, "Stopping service via intent action.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Try to get MAC address from intent, then from SharedPreferences
        val intentMacAddress = intent?.getStringExtra(EXTRA_MAC_ADDRESS)
        if (!intentMacAddress.isNullOrEmpty()) {
            macAddress = intentMacAddress
            Log.d(TAG, "MAC Address from Intent: $macAddress")
        } else {
            macAddress = sharedPreferences.getString(KEY_MAC_ADDRESS, null)
            Log.d(TAG, "MAC Address from SharedPreferences: $macAddress")
        }

        if (macAddress.isNullOrEmpty()) {
            Log.e(TAG, "MAC Address is null or empty. Stopping service.")
            stopSelf() // Stop if no MAC address is available
            return START_NOT_STICKY
        }

        Log.d(TAG, "Service started with MAC: $macAddress. Starting timer.")
        startTimer()
        return START_STICKY // Keep service running
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopTimerTask()
        super.onDestroy()
    }

    private fun startTimer() {
        if (timer != null) return // Timer already running

        timer = Timer()
        initializeTimerTask()
        //timer.schedule(timerTask, 5000L, POLLING_INTERVAL_SECONDS * 1000L)
        // Corrected schedule: initial delay, then period
        timer?.scheduleAtFixedRate(timerTask, 5000L, POLLING_INTERVAL_SECONDS * 1000L)
        Log.d(TAG, "Timer scheduled to run every $POLLING_INTERVAL_SECONDS seconds.")
    }

    private fun stopTimerTask() {
        timer?.cancel()
        timer = null
        timerTask?.cancel() // Also cancel the task itself
        timerTask = null
        Log.d(TAG, "Timer stopped.")
    }

    private fun initializeTimerTask() {
        timerTask = object : TimerTask() {
            override fun run() {
                // Perform task in a handler to interact with UI components or main thread operations if needed safely
                // For Firebase calls which are already async, direct call might be okay, but handler is safer for general service tasks
                handler.post {
                    fetchDataAndNotify()
                }
            }
        }
    }

    private fun fetchDataAndNotify() {
        if (macAddress.isNullOrEmpty()) {
            Log.w(TAG, "MAC address not set, skipping fetch.")
            return
        }
        val currentMac = macAddress ?: return // Ensure macAddress is not null

        Log.d(TAG, "Fetching data for MAC: $currentMac")
        // Path should ideally point directly to the Zones for a specific customer
        val customerPath = "/customers/$currentMac/Zones"

        database.child(customerPath).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d(TAG, "Data received for $currentMac")
                    var fireDetectedInCurrentFetch = false
                    for (zoneSnapshot in snapshot.children) {
                        val zoneId = zoneSnapshot.child("id").getValue(Int::class.java)
                        val zoneName = zoneSnapshot.child("name").getValue(String::class.java) ?: "Zone $zoneId"
                        val zoneStatus = zoneSnapshot.child("status").getValue(Int::class.java)

                        if (zoneId != null && zoneStatus == FIRE_STATUS_CODE) {
                            fireDetectedInCurrentFetch = true
                            val uniqueZoneIdentifier = "$currentMac-$zoneId" // Create a unique ID for the zone across MACs
                            if (!notifiedFireZoneIds.contains(uniqueZoneIdentifier)) {
                                Log.i(TAG, "FIRE detected in $zoneName (ID: $zoneId). Sending notification.")
                                sendFireNotification(zoneId, zoneName)
                                notifiedFireZoneIds.add(uniqueZoneIdentifier)
                            } else {
                                Log.d(TAG, "FIRE in $zoneName (ID: $zoneId) already notified.")
                            }
                        } else if (zoneId != null && zoneStatus != FIRE_STATUS_CODE) {
                            // If zone is no longer on fire, remove it from notified set so it can notify again if it catches fire later
                            val uniqueZoneIdentifier = "$currentMac-$zoneId"
                            if (notifiedFireZoneIds.contains(uniqueZoneIdentifier)) {
                                Log.d(TAG, "Zone $zoneName (ID: $zoneId) is no longer on fire. Clearing notification flag.")
                                notifiedFireZoneIds.remove(uniqueZoneIdentifier)
                            }
                        }
                    }
                     if (!fireDetectedInCurrentFetch) {
                        // Optional: If no fires are currently active for this user, one could clear all notifications,
                        // or more granularly, only remove those zones that were previously on fire but are now clear.
                        // The current logic in the loop already handles removing specific zones if they are confirmed NOT to be on fire.
                        Log.d(TAG, "No active fires detected for $currentMac in this fetch.")
                    }
                } else {
                    Log.w(TAG, "No data/zones found for MAC: $currentMac at path $customerPath")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase data fetch cancelled for $currentMac: ${error.message}")
            }
        })
    }

    private fun sendFireNotification(zoneId: Int, zoneName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Optional: add extras to intent to navigate to a specific zone in MainActivity
            // putExtra("zone_id_to_show", zoneId)
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        // Using zoneId as the request code for PendingIntent ensures uniqueness for each zone's notification
        val pendingIntent = PendingIntent.getActivity(this, zoneId, intent, pendingIntentFlags)

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Using default alert icon // Ensure this drawable exists
            .setContentTitle(getString(R.string.fire_alert_title)) // Using string resource
            .setContentText(getString(R.string.fire_alert_message, zoneName)) // Using string resource
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Important for alarms
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Notification disappears when tapped
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) // Use ALARM sound
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500)) // Vibration pattern: delay, vibrate, pause, ...
             // Add .setOngoing(false) if it should not be persistent after user sees it. Typically fire alerts are not ongoing.

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Using zoneId as notification ID to allow updating/cancelling per zone
        notificationManager.notify(zoneId, notificationBuilder.build())
        Log.d(TAG, "Notification sent for zone ID: $zoneId")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.firebase_polling_channel_name) // Using string resource
            val descriptionText = getString(R.string.firebase_polling_channel_description) // Using string resource
            val importance = NotificationManager.IMPORTANCE_HIGH // Crucial for fire alerts
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true) // Optional: LED light
                lightColor = android.graphics.Color.RED // Optional: LED color
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500) // Consistent vibration pattern
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created.")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We are not using a bound service
    }
}
