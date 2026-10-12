package com.example.paytagplus

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView

class AwarenessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Paste this at the TOP of every onCreate, before setContentView
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.getBoolean("dark_theme", true)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        setContentView(R.layout.activity_awareness)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        mapOf(
            R.id.cardUpiScams  to "Common UPI Scams",
            R.id.cardStaySafe  to "How to Stay Safe",
            R.id.cardScammed   to "What to Do If Scammed",
            R.id.cardFakeQr    to "Fake QR Code Tricks",
            R.id.cardChecklist to "Safe Payment Checklist"
        ).forEach { (id, topic) ->
            findViewById<CardView>(id).setOnClickListener {
                startActivity(
                    Intent(this, AwarenessDetailActivity::class.java)
                        .putExtra("topic", topic)
                )
            }
        }
    }
}