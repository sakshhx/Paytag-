package com.example.paytagplus

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ReportFraudActivity : BaseActivity() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var selectedReason = ""
    private lateinit var upiId: String

    private lateinit var radioFakeQr: ImageView
    private lateinit var radioImpersonation: ImageView
    private lateinit var radioUnauthorized: ImageView
    private lateinit var radioPhishing: ImageView
    private lateinit var radioOther: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_fraud)

        upiId = intent.getStringExtra("upi_id") ?: ""

        val tvUpiId   = findViewById<TextView>(R.id.tvUpiId)
        val etNotes   = findViewById<EditText>(R.id.etNotes)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitReport)
        val btnBack   = findViewById<ImageView>(R.id.btnBack)

        radioFakeQr        = findViewById(R.id.radioFakeQr)
        radioImpersonation = findViewById(R.id.radioImpersonation)
        radioUnauthorized  = findViewById(R.id.radioUnauthorized)
        radioPhishing      = findViewById(R.id.radioPhishing)
        radioOther         = findViewById(R.id.radioOther)

        tvUpiId.text = upiId.ifEmpty { "Unknown UPI" }
        btnBack.setOnClickListener { finish() }

        ensureUserDocumentExists()
        setupReasonSelection()

        btnSubmit.setOnClickListener {
            val notes = etNotes.text.toString().trim()
            if (selectedReason.isEmpty()) {
                Toast.makeText(this, "Please select a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showConfirmDialog(notes)
        }
    }

    private fun ensureUserDocumentExists() {
        val uid   = auth.currentUser?.uid ?: return
        val name  = auth.currentUser?.displayName ?: "User"
        val email = auth.currentUser?.email ?: ""

        db.collection("users").document(uid)
            .set(
                hashMapOf(
                    "uid"   to uid,
                    "name"  to name,
                    "email" to email
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
    }

    private fun setupReasonSelection() {
        val reasons = mapOf(
            findViewById<LinearLayout>(R.id.reasonFakeQr)        to "Fake QR Code",
            findViewById<LinearLayout>(R.id.reasonImpersonation) to "Impersonation / Fake Identity",
            findViewById<LinearLayout>(R.id.reasonUnauthorized)  to "Unauthorized Transaction",
            findViewById<LinearLayout>(R.id.reasonPhishing)      to "Phishing / Scam Link",
            findViewById<LinearLayout>(R.id.reasonOther)         to "Other"
        )

        val radios = mapOf(
            "Fake QR Code"                  to radioFakeQr,
            "Impersonation / Fake Identity" to radioImpersonation,
            "Unauthorized Transaction"      to radioUnauthorized,
            "Phishing / Scam Link"          to radioPhishing,
            "Other"                         to radioOther
        )

        reasons.forEach { (layout, reason) ->
            layout.setOnClickListener {
                selectedReason = reason
                radios.values.forEach { it.setImageResource(R.drawable.ic_radio_unselected) }
                radios[reason]?.setImageResource(R.drawable.ic_radio_selected)
            }
        }
    }

    private fun showConfirmDialog(notes: String) {
        showCustomDialog(
            title         = "Confirm Report",
            message       = "Report $upiId as fraudulent?\n\nReason: $selectedReason",
            positiveText  = "Report",
            negativeText  = "Cancel",
            positiveColor = "#B00020",
            onPositive    = {
                submitReport(notes)
            }
        )
    }

    private fun submitReport(notes: String) {
        val uid   = auth.currentUser?.uid ?: "anonymous"
        val docId = upiId.replace("@", "_").replace(".", "_")

        val sdf = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val timestamp = sdf.format(Date())

        db.collection("fraud_reports")
            .document(docId)
            .collection("reports")
            .whereEqualTo("reported_by", uid)
            .get()
            .addOnSuccessListener { existing ->
                if (!existing.isEmpty) {
                    Toast.makeText(
                        this,
                        "You have already reported this UPI!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                val report = hashMapOf(
                    "reported_by" to uid,
                    "reason"      to selectedReason,
                    "notes"       to notes,
                    "timestamp"   to timestamp,
                    "upi_id"      to upiId
                )

                db.collection("fraud_reports")
                    .document(docId)
                    .collection("reports")
                    .add(report)
                    .addOnSuccessListener {
                        db.collection("fraud_reports")
                            .document(docId)
                            .set(
                                hashMapOf(
                                    "upi_id"        to upiId,
                                    "report_count"  to FieldValue.increment(1),
                                    "last_reported" to timestamp
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                            .addOnSuccessListener {
                                checkAndAutoFlag(docId, upiId, uid)
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to submit ❌", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking reports ❌", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkAndAutoFlag(docId: String, upiId: String, reporterUid: String) {
        db.collection("fraud_reports").document(docId).get()
            .addOnSuccessListener { doc ->
                val count    = (doc.getLong("report_count") ?: 0).toInt()
                val severity = when {
                    count >= 20 -> "critical"
                    count >= 15 -> "high"
                    count >= 10 -> "medium"
                    count >= 5  -> "low"
                    else        -> null
                }

                if (severity != null) {
                    db.collection("flagged_upis")
                        .document(docId)
                        .set(
                            hashMapOf(
                                "upi_id"     to upiId,
                                "severity"   to severity,
                                "flagged_by" to "auto",
                                "reason"     to "Auto-flagged: $count community reports"
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                }
                alertTrustedFriends(upiId, reporterUid)
            }
            .addOnFailureListener {
                alertTrustedFriends(upiId, reporterUid)
            }
    }

    private fun alertTrustedFriends(upiId: String, reporterUid: String) {
        val docId = upiId.replace("@", "_").replace(".", "_")

        db.collection("users").get()
            .addOnSuccessListener { users ->
                if (users.isEmpty) {
                    showSuccessAndFinish()
                    return@addOnSuccessListener
                }

                var pendingChecks = users.size()

                users.forEach { user ->
                    val userId = user.id

                    if (userId == reporterUid) {
                        pendingChecks--
                        if (pendingChecks == 0) showSuccessAndFinish()
                        return@forEach
                    }

                    db.collection("users")
                        .document(userId)
                        .collection("trusted_friends")
                        .document(docId)
                        .get()
                        .addOnSuccessListener { friendDoc ->
                            if (friendDoc.exists()) {
                                val friendName = friendDoc.getString("name") ?: upiId

                                val alert = hashMapOf(
                                    "upi_id"    to upiId,
                                    "message"   to "⚠️ $friendName ($upiId) from your Trusted Friends has been reported as fraud!",
                                    "timestamp" to FieldValue.serverTimestamp(), // ✅ fixed
                                    "seen"      to false // ✅ boolean
                                )

                                db.collection("users")
                                    .document(userId)
                                    .collection("alerts")
                                    .add(alert)
                            }

                            pendingChecks--
                            if (pendingChecks == 0) showSuccessAndFinish()
                        }
                        .addOnFailureListener {
                            pendingChecks--
                            if (pendingChecks == 0) showSuccessAndFinish()
                        }
                }
            }
            .addOnFailureListener {
                showSuccessAndFinish()
            }
    }

    private fun showSuccessAndFinish() {
        Toast.makeText(
            this,
            "✅ Report submitted! Thank you for keeping PayTag++ safe.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }
}