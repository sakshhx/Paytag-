package com.example.paytagplus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ScanHistoryActivity : BaseActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var searchView: SearchView
    private val db = FirebaseFirestore.getInstance()

    private var allScans = listOf<ScanItem>()
    private var adapter: ScanHistoryAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_history)

        recycler    = findViewById(R.id.recyclerScanHistory)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        searchView  = findViewById(R.id.searchView)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnClearAll).setOnClickListener {
            showClearAllDialog()
        }

        recycler.layoutManager = LinearLayoutManager(this)

        setupSearch()
        loadScanHistory()
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val filtered = allScans.filter {
                    it.upiId.contains(newText ?: "", ignoreCase = true)
                }
                adapter?.updateList(filtered)
                layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility    = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                return true
            }
        })
    }

    // ── REPLACED: was AlertDialog.Builder ──
    private fun showClearAllDialog() {
        if (allScans.isEmpty()) {
            Toast.makeText(this, "No history to clear", Toast.LENGTH_SHORT).show()
            return
        }

        showCustomDialog(
            title         = "Clear Scan History",
            message       = "Are you sure you want to delete all scan history? This cannot be undone.",
            positiveText  = "Clear All",
            negativeText  = "Cancel",
            positiveColor = "#FFFFFF",
            onPositive    = { clearAllHistory() }
        )
    }

    private fun clearAllHistory() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users")
            .document(uid)
            .collection("scan_history")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) return@addOnSuccessListener

                val batch = db.batch()
                documents.forEach { doc -> batch.delete(doc.reference) }

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "✅ Scan history cleared!", Toast.LENGTH_SHORT).show()
                        allScans = emptyList()
                        adapter?.updateList(emptyList())
                        layoutEmpty.visibility = View.VISIBLE
                        recycler.visibility    = View.GONE
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to clear history ❌", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun loadScanHistory() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            layoutEmpty.visibility = View.VISIBLE
            recycler.visibility    = View.GONE
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { fetchScans(uid) }
            .addOnFailureListener {
                layoutEmpty.visibility = View.VISIBLE
                recycler.visibility    = View.GONE
            }
    }

    private fun fetchScans(uid: String) {
        db.collection("users")
            .document(uid)
            .collection("scan_history")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    layoutEmpty.visibility = View.VISIBLE
                    recycler.visibility    = View.GONE
                    return@addOnSuccessListener
                }

                val scans = documents.map { doc ->
                    ScanItem(
                        upiId     = doc.getString("upi_id") ?: "",
                        timestamp = doc.getString("timestamp") ?: "",
                        riskLabel = doc.getString("risk_label") ?: "Safe",
                        riskScore = doc.getString("risk_score") ?: "0"
                    )
                }
                refreshScoresAndShow(scans)
            }
            .addOnFailureListener {
                db.collection("users")
                    .document(uid)
                    .collection("scan_history")
                    .get()
                    .addOnSuccessListener { documents ->
                        if (documents.isEmpty) {
                            layoutEmpty.visibility = View.VISIBLE
                            recycler.visibility    = View.GONE
                            return@addOnSuccessListener
                        }
                        val scans = documents.map { doc ->
                            ScanItem(
                                upiId     = doc.getString("upi_id") ?: "",
                                timestamp = doc.getString("timestamp") ?: "",
                                riskLabel = doc.getString("risk_label") ?: "Safe",
                                riskScore = doc.getString("risk_score") ?: "0"
                            )
                        }
                        refreshScoresAndShow(scans)
                    }
                    .addOnFailureListener {
                        layoutEmpty.visibility = View.VISIBLE
                        recycler.visibility    = View.GONE
                    }
            }
    }

    private fun refreshScoresAndShow(scans: List<ScanItem>) {
        val updatedScans = scans.toMutableList()
        var pending = scans.size

        if (pending == 0) { showList(updatedScans); return }

        scans.forEachIndexed { index, scan ->
            if (scan.upiId.isEmpty()) {
                pending--
                if (pending == 0) showList(updatedScans)
                return@forEachIndexed
            }

            val docId     = scan.upiId.replace("@", "_").replace(".", "_")
            val reportRef = db.collection("fraud_reports").document(docId)
            val flagRef   = db.collection("flagged_upis").document(docId)

            reportRef.get().addOnSuccessListener { reportDoc ->
                val reportCount = if (reportDoc.exists()) (reportDoc.getLong("report_count") ?: 0).toInt() else 0

                flagRef.get().addOnSuccessListener { flagDoc ->
                    val severity  = if (flagDoc.exists()) flagDoc.getString("severity") ?: "none" else "none"
                    val liveScore = RiskEngine.calculateRiskScore(scan.upiId, reportCount, severity)
                    val liveLabel = RiskEngine.getRiskLabel(liveScore)

                    updatedScans[index] = scan.copy(riskScore = liveScore.toString(), riskLabel = liveLabel)
                    pending--
                    if (pending == 0) showList(updatedScans)

                }.addOnFailureListener { pending--; if (pending == 0) showList(updatedScans) }
            }.addOnFailureListener { pending--; if (pending == 0) showList(updatedScans) }
        }
    }

    private fun showList(scans: List<ScanItem>) {
        allScans = scans
        recycler.visibility    = View.VISIBLE
        layoutEmpty.visibility = View.GONE
        adapter = ScanHistoryAdapter(scans.toMutableList())
        recycler.adapter = adapter
    }
}

data class ScanItem(
    val upiId: String,
    val timestamp: String,
    val riskLabel: String,
    val riskScore: String
)

class ScanHistoryAdapter(private var items: MutableList<ScanItem>) :
    RecyclerView.Adapter<ScanHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUpiId: TextView     = view.findViewById(R.id.tvUpiId)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvRiskLabel: TextView = view.findViewById(R.id.tvRiskLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_scan_history, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvUpiId.text     = item.upiId
        holder.tvTimestamp.text = item.timestamp
        holder.tvRiskLabel.text = item.riskLabel

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

    fun updateList(newList: List<ScanItem>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
}