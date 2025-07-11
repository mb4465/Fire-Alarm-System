package com.example.firealarmsystem

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
//        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_logout -> {
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
