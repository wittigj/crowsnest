package com.wittighouse.crowsnest

import android.Manifest
import android.app.AppOpsManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class StatusSettingsFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var etApiUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etQuarterdeckUrl: EditText
    private lateinit var etQuarterdeckKey: EditText
    private lateinit var etInterval: EditText
    private lateinit var etStatusMessage: EditText
    private lateinit var btnSave: CardView
    private lateinit var btnStart: CardView
    private lateinit var btnStop: CardView
    private lateinit var btnPermissions: CardView
    private lateinit var statusDot: View
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvLastUpload: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStatus = view.findViewById(R.id.tvStatus)
        etApiUrl = view.findViewById(R.id.etApiUrl)
        etApiKey = view.findViewById(R.id.etApiKey)
        etQuarterdeckUrl = view.findViewById(R.id.etQuarterdeckUrl)
        etQuarterdeckKey = view.findViewById(R.id.etQuarterdeckKey)
        etInterval = view.findViewById(R.id.etInterval)
        etStatusMessage = view.findViewById(R.id.etStatusMessage)
        btnSave = view.findViewById(R.id.btnSave)
        btnStart = view.findViewById(R.id.btnStart)
        btnStop = view.findViewById(R.id.btnStop)
        btnPermissions = view.findViewById(R.id.btnPermissions)
        statusDot = view.findViewById(R.id.statusDot)
        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        tvLastUpload = view.findViewById(R.id.tvLastUpload)

        loadConfig()
        updateStatus()

        btnSave.setOnClickListener { saveConfig() }
        btnStart.setOnClickListener { startService() }
        btnStop.setOnClickListener { stopService() }
        btnPermissions.setOnClickListener { requestAllPermissions() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun loadConfig() {
        val prefs = requireContext().getSharedPreferences("config", Context.MODE_PRIVATE)
        etApiUrl.setText(prefs.getString("api_url", "https://quarterdeck.wittighouse.com/api"))
        etApiKey.setText(prefs.getString("api_key", ""))
        etQuarterdeckUrl.setText(prefs.getString("quarterdeck_url", "https://quarterdeck.wittighouse.com/api"))
        etQuarterdeckKey.setText(prefs.getString("quarterdeck_key", ""))
        etInterval.setText(prefs.getInt("interval", 60).toString())
        etStatusMessage.setText(prefs.getString("status_message", ""))
    }

    private fun saveConfig() {
        val prefs = requireContext().getSharedPreferences("config", Context.MODE_PRIVATE)
        var interval = etInterval.text.toString().toIntOrNull() ?: 60
        if (interval < 5) { interval = 5; etInterval.setText("5") }
        prefs.edit().apply {
            putString("api_url", etApiUrl.text.toString())
            putString("api_key", etApiKey.text.toString())
            putString("quarterdeck_url", etQuarterdeckUrl.text.toString())
            putString("quarterdeck_key", etQuarterdeckKey.text.toString())
            putInt("interval", interval)
            putString("status_message", etStatusMessage.text.toString())
            apply()
        }
        Toast.makeText(requireContext(), "✓ Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun startService() {
        saveConfig()
        val intent = Intent(requireContext(), StatusService::class.java)
        ContextCompat.startForegroundService(requireContext(), intent)
        Toast.makeText(requireContext(), "✓ Service started", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun stopService() {
        val intent = Intent(requireContext(), StatusService::class.java)
        requireContext().stopService(intent)
        Toast.makeText(requireContext(), "✓ Service stopped", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun updateStatus() {
        if (StatusService.isRunning) {
            statusDot.setBackgroundResource(R.drawable.status_dot_online)
            tvServiceStatus.text = "Service Running"
            tvServiceStatus.setTextColor(0xFF10B981.toInt())
            tvLastUpload.text = StatusService.lastUploadResult.ifEmpty { "Waiting..." }
        } else {
            statusDot.setBackgroundResource(R.drawable.status_dot_offline)
            tvServiceStatus.text = "Service Stopped"
            tvServiceStatus.setTextColor(0xFF6B7280.toInt())
            tvLastUpload.text = "Tap Start to begin"
        }

        val sb = StringBuilder()
        sb.appendLine("Permissions")
        sb.appendLine("  Location: ${if (checkLocationPermission()) "✓" else "✗"}")
        sb.appendLine("  Usage Stats: ${if (checkUsageStatsPermission()) "✓" else "✗"}")
        sb.appendLine("  Notifications: ${if (checkNotificationPermission()) "✓" else "✗"}")
        sb.appendLine("  Bluetooth: ${if (checkBluetoothPermission()) "✓" else "✗"}")
        sb.appendLine()
        sb.appendLine("Live Data")
        sb.appendLine("  Battery: ${getBatteryLevel()}% ${if (isCharging()) "⚡" else ""}")
        sb.appendLine("  Temp: ${getBatteryTemperature()}°C")
        sb.appendLine("  WiFi: ${if (isWifiConnected()) "✓ Connected" else "✗ Disconnected"}")
        sb.appendLine("  Bluetooth: ${if (isBluetoothEnabled()) "✓ On" else "✗ Off"}")
        sb.appendLine("  App: ${StatusCollector.getCurrentApp(requireContext())}")
        tvStatus.text = sb.toString()
    }

    private fun requestAllPermissions() {
        if (!checkLocationPermission()) {
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !checkBluetoothPermission()) {
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 103)
            }
        }
        if (!checkUsageStatsPermission()) {
            Toast.makeText(requireContext(), "Grant Usage Access, then come back", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        if (!checkNotificationPermission()) {
            Toast.makeText(requireContext(), "Grant Notification Access for music detection", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun checkLocationPermission() = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun checkBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
    }
    private fun checkUsageStatsPermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), requireContext().packageName) == AppOpsManager.MODE_ALLOWED
    }
    private fun checkNotificationPermission(): Boolean {
        val listeners = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners")
        return listeners?.contains(requireContext().packageName) == true
    }
    private fun getBatteryLevel(): Int {
        val bm = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    private fun isCharging(): Boolean {
        val bm = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }
    private fun getBatteryTemperature(): Float {
        val intent = requireContext().registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    }
    private fun isWifiConnected(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    private fun isBluetoothEnabled(): Boolean {
        return try {
            val bm = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bm.adapter?.isEnabled == true
        } catch (e: SecurityException) { false }
    }
}
