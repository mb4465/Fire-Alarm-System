package com.example.firealarmsystem.ui.home

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts // For runtime permissions
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat // Use NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.database.*
import com.example.firealarmsystem.R
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.example.firealarmsystem.LoginActivity
import com.example.firealarmsystem.MainActivity // Import your MainActivity

class HomeFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var macAddress: String

    // UI elements
    private lateinit var zone1Card: CardView
    private lateinit var zone1StatusImage: ImageView
    private lateinit var zone1StatusText: TextView
    private lateinit var zone2Card: CardView
    private lateinit var zone2StatusImage: ImageView
    private lateinit var zone2StatusText: TextView
    private lateinit var zone3Card: CardView
    private lateinit var zone3StatusImage: ImageView
    private lateinit var zone3StatusText: TextView
    private lateinit var zone4Card: CardView
    private lateinit var zone4StatusImage: ImageView
    private lateinit var zone4StatusText: TextView
    private lateinit var zone5Card: CardView
    private lateinit var zone5StatusImage: ImageView
    private lateinit var zone5StatusText: TextView
    private lateinit var zone6Card: CardView
    private lateinit var zone6StatusImage: ImageView
    private lateinit var zone6StatusText: TextView
    private lateinit var zone7Card: CardView
    private lateinit var zone7StatusImage: ImageView
    private lateinit var zone7StatusText: TextView
    private lateinit var zone8Card: CardView
    private lateinit var zone8StatusImage: ImageView
    private lateinit var zone8StatusText: TextView

    private lateinit var batteryImage: ImageView
    private lateinit var soundImage: ImageView

    // --- New additions for Notifications ---
    private val CHANNEL_ID = "fire_alarm_channel"
    private lateinit var notificationManager: NotificationManagerCompat // Use NotificationManagerCompat
    private val zonePreviousStatus = mutableMapOf<Int, Int>() // To track previous zone states to prevent spamming notifications

    // Activity Result Launcher for requesting POST_NOTIFICATIONS permission (for Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted. You can now post notifications.
            Log.d("HomeFragment", "Notification permission granted.")
        } else {
            // Permission is denied. Inform the user.
            Log.w("HomeFragment", "Notification permission denied.")
            Toast.makeText(context, "Notification permission denied. Alarm alerts may not be shown.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        initViews(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize NotificationManagerCompat
        notificationManager = NotificationManagerCompat.from(requireContext())

        // Create notification channel (important for Android O and above)
        createNotificationChannel()

        // Request POST_NOTIFICATIONS permission for Android 13 (API 33) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // TIRAMISU is API 33
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val loggedInMacAddress = activity?.intent?.getStringExtra("customer_id")

        if (loggedInMacAddress != null && loggedInMacAddress.isNotEmpty()) {
            macAddress = loggedInMacAddress
            initializeFirebase()
            loadDataFromFirebase()
        } else {
            Log.e("HomeFragment", "MAC Address not received from LoginActivity. Cannot load data.")
            Toast.makeText(context, "Error: User MAC Address not found. Please log in again.", Toast.LENGTH_LONG).show()

            activity?.let {
                val intent = Intent(it, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                it.finish()
            }
        }
    }


    private fun initViews(view: View) {
        batteryImage = view.findViewById(R.id.ivBattery)
        soundImage = view.findViewById(R.id.ivSound)
        zone1Card = view.findViewById(R.id.zone1Card)
        zone1StatusImage = view.findViewById(R.id.zone1StatusImage)
        zone1StatusText = view.findViewById(R.id.zone1StatusText)
        zone2Card = view.findViewById(R.id.zone2Card)
        zone2StatusImage = view.findViewById(R.id.zone2StatusImage)
        zone2StatusText = view.findViewById(R.id.zone2StatusText)
        zone3Card = view.findViewById(R.id.zone3Card)
        zone3StatusImage = view.findViewById(R.id.zone3StatusImage)
        zone3StatusText = view.findViewById(R.id.zone3StatusText)
        zone4Card = view.findViewById(R.id.zone4Card)
        zone4StatusImage = view.findViewById(R.id.zone4StatusImage)
        zone4StatusText = view.findViewById(R.id.zone4StatusText)
        zone5Card = view.findViewById(R.id.zone5Card)
        zone5StatusImage = view.findViewById(R.id.zone5StatusImage)
        zone5StatusText = view.findViewById(R.id.zone5StatusText)
        zone6Card = view.findViewById(R.id.zone6Card)
        zone6StatusImage = view.findViewById(R.id.zone6StatusImage)
        zone6StatusText = view.findViewById(R.id.zone6StatusText)
        zone7Card = view.findViewById(R.id.zone7Card)
        zone7StatusImage = view.findViewById(R.id.zone7StatusImage)
        zone7StatusText = view.findViewById(R.id.zone7StatusText)
        zone8Card = view.findViewById(R.id.zone8Card)
        zone8StatusImage = view.findViewById(R.id.zone8StatusImage)
        zone8StatusText = view.findViewById(R.id.zone8StatusText)
    }

    private fun initializeFirebase() {
        database = Firebase.database.reference
    }

    private fun loadDataFromFirebase() {
        val customerPath = "/customers/$macAddress"
        Log.d("HomeFragment", "Loading data for customer path: $customerPath")

        database.child(customerPath).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d("Firebase", "Data Exists for $macAddress.")
                    val batteryLevel = snapshot.child("battery").getValue(Int::class.java) ?: 6
                    val soundState = snapshot.child("sound").getValue(String::class.java) ?: "unmute"
                    updateBatteryIcon(batteryLevel)
                    updateSoundIcon(soundState)

                    val zones = snapshot.child("Zones")
                    if (zones.exists()) {
                        for (zoneSnapshot in zones.children) {
                            val zoneId = zoneSnapshot.child("id").getValue(Int::class.java)
                            val zoneStatus = zoneSnapshot.child("status").getValue(Int::class.java)

                            if (zoneId != null && zoneStatus != null) {
                                Log.d("Firebase", "zoneId: $zoneId zoneStatus: $zoneStatus")
                                activity?.runOnUiThread {
                                    updateZoneUI(zoneId, zoneStatus)
                                }
                            } else {
                                Log.w("Firebase", "zoneId or zoneStatus is null for a zone in $macAddress")
                            }
                        }
                    } else {
                        Log.w("Firebase", "Zones do not exist for $macAddress")
                    }

                } else {
                    Log.d("Firebase", "Data Doesn't Exist for $macAddress.")
                    Toast.makeText(context, "No data found for this account.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("Firebase", "Failed to read value for $macAddress.", error.toException())
                Toast.makeText(context, "Firebase error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateBatteryIcon(batteryLevel:Int){
        val batteryDrawable = when(batteryLevel){
            1-> R.drawable.bat_1
            2-> R.drawable.bat_2
            3-> R.drawable.bat_3
            4-> R.drawable.bat_4
            5-> R.drawable.bat_5
            else -> R.drawable.bat_6
        }

        batteryImage.post {
            activity?.runOnUiThread{
                batteryImage.setImageResource(batteryDrawable)
            }
        }
    }

    private fun updateSoundIcon(soundState: String){
        val soundDrawable = when(soundState){
            "mute" -> R.drawable.no_sound
            else -> R.drawable.sound
        }
        soundImage.post {
            activity?.runOnUiThread{
                soundImage.setImageResource(soundDrawable)
            }
        }
    }

    private fun updateZoneUI(zoneId: Int, zoneStatus: Int) {
        val prevStatus = zonePreviousStatus[zoneId]

        // Check if the status has changed to an alert state (FIRE or UNKNOWN/0)
        // AND the previous status was NOT an alert state (NORMAL, OFFLINE, WARNING)
        if (isAlertStatus(zoneStatus) && !isAlertStatus(prevStatus)) {
            sendFireAlarmNotification(
                zoneId, // Pass zoneId for unique notification ID
                "Zone $zoneId Alert!",
                "Zone $zoneId status is: ${getStatusString(zoneStatus)}"
            )
        } else if (!isAlertStatus(zoneStatus) && isAlertStatus(prevStatus)) {
            // If it transitioned from an alert state to a non-alert state, cancel the notification
            cancelFireAlarmNotification(zoneId)
        }

        // Always update the previous status for this zone
        zonePreviousStatus[zoneId] = zoneStatus

        when (zoneId) {
            1 -> updateStatusUI(zone1Card, zone1StatusImage, zone1StatusText, zoneStatus)
            2 -> updateStatusUI(zone2Card, zone2StatusImage, zone2StatusText, zoneStatus)
            3 -> updateStatusUI(zone3Card, zone3StatusImage, zone3StatusText, zoneStatus)
            4 -> updateStatusUI(zone4Card, zone4StatusImage, zone4StatusText, zoneStatus)
            5 -> updateStatusUI(zone5Card, zone5StatusImage, zone5StatusText, zoneStatus)
            6 -> updateStatusUI(zone6Card, zone6StatusImage, zone6StatusText, zoneStatus)
            7 -> updateStatusUI(zone7Card, zone7StatusImage, zone7StatusText, zoneStatus)
            8 -> updateStatusUI(zone8Card, zone8StatusImage, zone8StatusText, zoneStatus)
        }
    }

    /**
     * Determines if a given status is an alert condition.
     * Status 2 is FIRE. Status 0 (and other undefined) maps to UNKNOWN.
     * Both FIRE and UNKNOWN are considered alert states.
     */
    private fun isAlertStatus(status: Int?): Boolean {
        // Status 2 for FIRE, and 0 for UNKNOWN are considered alerts.
        // Other statuses (1=NORMAL, 3=OFFLINE, 4=WARNING) are not alerts.
        return status == 2 || status == 0
    }

    /**
     * Converts an integer status code to its corresponding string representation.
     */
    private fun getStatusString(status: Int): String {
        return when (status) {
            1 -> "NORMAL"
            2 -> "FIRE"
            3 -> "OFFLINE"
            4 -> "WARNING"
            else -> "UNKNOWN" // This covers status 0 as well, based on current UI logic
        }
    }

    private fun updateStatusUI(cardView: CardView, imageView: ImageView, textView: TextView, status: Int) {
        Log.d("Firebase", "Updating status for imageView and textView with status: $status")
        when (status) {
            1 -> {
                imageView.setImageResource(R.drawable.good)
                textView.text = "NORMAL"
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_ok))
            }
            0 -> {
                imageView.setImageResource(R.drawable.fire)
                textView.text = "FIRE"
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_fire))
            }
            3 -> {
                imageView.setImageResource(R.drawable.disconnect)
                textView.text = "OFFLINE"
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_offline))
            }
            2 -> {
                imageView.setImageResource(R.drawable.ic_sensor_warning)
                textView.text = "WARNING"
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_warning))
            }
            else -> { // This covers status 0 (as per request) and any other undefined status
                imageView.setImageResource(R.drawable.disconnect)
                textView.text = "UNKNOWN"
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_unknown))
            }
        }
    }

    // --- Notification Helper Functions ---

    /**
     * Creates a NotificationChannel for Android 8.0 (API 26) and higher.
     * This ensures the app can send notifications with proper sound and importance settings.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH // For heads-up notifications
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                // Set the default notification sound for this channel
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                enableVibration(true) // Enable vibration
            }
            // Register the channel with the system using NotificationManagerCompat
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Sends a fire alarm notification to the user.
     * @param zoneId The ID of the zone triggering the alert (used for unique notification ID).
     * @param title The title of the notification.
     * @param message The detailed message of the notification.
     */
    private fun sendFireAlarmNotification(zoneId: Int, title: String, message: String) {
        // IMPORTANT: For Android 13+, check POST_NOTIFICATIONS permission before notifying
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("HomeFragment", "Cannot post notification: POST_NOTIFICATIONS permission not granted. User may have denied it or not been prompted yet.")
                // Optionally, you could try to request permission again here, but avoid spamming.
                // The main request is done in onViewCreated.
                return
            }
        }

        // Create an explicit intent for MainActivity, which hosts HomeFragment.
        // Tapping the notification will bring the user back to the app's main screen.
        val intent = Intent(context, MainActivity::class.java).apply {
            // These flags ensure the app comes to the foreground with a clean task if not already running.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            requireContext(),
            0, // Request code
            intent,
            // FLAG_IMMUTABLE is required for Android 6.0 (API 23) and above.
            // FLAG_UPDATE_CURRENT ensures that if the PendingIntent already exists,
            // its extra data is updated rather than creating a new one.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(requireContext(), CHANNEL_ID)
            .setSmallIcon(R.drawable.fire) // Use a relevant icon for the notification
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)) // Use BigTextStyle for longer messages
            .setPriority(NotificationCompat.PRIORITY_HIGH) // For heads-up notifications on Android 7.1 and lower
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Categorize as an alarm
            .setContentIntent(pendingIntent) // Set the intent that fires when the user taps the notification
            .setAutoCancel(true) // Dismiss the notification when the user taps it

        // For API levels below 26, the sound needs to be set directly on the builder.
        // For API 26+, it's handled by the NotificationChannel itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE) // Include default vibration
        }

        // Use a unique ID for each zone's notification to allow multiple alerts to be shown
        // Offset by a large number to avoid conflicts with other potential app notifications.
        notificationManager.notify(ZONE_NOTIFICATION_BASE_ID + zoneId, builder.build())
    }

    /**
     * Cancels a specific fire alarm notification.
     * @param zoneId The ID of the zone whose notification should be cancelled.
     */
    private fun cancelFireAlarmNotification(zoneId: Int) {
        notificationManager.cancel(ZONE_NOTIFICATION_BASE_ID + zoneId)
    }

    companion object {
        // Base ID for zone-specific notifications to ensure uniqueness.
        private const val ZONE_NOTIFICATION_BASE_ID = 1000
    }
}