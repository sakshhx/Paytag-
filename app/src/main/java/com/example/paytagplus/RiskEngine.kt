package com.example.paytagplus

object RiskEngine {

    private val KNOWN_HANDLES = listOf(
        "okicici", "oksbi", "okaxis", "okhdfcbank",
        "ybl", "ibl", "axl",
        "paytm", "apl", "upi",
        "icici", "sbi", "hdfcbank", "kotak", "indus"
    )

    private val FRAUD_HANDLES = listOf(
        "yb1", "paytrn", "0kicici", "paytmm",
        "okicicii", "goog1epay", "phonepe1", "paytem",
        "0ksbi", "refund", "prize", "lucky", "reward",
        "cashback", "secure", "npci", "rbi", "support",
        "helpdesk", "customercare", "upi123", "pay123"
    )

    private val SUSPICIOUS_KEYWORDS = listOf(
        "refund", "prize", "lucky", "reward", "cashback",
        "offer", "free", "win", "helpdesk", "support",
        "care", "npci", "rbi", "secure", "verify",
        "kyc", "otp", "pin", "urgent", "block"
    )

    // ✅ 3rd parameter added — flaggedSeverity
    fun calculateRiskScore(
        upiId: String,
        reportCount: Int = 0,
        flaggedSeverity: String = "none"
    ): Int {

        // ── Layer 1: Critically flagged → return immediately ──
        if (flaggedSeverity == "critical") return 95

        // ── Layer 1: Flag score based on severity ──
        val flagScore = when (flaggedSeverity) {
            "low"      -> 30
            "medium"   -> 55
            "high"     -> 75
            "critical" -> 95
            else       -> 0
        }

        val upi    = upiId.lowercase().trim()
        val parts  = upi.split("@")
        val prefix = if (parts.isNotEmpty()) parts[0] else ""
        val handle = if (parts.size > 1) parts[1] else ""

        val isKnownHandle        = handle in KNOWN_HANDLES
        val isFraudHandle        = handle in FRAUD_HANDLES
        val digitCount           = prefix.count { it.isDigit() }
        val isAllDigits          = prefix.all { it.isDigit() }
        val digitRatio           = if (prefix.isNotEmpty()) digitCount.toFloat() / prefix.length else 0f
        val hasNumbersInHandle   = handle.any { it.isDigit() }
        val isLongPrefix         = prefix.length > 10
        val isShortPrefix        = prefix.length < 5
        val hasSpecialChars      = upi.count {
            !it.isLetterOrDigit() && it != '@' && it != '.'
        } > 0
        val hasSuspiciousKeyword = SUSPICIOUS_KEYWORDS.any { it in upi }

        // ── Layer 2: Pattern score ──
        var patternScore = 0
        if (isFraudHandle)        patternScore += 40
        else if (!isKnownHandle)  patternScore += 10
        if (isAllDigits)          patternScore += 8
        if (digitRatio > 0.7f)    patternScore += 6
        if (hasNumbersInHandle)   patternScore += 8
        if (hasSpecialChars)      patternScore += 5
        if (!upi.contains("@"))   patternScore += 15
        if (hasSuspiciousKeyword) patternScore += 30
        if (isLongPrefix)         patternScore += 4
        if (isShortPrefix)        patternScore += 3

        // ── Layer 3: Community report score ──
        val reportScore = when {
            reportCount == 0      -> 0
            reportCount == 1      -> 5
            reportCount == 2      -> 10
            reportCount in 3..4   -> 18
            reportCount in 5..6   -> 28
            reportCount in 7..8   -> 38
            reportCount in 9..10  -> 50
            reportCount in 11..15 -> 62
            reportCount in 16..20 -> 75
            else                  -> 85
        }

        // ── Final score ──
        // If flagged → flag score dominates, reports add a little on top
        // If not flagged → pattern + reports
        val rawScore = if (flagScore > 0) {
            flagScore + (reportScore / 3)
        } else {
            5 + patternScore + reportScore
        }

        return rawScore.coerceIn(5, 100)
    }

    fun getRiskLabel(score: Int): String {
        return when {
            score <= 15 -> "LOW RISK"
            score <= 35 -> "CAUTION"
            score <= 60 -> "MEDIUM RISK"
            score <= 80 -> "HIGH RISK"
            else        -> "DANGER"
        }
    }

    fun getRiskBreakdown(
        upiId: String,
        flaggedSeverity: String = "none"
    ): List<String> {
        val upi    = upiId.lowercase().trim()
        val parts  = upi.split("@")
        val prefix = if (parts.isNotEmpty()) parts[0] else ""
        val handle = if (parts.size > 1) parts[1] else ""
        val reasons = mutableListOf<String>()

        if (flaggedSeverity != "none")
            reasons.add("🚨 This UPI is in our flagged fraud database")
        if (handle in FRAUD_HANDLES)
            reasons.add("🚨 Known fraudulent UPI handle (@$handle)")
        else if (handle !in KNOWN_HANDLES)
            reasons.add("⚠ Unknown UPI handle (@$handle)")
        if (prefix.all { it.isDigit() })
            reasons.add("⚠ Prefix is all digits — suspicious pattern")
        if (prefix.length > 10)
            reasons.add("⚠ Unusually long prefix")
        if (handle.any { it.isDigit() })
            reasons.add("⚠ Handle contains numbers — possible fake")
        if (!upi.contains("@"))
            reasons.add("⚠ Invalid UPI format — missing @")
        val foundKeyword = SUSPICIOUS_KEYWORDS.firstOrNull { it in upi }
        if (foundKeyword != null)
            reasons.add("🚨 Suspicious keyword detected: '$foundKeyword'")
        if (reasons.isEmpty())
            reasons.add("✅ No suspicious patterns detected")

        return reasons
    }
}