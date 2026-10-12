package com.example.paytagplus

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ScannedUpiActivity : BaseActivity() {

    private var currentUpiId = ""
    private var currentPayeeName = ""
    private var currentRiskScore = 0
    private var currentRiskLabel = ""

    private lateinit var tvRiskPercent: TextView
    private lateinit var tvRiskLabel: TextView
    private lateinit var tvAlert: TextView
    private lateinit var riskProgress: ProgressBar
    private lateinit var tvAlreadyReported: TextView
    private lateinit var btnReportFraud: LinearLayout
    private lateinit var tvReportFraudText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanned_upi)

        val tvUpi = findViewById<TextView>(R.id.tvUpi)
        val tvPayeeName = findViewById<TextView>(R.id.tvPayeeName)
        tvRiskPercent = findViewById(R.id.tvRiskPercent)
        tvRiskLabel = findViewById(R.id.tvRiskLabel)
        tvAlert = findViewById(R.id.tvAlert)
        riskProgress = findViewById(R.id.riskProgress)
        tvAlreadyReported = findViewById(R.id.tvAlreadyReported)
        btnReportFraud = findViewById(R.id.btnReportFraud)
        tvReportFraudText = findViewById(R.id.tvReportFraudText)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnAddTrusted = findViewById<LinearLayout>(R.id.btnAddTrusted)
        val btnCopyUpi = findViewById<ImageView>(R.id.btnCopyUpi)
        val btnWhyRisky = findViewById<LinearLayout>(R.id.btnWhyRisky)
        val btnContinue = findViewById<Button>(R.id.btnRegister)

        btnBack.setOnClickListener { finish() }

        val upiId = intent.getStringExtra("upi_id") ?: "No UPI Found"

        var payeeName = intent.getStringExtra("payee_name")
        if (payeeName.isNullOrEmpty()) {
            payeeName = if (Regex("^[6-9]\\d{9}$").matches(upiId)) {
                "Mobile User"
            } else {
                upiId.substringBefore("@")
                    .replace(".", " ")
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
            }
        }

        currentUpiId = upiId
        currentPayeeName = payeeName ?: ""

        tvUpi.text = upiId
        tvPayeeName.text = currentPayeeName

        tvRiskPercent.text = "Checking..."
        tvRiskLabel.text = "Analyzing"
        tvAlert.text = "Fetching fraud reports..."
        tvAlreadyReported.visibility = View.GONE

        btnCopyUpi.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("UPI ID", currentUpiId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "UPI ID copied! ✅", Toast.LENGTH_SHORT).show()

            btnCopyUpi.setColorFilter(Color.parseColor("#16A34A"))
            btnCopyUpi.postDelayed({
                btnCopyUpi.setColorFilter(Color.WHITE)
            }, 1500)
        }

        btnWhyRisky.setOnClickListener {
            showWhyRiskyDialog()
        }

        btnContinue.setOnClickListener {
            openUpiPayment(currentUpiId, currentPayeeName)
        }

        btnReportFraud.setOnClickListener {
            if (btnReportFraud.isClickable) {
                val intent = Intent(this, ReportFraudActivity::class.java)
                intent.putExtra("upi_id", upiId)
                startActivity(intent)
            }
        }

        btnAddTrusted.setOnClickListener {
            showAddTrustedDialog(currentUpiId, currentPayeeName)
        }

        fetchReportCountAndShow(upiId, currentPayeeName)
    }

    private fun openUpiPayment(upiId: String, payeeName: String) {
        if (currentRiskScore > 60) {
            showCustomDialog(
                title = "⚠️ High Risk UPI!",
                message = "This UPI has a risk score of $currentRiskScore% ($currentRiskLabel).\n\nAre you sure you want to proceed with payment?",
                positiveText = "Yes, Proceed",
                negativeText = "Cancel",
                positiveColor = "#B00020",
                onPositive = { launchUpiIntent(upiId, payeeName) }
            )
        } else {
            launchUpiIntent(upiId, payeeName)
        }
    }

    private fun launchUpiIntent(upiId: String, payeeName: String) {
        val upiUri = Uri.parse("upi://pay?pa=$upiId&pn=${Uri.encode(payeeName)}&cu=INR")
        val upiIntent = Intent(Intent.ACTION_VIEW, upiUri)
        val resolvedApps = packageManager.queryIntentActivities(upiIntent, 0)

        if (resolvedApps.isNotEmpty()) {
            startActivity(Intent.createChooser(upiIntent, "Pay using"))
        } else {
            showInstallGPayDialog()
        }
    }

    private fun showInstallGPayDialog() {
        showCustomDialog(
            title = "No UPI App Found",
            message = "No UPI payment app is installed. Would you like to install Google Pay?",
            positiveText = "Install GPay",
            negativeText = "Cancel",
            onPositive = {
                startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.nbu.paisa.user")
                    }
                )
            }
        )
    }

    private fun showWhyRiskyDialog() {
        val reasons = RiskEngine.getRiskBreakdown(currentUpiId)
        val message = buildString {
            append("Risk Score: $currentRiskScore% — $currentRiskLabel\n\n")
            append("Reasons:\n")
            reasons.forEach { append("$it\n") }
        }

        showCustomDialog(
            title = "Why is this risky?",
            message = message,
            positiveText = "Got it",
            negativeText = "Close"
        )
    }

    private fun fetchReportCountAndShow(upiId: String, payeeName: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val docId = upiId.replace("@", "_").replace(".", "_")
        val db = FirebaseFirestore.getInstance()

        val reportRef = db.collection("fraud_reports").document(docId)
        val flagRef = db.collection("flagged_upis").document(docId)

        reportRef.get().addOnSuccessListener { reportDoc ->
            val reportCount = if (reportDoc.exists()) {
                (reportDoc.getLong("report_count") ?: 0).toInt()
            } else 0

            flagRef.get().addOnSuccessListener { flagDoc ->
                val severity = if (flagDoc.exists()) {
                    flagDoc.getString("severity") ?: "none"
                } else "none"

                currentRiskScore = RiskEngine.calculateRiskScore(upiId, reportCount, severity)
                currentRiskLabel = RiskEngine.getRiskLabel(currentRiskScore)

                updateRiskUI(currentRiskScore, currentRiskLabel)
                saveScanToHistory(upiId, payeeName, currentRiskScore, currentRiskLabel)

                if (uid.isNotEmpty() && reportDoc.exists()) {
                    db.collection("fraud_reports")
                        .document(docId)
                        .collection("reports")
                        .whereEqualTo("reported_by", uid)
                        .get()
                        .addOnSuccessListener { myReports ->
                            if (!myReports.isEmpty) {
                                tvAlreadyReported.visibility = View.VISIBLE
                                tvAlreadyReported.text = "⚠ You have already reported this UPI"
                                tvReportFraudText.text = "Reported ✓"
                                btnReportFraud.alpha = 0.75f
                                btnReportFraud.isClickable = false
                                btnReportFraud.isFocusable = false
                            } else {
                                tvAlreadyReported.visibility = View.GONE
                                tvReportFraudText.text = "Report Fraud"
                                btnReportFraud.alpha = 1.0f
                                btnReportFraud.isClickable = true
                                btnReportFraud.isFocusable = true
                            }
                        }
                }

                updateTrustedFriendRiskScore(upiId, currentRiskScore, currentRiskLabel)

            }.addOnFailureListener {
                currentRiskScore = RiskEngine.calculateRiskScore(upiId, reportCount, "none")
                currentRiskLabel = RiskEngine.getRiskLabel(currentRiskScore)
                updateRiskUI(currentRiskScore, currentRiskLabel)
                saveScanToHistory(upiId, payeeName, currentRiskScore, currentRiskLabel)
            }
        }.addOnFailureListener {
            currentRiskScore = RiskEngine.calculateRiskScore(upiId, 0, "none")
            currentRiskLabel = RiskEngine.getRiskLabel(currentRiskScore)
            updateRiskUI(currentRiskScore, currentRiskLabel)
            saveScanToHistory(upiId, payeeName, currentRiskScore, currentRiskLabel)
        }
    }

    private fun updateTrustedFriendRiskScore(
        upiId: String,
        riskScore: Int,
        riskLabel: String
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val docId = upiId.replace("@", "_").replace(".", "_")

        db.collection("users")
            .document(uid)
            .collection("trusted_friends")
            .document(docId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    db.collection("users")
                        .document(uid)
                        .collection("trusted_friends")
                        .document(docId)
                        .update(
                            mapOf(
                                "risk_score" to riskScore.toString(),
                                "risk_label" to riskLabel
                            )
                        )
                }
            }
    }

    private fun showAddTrustedDialog(upiId: String, payeeName: String) {
        showCustomDialog(
            title = "Add Trusted Friend",
            message = "Add $payeeName ($upiId) to your trusted friends?",
            positiveText = "Add",
            negativeText = "Cancel",
            positiveColor = "#FFFFFF",
            onPositive = { saveTrustedFriend(upiId, payeeName) }
        )
    }

    private fun saveTrustedFriend(upiId: String, payeeName: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val docId = upiId.replace("@", "_").replace(".", "_")

        db.collection("users")
            .document(uid)
            .collection("trusted_friends")
            .document(docId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Toast.makeText(
                        this,
                        "$payeeName is already in your trusted friends!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val friend = hashMapOf(
                        "name" to payeeName,
                        "upi_id" to upiId,
                        "risk_label" to currentRiskLabel,
                        "risk_score" to currentRiskScore.toString()
                    )

                    db.collection("users")
                        .document(uid)
                        .collection("trusted_friends")
                        .document(docId)
                        .set(friend)
                        .addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "$payeeName added to trusted friends! ✅",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to add ❌", Toast.LENGTH_SHORT).show()
                        }
                }
            }
    }

    private fun saveScanToHistory(
        upiId: String,
        payeeName: String,
        riskScore: Int,
        riskLabel: String
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        val sdf = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")

        val scan = hashMapOf(
            "upi_id" to upiId,
            "payee_name" to payeeName,
            "risk_score" to riskScore.toString(),
            "risk_label" to riskLabel,
            "timestamp" to sdf.format(Date()),
            "created_at" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(uid)
            .collection("scan_history")
            .add(scan)
    }

    private fun updateRiskUI(score: Int, label: String) {
        riskProgress.progress = score
        tvRiskPercent.text = "$score% Risk"
        tvRiskLabel.text = label

        when {
            score <= 15 -> {
                tvRiskLabel.setBackgroundResource(R.drawable.bg_risk_low)
                tvRiskPercent.setTextColor(Color.parseColor("#16A34A"))
                tvAlert.text = "✅ This UPI appears safe to use"
                tvAlert.setBackgroundResource(R.drawable.bg_alert_safe)
            }

            score <= 35 -> {
                tvRiskLabel.setBackgroundResource(R.drawable.bg_risk_low)
                tvRiskPercent.setTextColor(Color.parseColor("#2AF598"))
                tvAlert.text = "🟡 New or unknown UPI — verify before paying"
                tvAlert.setBackgroundResource(R.drawable.bg_alert_safe)
            }

            score <= 60 -> {
                tvRiskLabel.setBackgroundResource(R.drawable.bg_risk_medium)
                tvRiskPercent.setTextColor(Color.parseColor("#F59E0B"))
                tvAlert.text = "🟠 Suspicious pattern — proceed with caution"
                tvAlert.setBackgroundResource(R.drawable.bg_alert_medium)
            }

            score <= 80 -> {
                tvRiskLabel.setBackgroundResource(R.drawable.bg_risk_high)
                tvRiskPercent.setTextColor(Color.parseColor("#FF6B6B"))
                tvAlert.text = "🔴 High risk — this UPI has been reported"
                tvAlert.setBackgroundResource(R.drawable.bg_alert_soft)
            }

            else -> {
                tvRiskLabel.setBackgroundResource(R.drawable.bg_risk_high)
                tvRiskPercent.setTextColor(Color.parseColor("#FF4D4D"))
                tvAlert.text = "🚨 DANGER — fake handle or reported 3+ times!"
                tvAlert.setBackgroundResource(R.drawable.bg_alert_soft)
            }
        }
    }
}