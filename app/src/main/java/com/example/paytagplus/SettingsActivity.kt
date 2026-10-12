package com.example.paytagplus

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        setupAppLock()
        setupNotifications()
        setupSettingsCards()
    }

    private fun setupAppLock() {
        val switchAppLock = findViewById<Switch>(R.id.switchAppLock)
        switchAppLock.isChecked = prefs.getBoolean("biometric_enabled", false)

        switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean("biometric_enabled", isChecked)
                .putBoolean("home_unlocked", false)
                .apply()

            Toast.makeText(
                this,
                if (isChecked) "App Lock Enabled 🔒" else "App Lock Disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupNotifications() {
        val switchNotif = findViewById<Switch>(R.id.switchNotifications)
        switchNotif.isChecked = prefs.getBoolean("notifications_enabled", true)

        switchNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean("notifications_enabled", isChecked)
                .apply()

            Toast.makeText(
                this,
                if (isChecked) "Fraud alerts enabled 🔔" else "Fraud alerts disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateThemeUI(isDark: Boolean, icon: ImageView, sub: TextView) {
        if (isDark) {
            icon.setImageResource(R.drawable.ic_moon)
            sub.text = "Currently: Dark"
        } else {
            icon.setImageResource(R.drawable.ic_sun)
            sub.text = "Currently: Light"
        }
    }

    private fun setupSettingsCards() {
        val rowPrivacy = findViewById<View>(R.id.rowPrivacy)
        rowPrivacy.findViewById<ImageView>(R.id.icon).apply {
            setImageResource(R.drawable.privacy)
            setColorFilter(Color.parseColor("#111827"))
        }
        rowPrivacy.findViewById<TextView>(R.id.title).text = "Privacy Policy"
        rowPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        val rowAbout = findViewById<View>(R.id.rowAbout)
        rowAbout.findViewById<ImageView>(R.id.icon).apply {
            setImageResource(R.drawable.ic_info)
            setColorFilter(Color.parseColor("#111827"))
        }
        rowAbout.findViewById<TextView>(R.id.title).text = "About & Help"
        rowAbout.setOnClickListener {
            startActivity(Intent(this, AboutHelpActivity::class.java))
        }

        val rowLogout = findViewById<View>(R.id.rowLogout)
        rowLogout.findViewById<ImageView>(R.id.icon).apply {
            setImageResource(R.drawable.exit)
            setColorFilter(Color.parseColor("#EF4444"))
        }
        rowLogout.findViewById<TextView>(R.id.title).apply {
            text = "Logout"
            setTextColor(Color.parseColor("#EF4444"))
        }
        rowLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showLogoutDialog() {
        val auth = FirebaseAuth.getInstance()
        showCustomDialog(
            title = "Logout",
            message = "Are you sure you want to logout?",
            positiveText = "Yes",
            negativeText = "Cancel",
            positiveColor = "#FFFFFF",
            onPositive = {
                auth.signOut()
                prefs.edit().clear().apply()
                startActivity(Intent(this, WelcomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        )
    }
}