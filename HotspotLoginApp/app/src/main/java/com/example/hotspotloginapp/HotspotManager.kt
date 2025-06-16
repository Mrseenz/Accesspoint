package com.example.hotspotloginapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
// import android.net.Uri // Not used directly if startCaptivePortalApp is commented
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface

class HotspotManager(private val context: Context) {

    private val TAG = "HotspotManager"
    private var currentReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var webServer: AppNanoHttpd? = null

    var currentSsid: String? = null
    var currentPassword: String? = null
    var currentHotspotIp: String? = null

    interface HotspotStateListener {
        fun onHotspotStarting()
        fun onHotspotStarted(ssid: String?, password: String?, ipAddress: String?)
        fun onHotspotStopped()
        fun onHotspotFailed()
        fun onHotspotRequiresManualSetup() // New listener method
    }
    private var stateListener: HotspotStateListener? = null

    fun setHotspotStateListener(listener: HotspotStateListener?) {
        this.stateListener = listener
    }

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private val connectivityManager: ConnectivityManager by lazy {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hotspotNetwork: Network? = null

    init {
        Log.d(TAG, "HotspotManager initialized")
    }

    @SuppressLint("MissingPermission", "NewApi")
    fun startHotspot(ssid: String, pass: String) { // SSID/Pass are mostly for conceptual use here
        Log.d(TAG, "Attempting to start hotspot...")
        stateListener?.onHotspotStarting()
        startNetworkCallback()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+
            Log.w(TAG, "API 30+ (startTethering) code is currently commented out. Invoking manual setup.")
            // Simulate failure to programmatically start, requiring manual setup
            currentSsid = "SystemHotspot (Manual)" // Placeholder
            currentPassword = "N/A (Manual)"       // Placeholder
            currentHotspotIp = getHotspotIpAddress() // Try to get an IP anyway
            // startWebServer() // Don't start server if manual setup is needed first
            stateListener?.onHotspotRequiresManualSetup()
            // openWifiTetheringSettings(context) // Let MainActivity decide to open settings via button

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26-29
            Log.d(TAG, "Using startLocalOnlyHotspot API for Android 8-10")
            if (!isLocationEnabled(context)) {
                Log.w(TAG, "Location services are not enabled. LocalOnlyHotspot might fail or not work.")
                // Proceed, and let onFailed handle it, which calls onHotspotFailed on listener
            }
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                @SuppressLint("HardwareIds")
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    super.onStarted(reservation)
                    currentReservation = reservation
                    val wifiConfig: WifiConfiguration? = reservation.wifiConfiguration

                    currentSsid = wifiConfig?.SSID?.removeSurrounding("\"")
                    currentPassword = wifiConfig?.preSharedKey?.removeSurrounding("\"")
                    currentHotspotIp = getHotspotIpAddress()

                    Log.d(TAG, "LocalOnlyHotspot started. SSID: $currentSsid, Pass: $currentPassword, IP: $currentHotspotIp")
                    startWebServer()
                    stateListener?.onHotspotStarted(currentSsid, currentPassword, currentHotspotIp)
                }
                override fun onStopped() {
                    super.onStopped()
                    Log.d(TAG, "LocalOnlyHotspot stopped.")
                    clearHotspotDetailsAndStopServices()
                    stateListener?.onHotspotStopped()
                }
                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    Log.e(TAG, "LocalOnlyHotspot failed, reason: $reason")
                    clearHotspotDetailsAndStopServices()
                    stateListener?.onHotspotFailed()
                    // openWifiTetheringSettings(context) // Let MainActivity decide
                }
            }, Handler(Looper.getMainLooper()))
        } else { // API < 26
            Log.w(TAG, "Programmatic hotspot creation not supported on API < 26.")
            clearHotspotDetailsAndStopServices() // Ensure cleanup
            stateListener?.onHotspotRequiresManualSetup()
            // openOldWifiSettings(context) // Let MainActivity decide
        }
    }

    private fun clearHotspotDetailsAndStopServices() {
        currentSsid = null
        currentPassword = null
        currentHotspotIp = null
        currentReservation = null
        stopWebServer()
        stopNetworkCallback()
    }

    @SuppressLint("MissingPermission", "NewApi")
    fun stopHotspot() {
        Log.d(TAG, "Attempting to stop hotspot")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+
            Log.w(TAG, "API 30+ (stopTethering) code is commented out. Simulating stop.")
            // ... (original commented out stopTethering logic) ...
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26-29
            Log.d(TAG, "Closing LocalOnlyHotspot reservation (API 26-29)")
            currentReservation?.close() // This should trigger its onStopped callback
        } else { // API < 26
            Log.w(TAG, "Programmatic hotspot stop not applicable.")
        }
        // General cleanup, some parts might be redundant if callbacks fire correctly
        // Call this directly as callbacks might not fire if hotspot wasn't fully started or in error states
        clearHotspotDetailsAndStopServices()
        stateListener?.onHotspotStopped()
    }

    fun isHotspotEnabled(): Boolean {
        val enabled = webServer?.isAlive == true && currentSsid != null
        Log.d(TAG, "isHotspotEnabled called, returning: $enabled")
        return enabled
    }

    // ... (startNetworkCallback, stopNetworkCallback, reportNetworkValidated, getHotspotIpAddress, isLocationEnabled - mostly unchanged)
    // Ensure reportNetworkConnectivity and startCaptivePortalApp calls remain commented out in startNetworkCallback
    @SuppressLint("MissingPermission", "NewApi")
    private fun startNetworkCallback() {
        if (networkCallback != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "Network available via callback: $network")
                // ... (existing heuristic logic, ensuring calls to reportNetworkConnectivity/startCaptivePortalApp are commented)
                val caps = connectivityManager.getNetworkCapabilities(network)
                val linkProps = connectivityManager.getLinkProperties(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                    linkProps?.interfaceName?.contains("ap") == true) {
                    if (hotspotNetwork == null) {
                        hotspotNetwork = network
                        val newIp = getHotspotIpAddress() // Get current IP
                        if (newIp != null && newIp != currentHotspotIp) { // IP might have been found after LOHS started
                            currentHotspotIp = newIp
                            // If hotspot is already considered active, re-notify with IP.
                            if (stateListener != null && currentSsid != null) {
                                 Log.i(TAG, "Hotspot network IP updated: $currentHotspotIp. Re-notifying listener.")
                                 stateListener?.onHotspotStarted(currentSsid, currentPassword, currentHotspotIp)
                            }
                        }
                        Log.w(TAG, "reportNetworkConnectivity(false) call is commented out in NetworkCallback.")
                        Log.w(TAG, "startCaptivePortalApp call is commented out in NetworkCallback.")
                    }
                }
            }
            override fun onLost(network: Network) {
                super.onLost(network)
                if (network == hotspotNetwork) {
                    Log.i(TAG, "Monitored hotspot network lost: $network")
                    hotspotNetwork = null
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        try { connectivityManager.registerNetworkCallback(request, networkCallback!!) }
        catch (e: Exception) { Log.e(TAG, "NetCallback reg error: ${e.message}"); networkCallback = null }
    }

    private fun stopNetworkCallback() {
        if (networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback!!) }
            catch (e: Exception) { Log.e(TAG, "NetCallback unreg error: ${e.message}") }
            finally { networkCallback = null; hotspotNetwork = null }
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    fun reportNetworkValidated() {
        if (hotspotNetwork != null) {
            Log.i(TAG, "Reporting network validated for $hotspotNetwork")
            Log.w(TAG, "reportNetworkConnectivity(true) call is commented out.")
        } else { Log.w(TAG, "No hotspot network to report validated for.") }
    }

    private fun getHotspotIpAddress(): String? {
        try {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (!iface.isLoopback && iface.isUp &&
                    (iface.name.contains("ap") || iface.name.contains("wlan") || iface.name.contains("hotspot"))) {
                    for (addr in iface.inetAddresses) {
                        if (addr is Inet4Address) {
                            Log.d(TAG, "Found IP ${addr.hostAddress} on interface ${iface.name}")
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error getting hotspot IP: ${e.message}") }
        Log.w(TAG, "Could not determine Hotspot IP.")
        return null
    }

    private fun isLocationEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager?)
                ?.isLocationEnabled ?: false
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    fun openWifiTetheringSettings() { // Made public for MainActivity
        Log.d(TAG, "Opening Wi-Fi Tethering Settings panel requested.")
        // ... (implementation from previous step, ensuring calls to problematic Settings constants are commented out)
        // For now, always falls back to openOldWifiSettings due to compile issues with newer Settings actions
        openOldWifiSettings(context)
    }

    private fun openOldWifiSettings(context: Context) {
        Log.d(TAG, "Opening older Wireless Settings panel.")
        try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) { Log.e(TAG, "Failed to open Wireless settings: ${e.message}") }
    }

    private fun startWebServer() {
        if (webServer == null) {
            try {
                webServer = AppNanoHttpd(context, 8080, object : AppNanoHttpd.AuthCallback {
                    override fun onAuthSuccess() { reportNetworkValidated() }
                })
                webServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                Log.i(TAG, "NanoHTTPD web server started.")
            } catch (e: IOException) { Log.e(TAG, "IOException starting NanoHTTPD", e); webServer = null }
            catch (e: Exception) { Log.e(TAG, "Exception starting NanoHTTPD", e); webServer = null }
        } else { Log.d(TAG, "Web server already running.") }
    }

    private fun stopWebServer() {
        webServer?.stop()
        webServer = null
        Log.i(TAG, "NanoHTTPD web server stopped.")
    }
}
