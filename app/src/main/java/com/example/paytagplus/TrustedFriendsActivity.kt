package com.example.paytagplus

import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TrustedFriendsActivity : BaseActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var searchView: SearchView
    private val db = FirebaseFirestore.getInstance()

    private var allFriends = listOf<TrustedFriend>()
    private var adapter: TrustedFriendsAdapter? = null
    private var pendingRemovalDocId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trusted_friends)

        recycler   = findViewById(R.id.recyclerTrustedFriends)
        tvEmpty    = findViewById(R.id.tvEmpty)
        searchView = findViewById(R.id.searchView)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        recycler.layoutManager = LinearLayoutManager(this)

        setupSearch()
        loadTrustedFriends()
    }

    override fun onResume() {
        super.onResume()   // ← keeps BaseActivity broadcast receiver working
        val docId = pendingRemovalDocId ?: return
        val uid   = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users")
            .document(uid)
            .collection("trusted_friends")
            .document(docId)
            .delete()
            .addOnSuccessListener {
                allFriends = allFriends.filter { it.docId != docId }
                adapter?.updateList(allFriends)
                if (allFriends.isEmpty()) {
                    tvEmpty.visibility  = View.VISIBLE
                    recycler.visibility = View.GONE
                }
                Toast.makeText(this, "Removed from Trusted Friends ✅", Toast.LENGTH_SHORT).show()
            }
        pendingRemovalDocId = null
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val filtered = allFriends.filter {
                    it.name.contains(newText ?: "", ignoreCase = true) ||
                            it.upiId.contains(newText ?: "", ignoreCase = true)
                }
                adapter?.updateList(filtered)
                tvEmpty.visibility  = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility = if (filtered.isEmpty()) View.GONE   else View.VISIBLE
                return true
            }
        })
    }

    private fun loadTrustedFriends() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            tvEmpty.visibility  = View.VISIBLE
            recycler.visibility = View.GONE
            return
        }

        db.collection("users").document(uid)
            .collection("trusted_friends")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    tvEmpty.visibility  = View.VISIBLE
                    recycler.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val friends = mutableListOf<TrustedFriend>()
                var pending = documents.size()

                documents.forEach { doc ->
                    val upiId = doc.getString("upi_id") ?: ""
                    val name  = doc.getString("name")   ?: ""
                    val docId = upiId.replace("@", "_").replace(".", "_")

                    fetchLiveRiskScore(upiId, docId) { liveScore, liveLabel ->
                        db.collection("users").document(uid)
                            .collection("trusted_friends").document(docId)
                            .update(mapOf("risk_score" to liveScore.toString(), "risk_label" to liveLabel))

                        friends.add(TrustedFriend(docId, name, upiId, liveLabel, liveScore.toString()))

                        pending--
                        if (pending == 0) {
                            allFriends = friends.sortedBy { it.name }
                            recycler.visibility = View.VISIBLE
                            tvEmpty.visibility  = View.GONE

                            adapter = TrustedFriendsAdapter(
                                items         = allFriends.toMutableList(),
                                onRemove      = { friend -> showRemoveDialog(friend, uid) },
                                onReportFraud = { friend -> launchReportFraud(friend) }
                            )
                            recycler.adapter = adapter
                        }
                    }
                }
            }
            .addOnFailureListener {
                tvEmpty.visibility  = View.VISIBLE
                recycler.visibility = View.GONE
            }
    }

    // ── REPLACED: was AlertDialog.Builder ──
    private fun launchReportFraud(friend: TrustedFriend) {
        showCustomDialog(
            title         = "Report Fraud",
            message       = "Report ${friend.upiId} as fraudulent?\n\nThey will also be removed from your Trusted Friends.",
            positiveText  = "Report",
            negativeText  = "Cancel",
            positiveColor = "#FFFFFF",
            onPositive    = {
                pendingRemovalDocId = friend.docId
                startActivity(
                    Intent(this, ReportFraudActivity::class.java)
                        .putExtra("upi_id", friend.upiId)
                )
            }
        )
    }

    // ── REPLACED: was AlertDialog.Builder ──
    private fun showRemoveDialog(friend: TrustedFriend, uid: String) {
        showCustomDialog(
            title         = "Remove Trusted Friend",
            message       = "Remove ${friend.name} from your trusted friends?",
            positiveText  = "Remove",
            negativeText  = "Cancel",
            positiveColor = "#FFFFFF",
            onPositive    = {
                db.collection("users").document(uid)
                    .collection("trusted_friends").document(friend.docId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "${friend.name} removed ✅", Toast.LENGTH_SHORT).show()
                        allFriends = allFriends.filter { it.docId != friend.docId }
                        adapter?.updateList(allFriends)
                        if (allFriends.isEmpty()) {
                            tvEmpty.visibility  = View.VISIBLE
                            recycler.visibility = View.GONE
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to remove ❌", Toast.LENGTH_SHORT).show()
                    }
            }
        )
    }

    private fun fetchLiveRiskScore(
        upiId: String, docId: String,
        callback: (score: Int, label: String) -> Unit
    ) {
        val reportRef = db.collection("fraud_reports").document(docId)
        val flagRef   = db.collection("flagged_upis").document(docId)

        reportRef.get().addOnSuccessListener { reportDoc ->
            val reportCount = if (reportDoc.exists()) (reportDoc.getLong("report_count") ?: 0).toInt() else 0
            flagRef.get().addOnSuccessListener { flagDoc ->
                val severity = if (flagDoc.exists()) flagDoc.getString("severity") ?: "none" else "none"
                val score = RiskEngine.calculateRiskScore(upiId, reportCount, severity)
                callback(score, RiskEngine.getRiskLabel(score))
            }.addOnFailureListener {
                val score = RiskEngine.calculateRiskScore(upiId, reportCount, "none")
                callback(score, RiskEngine.getRiskLabel(score))
            }
        }.addOnFailureListener {
            val score = RiskEngine.calculateRiskScore(upiId, 0, "none")
            callback(score, RiskEngine.getRiskLabel(score))
        }
    }
}

// ── Data class & Adapter unchanged below ──

data class TrustedFriend(
    val docId: String = "",
    val name: String,
    val upiId: String,
    val riskLabel: String,
    val riskScore: String
)

class TrustedFriendsAdapter(
    private var items: MutableList<TrustedFriend>,
    private val onRemove: (TrustedFriend) -> Unit,
    private val onReportFraud: (TrustedFriend) -> Unit
) : RecyclerView.Adapter<TrustedFriendsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView   = view.findViewById(R.id.ivAvatar)
        val tvName: TextView      = view.findViewById(R.id.tvName)
        val tvUpiId: TextView     = view.findViewById(R.id.tvUpiId)
        val tvRiskLabel: TextView = view.findViewById(R.id.tvRiskLabel)
        val btnMore: ImageView    = view.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_trusted_friend, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text      = item.name
        holder.tvUpiId.text     = item.upiId
        holder.tvRiskLabel.text = item.riskLabel
        holder.ivAvatar.setImageResource(R.drawable.ic_avtar)

        val bgRes = when (item.riskLabel.lowercase()) {
            "low risk"              -> R.drawable.bg_risk_low
            "caution"               -> R.drawable.bg_risk_caution
            "medium risk", "medium" -> R.drawable.bg_risk_medium
            "high risk", "high"     -> R.drawable.bg_risk_high
            "danger"                -> R.drawable.bg_risk_danger
            else                    -> R.drawable.bg_risk_low
        }
        holder.tvRiskLabel.setBackgroundResource(bgRes)

        // ✅ FIXED POPUP MENU
        holder.btnMore.setOnClickListener { anchor ->

            val popupView = LayoutInflater.from(anchor.context)
                .inflate(R.layout.layout_trusted_friend_popup, null)

            val popupWindow = android.widget.PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            popupWindow.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            popupWindow.elevation = 8f
            popupWindow.isOutsideTouchable = true

            // ✅ Measure popup size (important for proper alignment)
            popupView.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED
            )

            val popupWidth = popupView.measuredWidth
            val anchorWidth = anchor.width

            // Click actions
            popupView.findViewById<TextView>(R.id.optionRemove).setOnClickListener {
                popupWindow.dismiss()
                onRemove(item)
            }

            popupView.findViewById<TextView>(R.id.optionReportFraud).setOnClickListener {
                popupWindow.dismiss()
                onReportFraud(item)
            }

            // ✅ Perfect alignment with 3-dot button (RIGHT aligned, not oversized)
            popupWindow.showAsDropDown(
                anchor,
                anchorWidth - popupWidth,   // aligns to right edge
                8                           // slight gap below
            )
        }
    }

    fun updateList(newList: List<TrustedFriend>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
}