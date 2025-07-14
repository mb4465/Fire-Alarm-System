package com.example.firealarmsystem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("login_pref", Context.MODE_PRIVATE)
            val macAddress = prefs.getString("macAddress", null)
            val isLoggedIn = prefs.getBoolean("isLoggedIn", false)

            if (isLoggedIn && !macAddress.isNullOrEmpty()) {
                val serviceIntent = Intent(context, MyBackgroundService::class.java).apply {
                    putExtra("MAC_ADDRESS", macAddress)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
                Log.d("BootReceiver", "Service restarted for MAC: $macAddress")
            }
        }
    }
}