package com.example.firealarmsystem

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LoginActivity : AppCompatActivity() {

    private lateinit var etMac: EditText
    private lateinit var etPin: EditText
    private lateinit var btnLogin: Button
    private lateinit var database: DatabaseReference
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREF_NAME = "login_pref"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_MAC_ADDRESS = "macAddress"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Check if user is already logged in
        if (sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)) {
            val macAddress = sharedPreferences.getString(KEY_MAC_ADDRESS, null)
            if (macAddress != null) {
                navigateToDashboard(macAddress)
                finish() // Finish LoginActivity so user can't go back to it
                return // Skip the rest of onCreate
            }
        }

        setContentView(R.layout.activity_login)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance().reference.child("customers")

        // Initialize Views
        etMac = findViewById(R.id.et_mac)
        etPin = findViewById(R.id.et_pin)
        btnLogin = findViewById(R.id.btn_login)

        btnLogin.setOnClickListener {
            login()
        }
    }

    private fun login() {
        val macAddress = etMac.text.toString().trim()
        val pin = etPin.text.toString().trim()

        if (macAddress.isEmpty() || pin.isEmpty()) {
            showToast("Please enter Username and Password")
            return
        }

        database.orderByKey().equalTo(macAddress).addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (customerSnapshot in snapshot.children) {
                        val correctPin = customerSnapshot.child("pin").getValue(String::class.java)
                        if (pin == correctPin) {
                            showToast("Login Successfully!")
                            // Save login state
                            val editor = sharedPreferences.edit()
                            editor.putBoolean(KEY_IS_LOGGED_IN, true)
                            editor.putString(KEY_MAC_ADDRESS, customerSnapshot.key.toString())
                            editor.apply()

                            navigateToDashboard(customerSnapshot.key.toString())
                            finish() // Finish LoginActivity after successful login
                            return
                        }
                    }
                    showToast("Incorrect Password")
                } else {
                    showToast("No user found with this Username")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showToast("Error: ${error.message}")
            }
        })
    }

    private fun navigateToDashboard(customerId: String) {
        val dashboardIntent = Intent(this, MainActivity::class.java)
        dashboardIntent.putExtra("customer_id", customerId)
        startActivity(dashboardIntent)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
