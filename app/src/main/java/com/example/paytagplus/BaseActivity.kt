package com.example.paytagplus

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    private val fraudAlertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val title = intent.getStringExtra("title") ?: return
            val body  = intent.getStringExtra("body")  ?: ""
            val upiId = intent.getStringExtra("upi_id") ?: ""
            showFraudAlertDialog(title, body, upiId)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            fraudAlertReceiver,
            IntentFilter("FRAUD_ALERT_DIALOG"),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(fraudAlertReceiver)
    }

    private fun showFraudAlertDialog(title: String, body: String, upiId: String) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_fraud_alert, null)

        view.findViewById<TextView>(R.id.tvTitle).text = title
        view.findViewById<TextView>(R.id.tvBody).text  = body

        val tvUpiId = view.findViewById<TextView>(R.id.tvUpiId)
        if (upiId.isNotEmpty()) {
            tvUpiId.text       = "UPI: $upiId"
            tvUpiId.visibility = View.VISIBLE
        }

        view.findViewById<TextView>(R.id.btnView).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<TextView>(R.id.btnDismiss).setOnClickListener {
            dialog.dismiss()
        }

        dialog.apply {
            setContentView(view)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.82).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
            show()
        }
    }fun showCustomDialog(
        title: String,
        message: String,
        positiveText: String = "OK",
        negativeText: String = "Cancel",
        positiveColor: String = "#0A84FF",
        onPositive: () -> Unit = {},
        onNegative: () -> Unit = {}
    ) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_fraud_alert, null)

        view.findViewById<TextView>(R.id.tvTitle).text = title
        view.findViewById<TextView>(R.id.tvBody).text  = message
        view.findViewById<TextView>(R.id.tvUpiId).visibility = View.GONE

        view.findViewById<TextView>(R.id.btnView).apply {
            text = positiveText
            setTextColor(Color.parseColor(positiveColor))
            setOnClickListener { dialog.dismiss(); onPositive() }
        }

        view.findViewById<TextView>(R.id.btnDismiss).apply {
            text = negativeText
            setOnClickListener { dialog.dismiss(); onNegative() }
        }

        dialog.apply {
            setContentView(view)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.82).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
            show()
        }
    }
}