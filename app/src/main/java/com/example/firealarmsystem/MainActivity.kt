package com.example.firealarmsystem

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.onNavDestinationSelected
import com.example.firealarmsystem.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var navController: NavController

    companion object {
        const val PREF_NAME = "login_pref"
        const val KEY_IS_LOGGED_IN = "isLoggedIn"
        const val KEY_MAC_ADDRESS = "macAddress"
        private const val TAG = "MainActivity"
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted!")
                startBackgroundService()
            } else {
                Log.w(TAG, "Notification permission denied.")
                Toast.makeText(
                    this,
                    "Notification permission denied. Fire alerts will not work when app is closed.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow, R.id.nav_logout
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener(this)

        // Start service if logged in
        if (sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)) {
            Log.d(TAG, "User is logged in. Starting service check.")
            checkNotificationPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure service is running if we have permission
        if (sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false) && hasNotificationPermission()) {
            startBackgroundService()
        }
    }

    private fun checkNotificationPermission() {
        if (hasNotificationPermission()) {
            startBackgroundService()
        } else {
            requestNotificationPermission()
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Toast.makeText(
                    this,
                    "Fire alerts require notification permission to work when app is closed",
                    Toast.LENGTH_LONG
                ).show()
            }
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startBackgroundService()
        }
    }

    private fun startBackgroundService() {
        val macAddress = sharedPreferences.getString(KEY_MAC_ADDRESS, null)
        if (macAddress.isNullOrEmpty()) {
            Log.w(TAG, "Cannot start service without MAC address")
            return
        }

        val serviceIntent = Intent(this, MyBackgroundService::class.java).apply {
            putExtra("MAC_ADDRESS", macAddress)
        }

        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.d(TAG, "Background service started with MAC: $macAddress")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting background service: ${e.message}", e)
            Toast.makeText(
                this,
                "Failed to start background monitoring: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopBackgroundService() {
        val serviceIntent = Intent(this, MyBackgroundService::class.java)
        if (stopService(serviceIntent)) {
            Log.d(TAG, "Background service stopped")
        } else {
            Log.d(TAG, "Background service was not running")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_logout -> {
                stopBackgroundService()

                // Clear login state
                sharedPreferences.edit()
                    .putBoolean(KEY_IS_LOGGED_IN, false)
                    .remove(KEY_MAC_ADDRESS)
                    .apply()

                // Navigate to login
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return true
            }
        }
        binding.drawerLayout.closeDrawers()
        return item.onNavDestinationSelected(navController) || super.onSupportNavigateUp()
    }
}