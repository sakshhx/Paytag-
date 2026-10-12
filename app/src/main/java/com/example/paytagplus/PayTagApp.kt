package com.example.paytagplus

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class PayTagApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_theme", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else        AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}