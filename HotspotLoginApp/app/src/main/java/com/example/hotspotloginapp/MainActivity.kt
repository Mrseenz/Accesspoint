package com.example.hotspotloginapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), HotspotManager.HotspotStateListener {

    private lateinit var hotspotManager: HotspotManager
    private lateinit var toggleButton: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvSsid: TextView
    private lateinit var tvPassword: TextView
    private lateinit var tvLoginUrl: TextView

    private val TAG = "MainActivity"
    private val NEARBY_WIFI_REQUEST_CODE = 101
    private var pendingHotspotAction: String? = null
    private val START_ACTION = "START"
    // private val STOP_ACTION = "STOP" // Not strictly needed for pending action if stop is direct

    // This flag helps decide if the "Start Hotspot" button should try to start or open settings.
    private var requiresManualSetupMode = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hotspotManager = HotspotManager(this)
        hotspotManager.setHotspotStateListener(this)

        toggleButton = findViewById(R.id.buttonToggleHotspot)
        tvStatus = findViewById(R.id.textViewHotspotStatus)
        tvSsid = findViewById(R.id.textViewSsid)
        tvPassword = findViewById(R.id.textViewPassword)
        tvLoginUrl = findViewById(R.id.textViewLoginUrl)

        updateUiForCurrentState()

        toggleButton.setOnClickListener {
            Log.d(TAG, "Toggle Hotspot button clicked. Current text: ${toggleButton.text}")
            if (requiresManualSetupMode && toggleButton.text.toString().contains("Settings")) {
                hotspotManager.openWifiTetheringSettings()
            } else if (hotspotManager.isHotspotEnabled()) {
                hotspotManager.stopHotspot() // UI update via onHotspotStopped
            } else {
                pendingHotspotAction = START_ACTION
                checkAndRequestNearbyWifiPermission()
            }
        }
    }

    private fun checkAndRequestNearbyWifiPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), NEARBY_WIFI_REQUEST_CODE)
            } else {
                proceedWithStartHotspot()
            }
        } else {
            proceedWithStartHotspot()
        }
    }

    private fun proceedWithStartHotspot() {
        // onHotspotStarting will be called by HotspotManager
        hotspotManager.startHotspot("MyDeviceHotspot", "password123")
        pendingHotspotAction = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NEARBY_WIFI_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingHotspotAction == START_ACTION) {
                    proceedWithStartHotspot()
                }
            } else {
                Toast.makeText(this, "Nearby WiFi permission is needed to manage the hotspot on this Android version. Please grant it in app settings to enable this feature.", Toast.LENGTH_LONG).show()
                onHotspotFailed() // Update UI to reflect failure due to permission
                pendingHotspotAction = null
            }
        }
    }

    private fun updateUiForCurrentState() {
        // This function will be called by most listener methods to refresh UI
        if (hotspotManager.isHotspotEnabled()) {
            onHotspotStarted(hotspotManager.currentSsid, hotspotManager.currentPassword, hotspotManager.currentHotspotIp)
        } else {
            // If requiresManualSetupMode is true, onHotspotRequiresManualSetup would have set the specific UI.
            // Otherwise, default to "Off" state.
            if (!requiresManualSetupMode) {
                 onHotspotStopped() // General "off" state
            } else {
                // If it's manual setup mode, onHotspotRequiresManualSetup has already set the text.
                // Just ensure button is enabled.
                toggleButton.isEnabled = true
            }
        }
    }

    // HotspotStateListener implementations
    override fun onHotspotStarting() {
        runOnUiThread {
            requiresManualSetupMode = false // Reset manual setup mode when trying to start
            tvStatus.text = "Status: Starting hotspot..."
            toggleButton.text = "Starting..." // Indicate activity
            toggleButton.isEnabled = false
            tvSsid.text = "SSID: -"
            tvPassword.text = "Password: -"
            tvLoginUrl.text = "Login URL: -"
        }
    }

    override fun onHotspotStarted(ssid: String?, password: String?, ipAddress: String?) {
        runOnUiThread {
            requiresManualSetupMode = false
            tvStatus.text = "Status: Active"
            val cleanSsid = ssid?.removePrefix("LOHS_") // Example of cleaning common prefix
            if (ssid != null && ssid.startsWith("LOHS_")) { // A more direct check for LOHS
                 tvSsid.text = "SSID: $cleanSsid (System Generated)"
            } else {
                 tvSsid.text = "SSID: ${cleanSsid ?: "N/A"}"
            }
            tvPassword.text = "Password: ${password ?: "N/A"}"
            if (ipAddress != null) {
                tvLoginUrl.text = "Login URL: http://$ipAddress:8080/login.html"
            } else {
                tvLoginUrl.text = "Login URL: (IP not available)"
            }
            toggleButton.text = "Stop Hotspot"
            toggleButton.isEnabled = true
        }
    }

    override fun onHotspotStopped() {
        runOnUiThread {
            requiresManualSetupMode = false
            tvStatus.text = "Status: Off"
            tvSsid.text = "SSID: -"
            tvPassword.text = "Password: -"
            tvLoginUrl.text = "Login URL: -"
            toggleButton.text = "Start Hotspot"
            toggleButton.isEnabled = true
        }
    }

    override fun onHotspotFailed() {
        runOnUiThread {
            requiresManualSetupMode = false
            tvStatus.text = "Status: Operation failed. Please check permissions or system settings."
            tvSsid.text = "SSID: -"
            tvPassword.text = "Password: -"
            tvLoginUrl.text = "Login URL: -"
            toggleButton.text = "Start Hotspot"
            toggleButton.isEnabled = true
        }
    }

    override fun onHotspotRequiresManualSetup() {
        runOnUiThread {
            requiresManualSetupMode = true
            tvStatus.text = "Status: Please configure hotspot via System Settings."
            tvSsid.text = "SSID: -"
            tvPassword.text = "Password: -"
            tvLoginUrl.text = "Login URL: -"
            toggleButton.text = "Open Wi-Fi Settings"
            toggleButton.isEnabled = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hotspotManager.setHotspotStateListener(null)
        // Consider stopping hotspot if it was started by the app and is LocalOnlyHotspot
        // if (hotspotManager.isHotspotEnabled() && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        //    hotspotManager.stopHotspot()
        // }
    }
}
