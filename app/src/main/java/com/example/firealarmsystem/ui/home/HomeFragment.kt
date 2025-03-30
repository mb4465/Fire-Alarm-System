package com.example.firealarmsystem.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.database.*
import com.example.firealarmsystem.R // Replace with your actual package name
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class HomeFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var macAddress: String
    private lateinit var pin: String

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
        // Retrieve mac address and pin from where you store them
        // like sharedPreferences, arguments or from loginActivity
        // Here is a dummy initialization
        // you need to fetch these values form the login screen
        macAddress = "AA:BB:CC:DD:EE:FF"
        pin = "0000"
        initializeFirebase()
        loadDataFromFirebase()
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
        //Removed debug log here.
    }

    private fun loadDataFromFirebase() {

        // Build the path to the customer's data
        val customerPath = "/customers/$macAddress"


        // Attach a listener to read the data at our posts reference
        database.child(customerPath).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                if (snapshot.exists()) {
                    Log.d("Firebase", "Data Exists.")
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
                                Log.d(
                                    "Firebase",
                                    "zoneId: " + zoneId.toString() + " zoneStatus: " + zoneStatus.toString()
                                );
                                activity?.runOnUiThread {
                                    updateZoneUI(zoneId, zoneStatus)
                                }
                            } else {
                                Log.w("Firebase", "zoneId or zoneStatus is null");
                            }
                        }
                    } else {
                        Log.w("Firebase", "Zones do not exist");
                    }

                } else {
                    Log.d("Firebase", "Data Doesn't Exist.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
                Log.w("Firebase", "Failed to read value.", error.toException())
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

    private fun updateStatusUI(cardView: CardView, imageView: ImageView, textView: TextView, status: Int) {
        Log.d("Firebase", "Updating status for imageView and textView with status: $status");
        when (status) {
            1 -> {
                imageView.setImageResource(R.drawable.good)
                textView.text = "OK"
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_ok))
            }
            2 -> {
                imageView.setImageResource(R.drawable.fire)
                textView.text = "FIRE"
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_fire))
            }
            3 -> {
                imageView.setImageResource(R.drawable.disconnect)
                textView.text = "OFFLINE"
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_offline))
            }
            4 -> {
                imageView.setImageResource(R.drawable.ic_sensor_warning)
                textView.text = "WARNING"
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_warning)) // Assuming you have a color defined for warnings
            }
            else -> {
                imageView.setImageResource(R.drawable.disconnect)
                textView.text = "UNKNOWN"
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_unknown)) // Assuming you have a color defined for unknown states
            }
        }
    }
}