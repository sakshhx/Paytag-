package com.example.paytagplus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ReportListActivity : BaseActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Paste this at the TOP of every onCreate, before setContentView
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        setContentView(R.layout.activity_report_list)

        recycler     = findViewById(R.id.recyclerReports)
        layoutEmpty  = findViewById(R.id.layoutEmpty)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        recycler.layoutManager = LinearLayoutManager(this)

        loadMyReports()
    }

    private fun loadMyReports() {
        val uid = auth.currentUser?.uid ?: return

        // ── Fetch all fraud_reports and check which ones I reported ──
        db.collection("fraud_reports").get()
            .addOnSuccessListener { fraudDocs ->
                val reportItems = mutableListOf<ReportItem>()
                var pending = fraudDocs.size()

                if (pending == 0) {
                    showEmpty()
                    return@addOnSuccessListener
                }

                fraudDocs.forEach { fraudDoc ->
                    val upiId       = fraudDoc.getString("upi_id") ?: ""
                    val reportCount = (fraudDoc.getLong("report_count") ?: 0).toInt()

                    // ── Check if I reported this UPI ──
                    fraudDoc.reference
                        .collection("reports")
                        .whereEqualTo("reported_by", uid)
                        .get()
                        .addOnSuccessListener { myReports ->
                            if (!myReports.isEmpty) {
                                val myReport  = myReports.documents[0]
                                val reason    = myReport.getString("reason") ?: ""
                                val timestamp = myReport.getString("timestamp") ?: ""

                                val riskScore = RiskEngine.calculateRiskScore(upiId, reportCount)
                                val riskLabel = RiskEngine.getRiskLabel(riskScore)

                                reportItems.add(
                                    ReportItem(
                                        upiId       = upiId,
                                        reason      = reason,
                                        timestamp   = timestamp,
                                        riskLabel   = riskLabel,
                                        reportCount = reportCount
                                    )
                                )
                            }

                            pending--
                            if (pending == 0) {
                                if (reportItems.isEmpty()) {
                                    showEmpty()
                                } else {
                                    // ── Sort newest first ──
                                    reportItems.sortByDescending { it.timestamp }
                                    recycler.visibility     = View.VISIBLE
                                    layoutEmpty.visibility  = View.GONE
                                    recycler.adapter = ReportListAdapter(reportItems)
                                }
                            }
                        }
                        .addOnFailureListener {
                            pending--
                            if (pending == 0 && reportItems.isEmpty()) showEmpty()
                        }
                }
            }
            .addOnFailureListener { showEmpty() }
    }

    private fun showEmpty() {
        layoutEmpty.visibility  = View.VISIBLE
        recycler.visibility     = View.GONE
    }
}

// ── Data class ──
data class ReportItem(
    val upiId: String,
    val reason: String,
    val timestamp: String,
    val riskLabel: String,
    val reportCount: Int
)

// ── Adapter ──
class ReportListAdapter(private val items: List<ReportItem>) :
    RecyclerView.Adapter<ReportListAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUpiId      : TextView = view.findViewById(R.id.tvUpiId)
        val tvRiskLabel  : TextView = view.findViewById(R.id.tvRiskLabel)
        val tvReason     : TextView = view.findViewById(R.id.tvReason)
        val tvTimestamp  : TextView = view.findViewById(R.id.tvTimestamp)
        val tvReportCount: TextView = view.findViewById(R.id.tvReportCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report_fraud, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvUpiId.text       = item.upiId
        holder.tvReason.text      = "Reason: ${item.reason}"
        holder.tvTimestamp.text   = item.timestamp
        holder.tvReportCount.text = "${item.reportCount} report${if (item.reportCount > 1) "s" else ""}"
        holder.tvRiskLabel.text   = item.riskLabel

        val bgRes = when (item.riskLabel.lowercase()) {
            "low risk"              -> R.drawable.bg_risk_low
            "caution"               -> R.drawable.bg_risk_caution
            "medium risk", "medium" -> R.drawable.bg_risk_medium
            "high risk", "high"     -> R.drawable.bg_risk_high
            "danger"                -> R.drawable.bg_risk_danger
            else                    -> R.drawable.bg_risk_low
        }
        holder.tvRiskLabel.setBackgroundResource(bgRes)
    }

    override fun getItemCount() = items.size
}