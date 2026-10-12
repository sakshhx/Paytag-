package com.example.paytagplus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AwarenessDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Paste this at the TOP of every onCreate, before setContentView
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.getBoolean("dark_theme", true)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        setContentView(R.layout.activity_awareness_detail)

        val topic = intent.getStringExtra("topic") ?: "Tips"

        findViewById<TextView>(R.id.txtDetailTitle).text = topic
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val items = getContentFor(topic)
        val rv = findViewById<RecyclerView>(R.id.rvAwarenessItems)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = AwarenessTipAdapter(items)
    }

    // ── Content map — add as many topics as you want ──────────────────────
    private fun getContentFor(topic: String): List<AwarenessTip> = when (topic) {

        "Common UPI Scams" -> listOf(
            AwarenessTip("🎭", "Fake Payment Requests",
                "Scammers send a 'Collect Money' request pretending to send money. Accepting it deducts money FROM you, not adds."),
            AwarenessTip("📞", "Vishing / Phone Fraud",
                "Fraudsters call posing as bank officials asking for your UPI PIN or OTP. No legitimate bank ever asks for this."),
            AwarenessTip("🔗", "Phishing Links",
                "Fake links like 'paytm-offer.com' mimic real apps. Always check the URL carefully before entering any credentials."),
            AwarenessTip("🎁", "Too-Good-To-Be-True Offers",
                "\"Send ₹10, get ₹1000 cashback\" — these are always scams designed to steal your money or credentials."),
            AwarenessTip("📷", "QR Code Swapping",
                "Scammers physically replace QR codes on shop counters with their own. Always verify the merchant name on the payment screen.")
        )

        "How to Stay Safe" -> listOf(
            AwarenessTip("🔒", "Never Share Your UPI PIN",
                "Your UPI PIN is like your ATM PIN. No app, bank, or support agent should ever ask for it."),
            AwarenessTip("✅", "Verify Before Paying",
                "Always check the receiver's name on the confirmation screen. Scam UPI IDs often have slight misspellings."),
            AwarenessTip("📲", "Use App Lock",
                "Enable PayTag++ App Lock (Settings → App Lock) so no one can open the app without your PIN or fingerprint."),
            AwarenessTip("🔔", "Enable Fraud Alerts",
                "Turn on Notifications in Settings so PayTag++ can warn you about suspicious UPI IDs in real-time."),
            AwarenessTip("👥", "Add Trusted Friends",
                "Add verified contacts to your Trusted list. PayTag++ cross-checks scans against your trusted network.")
        )

        "What to Do If Scammed" -> listOf(
            AwarenessTip("🚨", "Call Your Bank Immediately",
                "Call the bank helpline (usually 1800-xxx) within minutes. They may be able to reverse the transaction."),
            AwarenessTip("📋", "File a Cybercrime Report",
                "Visit cybercrime.gov.in or call 1930 (National Cybercrime Helpline) to report the fraud officially."),
            AwarenessTip("📸", "Take Screenshots",
                "Screenshot the transaction ID, UPI ID, and any chats with the scammer. These are needed for your complaint."),
            AwarenessTip("🏦", "Contact NPCI",
                "Raise a dispute on the NPCI portal at npci.org.in if your bank doesn't resolve within 30 days."),
            AwarenessTip("🔐", "Change Your UPI PIN",
                "Immediately change your UPI PIN on your banking app to prevent further unauthorized access.")
        )

        "Fake QR Code Tricks" -> listOf(
            AwarenessTip("🔍", "Check Merchant Name",
                "After scanning, always read the merchant name carefully on the payment screen before confirming."),
            AwarenessTip("📌", "Look for Stickers",
                "Scammers paste their QR sticker over the real one. If a QR code looks like a sticker, peel it — the real one may be underneath."),
            AwarenessTip("💰", "Receiving ≠ Scanning",
                "To RECEIVE money, you never need to scan a QR. If someone asks you to scan to receive money, it's a scam."),
            AwarenessTip("🏪", "Verify at Big Shops",
                "At shops/restaurants, ask staff to confirm their UPI ID verbally before paying to an unfamiliar QR."),
            AwarenessTip("🤖", "PayTag++ Scans for You",
                "PayTag++ checks scanned QR codes against known fraud patterns instantly — always use it before paying.")
        )

        "Safe Payment Checklist" -> listOf(
            AwarenessTip("☑️", "Is the UPI ID correct?",
                "Double-check for typos like 'paytm@icic' vs 'paytm@icici'. One missing letter = different account."),
            AwarenessTip("☑️", "Is the amount correct?",
                "Verify the exact amount before pressing Pay. Some scam apps auto-fill higher amounts."),
            AwarenessTip("☑️", "Is this a trusted contact?",
                "Check if the receiver is in your Trusted Friends list in PayTag++."),
            AwarenessTip("☑️", "Did PayTag++ give a Safe score?",
                "Only proceed if PayTag++ shows Low Risk. For Medium/High Risk, investigate further."),
            AwarenessTip("☑️", "Are you on a secure network?",
                "Avoid making UPI payments on public Wi-Fi. Use mobile data for financial transactions.")
        )

        else -> listOf(AwarenessTip("ℹ️", "Coming Soon", "More content will be added soon."))
    }
}

// ── Data class ────────────────────────────────────────────────────────────────
data class AwarenessTip(val emoji: String, val title: String, val body: String)

// ── Adapter ───────────────────────────────────────────────────────────────────
class AwarenessTipAdapter(private val items: List<AwarenessTip>) :
    RecyclerView.Adapter<AwarenessTipAdapter.TipVH>() {

    inner class TipVH(v: View) : RecyclerView.ViewHolder(v) {
        val emoji: TextView  = v.findViewById(R.id.tipEmoji)
        val title: TextView  = v.findViewById(R.id.tipTitle)
        val body:  TextView  = v.findViewById(R.id.tipBody)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TipVH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_awareness_tip, parent, false)
    )

    override fun onBindViewHolder(h: TipVH, i: Int) {
        h.emoji.text = items[i].emoji
        h.title.text = items[i].title
        h.body.text  = items[i].body
    }

    override fun getItemCount() = items.size
}