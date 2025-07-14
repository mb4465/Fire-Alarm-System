package com.example.firealarmsystem.ui.home

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.registerForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.firealarmsystem.LoginActivity
import com.example.firealarmsystem.MainActivity
import com.example.firealarmsystem.R
import com.example.firealarmsystem.databinding.FragmentHomeBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: DatabaseReference
    private lateinit var macAddress: String
    private var firebaseListener: ValueEventListener? = null
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkAvailable = false

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

    // Notification setup
    private val CHANNEL_ID = "fire_alarm_channel"
    private lateinit var notificationManager: NotificationManagerCompat
    private val zonePreviousStatus = mutableMapOf<Int, Int>()
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("HomeFragment", "Notification permission granted.")
        } else {
            Log.w("HomeFragment", "Notification permission denied.")
            Toast.makeText(context, "Notification permission denied. Alarm alerts may not be shown.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI elements
        initViews(binding.root)
        // In MainActivity onCreate()
//        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
//        val mac = prefs.getString(KEY_MAC_ADDRESS, "NOT FOUND")
//        val keys = prefs.all.keys.joinToString()
//        Log.d(TAG, "MAC in prefs: $mac\nKeys: $keys")

        // Setup notifications
        notificationManager = NotificationManagerCompat.from(requireContext())
        createNotificationChannel()

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Initialize network monitoring
        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        setupNetworkMonitoring()

        // Get MAC address from login
        val loggedInMacAddress = activity?.intent?.getStringExtra("customer_id")
        if (loggedInMacAddress != null && loggedInMacAddress.isNotEmpty()) {
            macAddress = loggedInMacAddress
            initializeFirebase()
            loadDataFromFirebase()
        } else {
            handleLoginError()
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

    private fun setupNetworkMonitoring() {
        // Immediate network check
        checkNetworkState()

        // Continuous network monitoring
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                isNetworkAvailable = true
                activity?.runOnUiThread {
                    binding.networkStatus.visibility = View.GONE
                    Toast.makeText(context, "Internet connected", Toast.LENGTH_SHORT).show()
                    loadDataFromFirebase()
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                isNetworkAvailable = false
                activity?.runOnUiThread {
                    handleNetworkLost()
                }
            }
        }
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun checkNetworkState() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isNetworkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (!isNetworkAvailable) {
            handleNetworkLost()
        } else {
            binding.networkStatus.visibility = View.GONE
        }
    }

    private fun handleNetworkLost() {
        // Show network status warning
        binding.networkStatus.visibility = View.VISIBLE

        // Set all zones to offline
        setAllZonesOffline()

        // Show toast notification
        Toast.makeText(
            context,
            "No internet connection - All zones offline",
            Toast.LENGTH_LONG
        ).show()

        // Detach Firebase listener
        firebaseListener?.let {
            database.child("/customers/$macAddress").removeEventListener(it)
            firebaseListener = null
        }
    }

    private fun setAllZonesOffline() {
        for (zoneId in 1..8) {
            updateZoneUI(zoneId, 3) // 3 = OFFLINE status
        }
    }

    private fun handleLoginError() {
        Log.e("HomeFragment", "MAC Address not received. Cannot load data.")
        Toast.makeText(context, "Error: User MAC Address not found. Please log in again.", Toast.LENGTH_LONG).show()
        activity?.let {
            val intent = Intent(it, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            it.finish()
        }
    }

    private fun initializeFirebase() {
        database = Firebase.database.reference
    }

    private fun loadDataFromFirebase() {
        // Skip if listener already attached
        if (firebaseListener != null) return

        val customerPath = "/customers/$macAddress"
        Log.d("HomeFragment", "Loading data for customer path: $customerPath")

        firebaseListener = object : ValueEventListener {
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
                if (!isNetworkAvailable) {
                    setAllZonesOffline()
                } else {
                    Toast.makeText(context, "Firebase error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        database.child(customerPath).addValueEventListener(firebaseListener!!)
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

        activity?.runOnUiThread{
            batteryImage.setImageResource(batteryDrawable)
        }
    }

    private fun updateSoundIcon(soundState: String){
        val soundDrawable = when(soundState){
            "mute" -> R.drawable.no_sound
            else -> R.drawable.sound
        }
        activity?.runOnUiThread{
            soundImage.setImageResource(soundDrawable)
        }
    }

    private fun updateZoneUI(zoneId: Int, zoneStatus: Int) {
        val prevStatus = zonePreviousStatus[zoneId]

        // Check if the status has changed to an alert state (FIRE or WARNING)
        if (isAlertStatus(zoneStatus) && !isAlertStatus(prevStatus)) {
            // Get the correct status string for the notification
            val statusString = when (zoneStatus) {
                0 -> "FIRE"      // Status 0 = FIRE
                2 -> "WARNING"   // Status 2 = WARNING
                else -> "UNKNOWN" // Shouldn't happen for alerts
            }

            sendFireAlarmNotification(
                zoneId,
                "Zone $zoneId Alert!",
                "Zone $zoneId status is: $statusString"
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

    private fun isAlertStatus(status: Int?): Boolean {
        // Only status 0 (FIRE) and 2 (WARNING) are alerts
        return status == 0 || status == 2
    }

    private fun getStatusString(status: Int): String {
        return when (status) {
            0 -> "FIRE"       // Status 0 = FIRE
            1 -> "NORMAL"     // Status 1 = NORMAL
            2 -> "WARNING"    // Status 2 = WARNING
            3 -> "OFFLINE"    // Status 3 = OFFLINE
            else -> "UNKNOWN" // Other values = UNKNOWN
        }
    }

    private fun updateStatusUI(cardView: CardView, imageView: ImageView, textView: TextView, status: Int) {
        Log.d("Firebase", "Updating status for imageView and textView with status: $status")
        activity?.runOnUiThread {
            when (status) {
                0 -> {  // FIRE
                    imageView.setImageResource(R.drawable.fire)
                    textView.text = "FIRE"
                    cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_fire))
                }
                1 -> {  // NORMAL
                    imageView.setImageResource(R.drawable.good)
                    textView.text = "NORMAL"
                    cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_ok))
                }
                2 -> {  // WARNING
                    imageView.setImageResource(R.drawable.ic_sensor_warning)
                    textView.text = "WARNING"
                    cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_warning))
                }
                3 -> {  // OFFLINE
                    imageView.setImageResource(R.drawable.disconnect)
                    textView.text = "OFFLINE"
                    cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_offline))
                }
                else -> {  // UNKNOWN
                    imageView.setImageResource(R.drawable.disconnect)
                    textView.text = "UNKNOWN"
                    cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_unknown))
                }
            }
        }
    }

    // --- Notification Helper Functions ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500) // Custom vibration pattern
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendFireAlarmNotification(zoneId: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("HomeFragment", "Cannot post notification: Permission denied")
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            requireContext(),
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(requireContext(), CHANNEL_ID)
            .setSmallIcon(R.drawable.fire)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX) // Highest priority for alarms
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(true) // Make notification persistent until dismissed
            .setOnlyAlertOnce(false) // Alert every time

        // Use alarm sound for fire notifications
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        builder.setSound(alarmSound)

        // Add vibration pattern
        builder.setVibrate(longArrayOf(0, 500, 250, 500))

        // Add full-screen intent for critical alerts
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setFullScreenIntent(pendingIntent, true)
        }

        notificationManager.notify(ZONE_NOTIFICATION_BASE_ID + zoneId, builder.build())
    }

    private fun cancelFireAlarmNotification(zoneId: Int) {
        notificationManager.cancel(ZONE_NOTIFICATION_BASE_ID + zoneId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up resources
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        firebaseListener?.let {
            database.child("/customers/$macAddress").removeEventListener(it)
        }
        _binding = null
    }

    companion object {
        private const val ZONE_NOTIFICATION_BASE_ID = 1000
    }
}