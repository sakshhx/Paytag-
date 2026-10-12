package com.example.paytagplus

import android.content.Intent
import android.os.Bundle
import android.widget.*
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : BaseActivity() {

    private lateinit var btnLogout: Button
    private lateinit var rowReportFraud: LinearLayout
    private lateinit var rowAbout: LinearLayout
    private lateinit var rowPrivacy: LinearLayout
    private lateinit var userName: TextView
    private lateinit var userUpi: TextView
    private lateinit var userPhone: TextView
    private lateinit var profileImage: ImageView
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        mAuth = FirebaseAuth.getInstance()

        btnLogout      = findViewById(R.id.btnLogout)
        rowReportFraud = findViewById(R.id.rowReportFraud)
        rowAbout       = findViewById(R.id.rowAbout)
        rowPrivacy     = findViewById(R.id.rowPrivacy)
        userName       = findViewById(R.id.userName)
        userUpi        = findViewById(R.id.userUpi)
        userPhone      = findViewById(R.id.userPhone)
        profileImage   = findViewById(R.id.profileImage)
        bottomNav      = findViewById(R.id.bottomnav)

        val user = mAuth.currentUser
        if (user != null) {
            userName.text  = user.displayName ?: "PayTag User"
            userUpi.text   = user.email ?: "No Email"
            userPhone.text = user.phoneNumber ?: "Phone not linked"
            user.photoUrl?.let { Glide.with(this).load(it).into(profileImage) }
        }

        bottomNav.selectedItemId = R.id.Profile

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.Home -> { startActivity(Intent(this, HomeActivity::class.java)); true }
                R.id.Profile -> true
                R.id.about -> { startActivity(Intent(this, AboutHelpActivity::class.java)); true }
                else -> false
            }
        }

        rowReportFraud.setOnClickListener {
            Toast.makeText(this, "🚨 Report Fraud clicked!", Toast.LENGTH_SHORT).show()
        }

        // ── REPLACED: was AlertDialog.Builder ──
        rowAbout.setOnClickListener {
            showCustomDialog(
                title        = "About",
                message      = "PayTag+ helps users safely verify UPI QR codes and prevent fraud.",
                positiveText = "OK",
                negativeText = "Close"
            )
        }

        // ── REPLACED: was AlertDialog.Builder ──
        rowPrivacy.setOnClickListener {
            showCustomDialog(
                title        = "Privacy Policy",
                message      = "We respect your privacy. No data is shared without consent.",
                positiveText = "OK",
                negativeText = "Close"
            )
        }

        btnLogout.setOnClickListener { showLogoutDialog() }
    }

    // ── REPLACED: was AlertDialog.Builder ──
    private fun showLogoutDialog() {
        showCustomDialog(
            title        = "Logout",
            message      = "Are you sure you want to logout?",
            positiveText = "Yes",
            negativeText = "Cancel",
            onPositive   = {
                mAuth.signOut()
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
        )
    }
}