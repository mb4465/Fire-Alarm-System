package com.example.firealarmsystem

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            showToast("Please enter Username and Password") // Changed here
            return
        }

        // Check if customer entity exists based on mac address
        database.orderByKey().equalTo(macAddress).addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // check pin
                    for (customerSnapshot in snapshot.children) {
                        val correctPin = customerSnapshot.child("pin").getValue(String::class.java)
                        if (pin == correctPin) {
                            // Credentials are correct, navigate to dashboard
                            showToast("Login Successfully!")
                            navigateToDashboard(customerSnapshot.key.toString())
                            return
                        }
                    }
                    showToast("Incorrect Password") // Changed here

                } else {
                    showToast("No user found with this Username") // Changed here
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showToast("Error: ${error.message}")
            }
        })
    }

    private fun navigateToDashboard(customerId: String) {
        // Navigate to Dashboard, pass customer ID (or Mac Address)
        val dashboardIntent = Intent(this, MainActivity::class.java)
        dashboardIntent.putExtra("customer_id", customerId)
        startActivity(dashboardIntent)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}