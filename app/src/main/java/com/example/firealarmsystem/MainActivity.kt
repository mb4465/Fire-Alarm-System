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
import android.widget.Toast // Added for user feedback
import androidx.activity.result.contract.ActivityResultContracts // Added for permission handling
import androidx.navigation.NavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // Added for ContextCompat.startForegroundService
import androidx.navigation.ui.onNavDestinationSelected
import com.example.firealarmsystem.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var navController: NavController

    companion object {
        private const val PREF_NAME = "login_pref"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_MAC_ADDRESS = "macAddress"
        private const val TAG = "MainActivity" // For logging
    }

    // Register a launcher for requesting the POST_NOTIFICATIONS permission
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted!")
                Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
                startBackgroundService() // Start service if permission granted
            } else {
                Log.w(TAG, "Notification permission denied.")
                Toast.makeText(this, "Notification permission denied. Cannot show alerts effectively.", Toast.LENGTH_LONG).show()
                // You might want to stop the service or inform the user that background alerts won't work
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
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow, R.id.nav_logout
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener(this)

        // --- NEW: Handle service start based on login state and notification permission ---
        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        if (isLoggedIn) {
            Log.d(TAG, "User is logged in. Checking notification permission to start service.")
            requestNotificationPermission() // Request permission, then start service if granted
        } else {
            Log.d(TAG, "User is not logged in. Background service will not start automatically.")
            // Ensure service is stopped if not logged in (e.g., if app was killed and restarted
            // in a logged-out state, but service was sticky)
            stopBackgroundService()
        }
    }

    // --- NEW: Method to request notification permission ---
    private fun requestNotificationPermission() {
        // For Android 13 (API 33) and above, POST_NOTIFICATIONS is a runtime permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted, proceed to start service
                    Log.d(TAG, "POST_NOTIFICATIONS permission already granted.")
                    startBackgroundService()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Explain why the permission is needed (e.g., using a dialog or a Toast)
                    Log.d(TAG, "Showing rationale for POST_NOTIFICATIONS permission.")
                    Toast.makeText(this, "Fantom needs notification permission to alert you about fire alarms even when the app is closed.", Toast.LENGTH_LONG).show()
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Request the permission directly
                    Log.d(TAG, "Requesting POST_NOTIFICATIONS permission.")
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For Android versions below 13, POST_NOTIFICATIONS is not a runtime permission.
            // It's automatically granted if declared in the manifest.
            Log.d(TAG, "POST_NOTIFICATIONS not required for runtime permission (API < 33). Proceeding to start service.")
            startBackgroundService()
        }
    }

    // --- NEW: Method to start the background service ---
    private fun startBackgroundService() {
        val serviceIntent = Intent(this, MyBackgroundService::class.java)
        try {
            // IMPORTANT: For Android 12 (API 31) and above,
            // you MUST call ContextCompat.startForegroundService() when the app
            // is in the foreground (e.g., from an activity that the user is interacting with).
            // Attempting to start it from the background can throw ForegroundServiceStartNotAllowedException.
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Fantom monitoring service initiated.", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Attempted to start MyBackgroundService.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting background service: ${e.message}", e)
            Toast.makeText(this, "Error starting service. Check device logs.", Toast.LENGTH_LONG).show()
        }
    }

    // --- NEW: Method to stop the background service ---
    private fun stopBackgroundService() {
        val serviceIntent = Intent(this, MyBackgroundService::class.java)
        if (stopService(serviceIntent)) {
            Toast.makeText(this, "Fantom monitoring service stopped.", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "MyBackgroundService stopped.")
        } else {
            Toast.makeText(this, "Fantom monitoring service was not running.", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "MyBackgroundService was not running to stop.")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
//        menuInflater.inflate(R.menu.main, menu) // Your original commented out line
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_logout -> {
                // --- NEW: Stop the background service when logging out ---
                stopBackgroundService()

                // Clear login state
                val editor = sharedPreferences.edit()
                editor.putBoolean(KEY_IS_LOGGED_IN, false)
                editor.remove(KEY_MAC_ADDRESS)
                editor.apply()

                // Navigate to LoginActivity
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return true
            }
        }
        // Close the navigation drawer when an item is tapped.
        binding.drawerLayout.closeDrawers()
        // This is important to allow the normal menu items to be handled by the NavController
        // Corrected line: Use a valid combination for navigation and options item selection
        // The following line uses NavigationUI.onNavDestinationSelected to handle standard menu items.
        // It's combined with checking if navigateUp handled the action, common for custom handling.
        val navigatedUp = navController.navigateUp(appBarConfiguration)
        return item.onNavDestinationSelected(navController) || navigatedUp || super.onSupportNavigateUp()
    }
}