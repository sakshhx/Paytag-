package com.example.paytagplus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import android.content.res.ColorStateList


class HomeActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var btnScanQR: Button
    private lateinit var btnContinue: Button
    private lateinit var etUpi: EditText
    private lateinit var btnProfileMenu: TextView
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    companion object {
        private const val NOTIF_PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        setupViews()
        setupDrawer()
        setupBiometric()
        wireBottomButtons()
        ensureUserExists()
        requestNotificationPermission()
        createNotificationChannel()
        saveFcmToken()
        listenForFraudAlerts()
    }

    override fun onResume() {
        super.onResume()

        // ── Clear UPI field on return ──
        if (::etUpi.isInitialized) {
            etUpi.text.clear()
            btnContinue.visibility = View.GONE
        }

        // ── Biometric check ──
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("biometric_enabled", false) &&
            !prefs.getBoolean("home_unlocked", false) &&
            ::biometricPrompt.isInitialized
        ) {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            if (prefs.getBoolean("biometric_enabled", false)) {
                prefs.edit().putBoolean("home_unlocked", false).apply()
            }
        }
    }

    // ── User document ─────────────────────────────────────
    private fun ensureUserExists() {
        val uid   = auth.currentUser?.uid ?: return
        val name  = auth.currentUser?.displayName ?: "User"
        val email = auth.currentUser?.email ?: ""

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(
                hashMapOf("uid" to uid, "name" to name, "email" to email),
                com.google.firebase.firestore.SetOptions.merge()
            )
    }

    // ── FCM Token ─────────────────────────────────────────
    private fun saveFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update("fcm_token", token)
                .addOnFailureListener {
                    FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .set(mapOf("fcm_token" to token),
                            com.google.firebase.firestore.SetOptions.merge())
                }
        }
    }

    // ── Notification permission ───────────────────────────
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIF_PERMISSION_CODE
                )
            }
        }
    }

    // ── Notification channel ──────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "fraud_alerts", "Fraud Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when trusted friend's UPI is reported as fraud"
                enableVibration(true)
                enableLights(true)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // ── Real-time fraud alert listener ────────────────────
    private fun listenForFraudAlerts() {
        val uid = auth.currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("alerts")
            .whereEqualTo("seen", false)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                val first   = snapshots.documents[0]
                val upiId   = first.getString("upi_id") ?: return@addSnapshotListener
                val message = first.getString("message") ?: return@addSnapshotListener
                val alertId = first.id

                // ── Check notification setting ──
                val notifEnabled = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getBoolean("notifications_enabled", true)

                showFraudAlertDialog(upiId, message, alertId, uid)
                if (notifEnabled) showLocalNotification("⚠️ Fraud Alert!", message, upiId)
            }
    }

    private fun showLocalNotification(title: String, message: String, upiId: String) {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("upi_id", upiId)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = androidx.core.app.NotificationCompat
            .Builder(this, "fraud_alerts")
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showFraudAlertDialog(upiId: String, message: String, alertId: String, uid: String) {
        showCustomDialog(
            title = "⚠️ Fraud Alert!",
            message = "$message\n\nWould you like to report this UPI as fraud too?",
            positiveText = "Yes, Report",
            negativeText = "Dismiss",
            positiveColor = "#FFFFFF",
            onPositive = {
                markAlertSeen(alertId, uid)
                startActivity(Intent(this, ReportFraudActivity::class.java)
                    .putExtra("upi_id", upiId))
            },
            onNegative = {
                markAlertSeen(alertId, uid)
            }
        )
    }

    private fun markAlertSeen(alertId: String, uid: String) {
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("alerts").document(alertId)
            .update("seen", true)
    }

    // ── Bottom buttons ────────────────────────────────────
    private fun wireBottomButtons() {
        findViewById<LinearLayout>(R.id.layoutHistory)?.setOnClickListener {
            startActivity(Intent(this, ScanHistoryActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.layoutFriends)?.setOnClickListener {
            startActivity(Intent(this, TrustedFriendsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.layoutShare)?.setOnClickListener {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT,
                        "Check out PayTag++ — UPI fraud detection app! Stay safe from scams.")
                }, "Share PayTag++"
            ))
        }
    }

    // ── Biometric ─────────────────────────────────────────
    private fun setupBiometric() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) != BiometricManager.BIOMETRIC_SUCCESS
        ) return

        biometricPrompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit().putBoolean("home_unlocked", true).apply()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finish()
                }
            }
        )

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock PayTag++")
            .setSubtitle("Verify to continue")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    }

    // ── Views ─────────────────────────────────────────────
    private fun setupViews() {
        drawerLayout   = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        btnScanQR      = findViewById(R.id.btnScanQR)
        btnContinue    = findViewById(R.id.btnContinue)
        etUpi          = findViewById(R.id.etUpi)
        btnProfileMenu = findViewById(R.id.btnProfileMenu)

        val whiteStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val whiteColors = intArrayOf(Color.WHITE, Color.WHITE)
        val whiteList   = ColorStateList(whiteStates, whiteColors)
        navigationView.itemTextColor    = whiteList
        navigationView.itemIconTintList = null


        navigationView.post {
            navigationView.menu.findItem(R.id.menu_logout)
                ?.icon?.setTint(ContextCompat.getColor(this, R.color.settings_danger))
        }


        btnProfileMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        btnScanQR.setOnClickListener {
            if (!isConnected()) {
                Toast.makeText(this, "No Internet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, QrScannerActivity::class.java))
        }

        btnContinue.setOnClickListener {
            val upi = etUpi.text.toString().trim()
            if (!isValidUpiOrPhone(upi)) {
                Toast.makeText(this, "Invalid UPI ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, ScannedUpiActivity::class.java).apply {
                putExtra("upi_id", upi)
                putExtra("payee_name", "")
            })
        }

        etUpi.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                btnContinue.visibility =
                    if (isValidUpiOrPhone(s.toString())) View.VISIBLE else View.GONE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // ── Drawer ────────────────────────────────────────────
    private fun setupDrawer() {
        val header = navigationView.getHeaderView(0)
        val name   = auth.currentUser?.displayName ?: "User"
        val letter = name.first().uppercaseChar().toString()

        header.findViewById<TextView>(R.id.tvUserName).text     = "Welcome $name"
        header.findViewById<TextView>(R.id.tvProfileLetter).text = letter
        btnProfileMenu.text = letter

        navigationView.menu.findItem(R.id.menu_logout)?.icon?.setTint(Color.RED)

        navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.menu_home     -> drawerLayout.closeDrawer(GravityCompat.START)
                R.id.menu_history  -> startActivity(Intent(this, ScanHistoryActivity::class.java))
                R.id.menu_friends  -> startActivity(Intent(this, TrustedFriendsActivity::class.java))
                R.id.menu_report   -> startActivity(Intent(this, ReportListActivity::class.java))
                R.id.menu_tips     -> startActivity(Intent(this, AwarenessActivity::class.java))
                R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.menu_about -> startActivity(Intent(this, AboutHelpActivity::class.java))

                R.id.menu_logout   -> showLogoutDialog()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    // ── Helpers ───────────────────────────────────────────
    private fun isValidUpiOrPhone(input: String): Boolean =
        input.matches("^[\\w.-]+@[\\w.-]+$".toRegex()) ||
                input.matches("^[6-9]\\d{9}$".toRegex())

    private fun isConnected(): Boolean =
        (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
            .activeNetworkInfo?.isConnected == true

    private fun showLogoutDialog() {
        showCustomDialog(
            title = "Logout",
            message = "Are you sure you want to logout?",
            positiveText = "Yes",
            negativeText = "Cancel",
            positiveColor = "#FFFFFF",
            onPositive = {
                auth.signOut()
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit().clear().apply()
                startActivity(Intent(this, WelcomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        )
    }
}