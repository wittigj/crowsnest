package com.wittighouse.crowsnest

import android.Manifest
import android.app.AppOpsManager
import android.bluetooth.BluetoothManager
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
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import androidx.viewpager2.widget.ViewPager2
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private var hudFragment: QuarterdeckHUDFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hudFragment = QuarterdeckHUDFragment()
        
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        viewPager.adapter = HUDAdapter(this, hudFragment!!)
        tabLayout.setupWithViewPager(viewPager)

        // Show HUD first (Page 0)
        viewPager.currentItem = 0
    }

    override fun onResume() {
        super.onResume()
        hudFragment?.refreshHUD()
    }

    // ── Status/Settings Fragment (Page 1) ──
    inner class HUDAdapter(fa: FragmentActivity, val hud: QuarterdeckHUDFragment) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> hud
                1 -> StatusSettingsFragment()
                else -> hud
            }
        }

        fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                0 -> "Quarterdeck"
                1 -> "Settings"
                else -> ""
            }
        }
    }
}
