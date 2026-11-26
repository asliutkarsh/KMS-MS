package com.example.intelligence

import com.example.data.local.SenderRuleEntity
import com.example.data.model.SmsCategory

data class ClassificationResult(
    val category: SmsCategory,
    val isBlocked: Boolean,
    val otpCode: String?,
    val isBillMessage: Boolean
)

/**
 * 100% On-Device Deterministic SMS Classifier & NLP Rule Engine.
 * Runs locally with zero network dependencies.
 */
object SmsClassifierEngine {

    private val dltHeaderRegex = Regex("^[A-Za-z]{2}-[A-Za-z0-9]{4,8}$")
    private val alphanumericSenderRegex = Regex(".*[A-Za-z]{2,}.*")
    private val phoneNumberRegex = Regex("^\\+?\\d[\\d\\s\\-()]{6,}$")

    private val otpExplicitTightRegex = Regex(
        "(?i)\\b(?:otp|one[-\\s]?time\\s+(?:password|passcode|pin)|verification\\s+code|verify\\s+code|passcode|security\\s+code|login\\s+code|auth(?:entication)?\\s+code|2fa\\s+code|mfa\\s+code|secret\\s+code|confirmation\\s+code|tpin|mpin|pin\\s+code|cvv)\\b[^0-9\\n]{0,28}?(?:G-)?(\\d{3}-\\d{3}|\\d{4,8})\\b"
    )
    private val otpSeparatedIsRegex = Regex(
        "(?i)\\b(?:otp|one[-\\s]?time\\s+(?:password|passcode)|verification\\s+code|security\\s+code|login\\s+code|passcode)\\b.{0,80}?\\bis\\s*[:\\-]?\\s*(?:G-)?(\\d{3}-\\d{3}|\\d{4,8})\\b"
    )
    private val otpReverseRegex = Regex(
        "(?i)(?:^|\\W)(?:G-)?(\\d{3}-\\d{3}|\\d{4,8})\\s+(?:is\\s+(?:your|the)|as)\\s+(?:[A-Za-z0-9._-]+\\s+){0,4}?(?:otp|one[-\\s]?time|verification|verify|login|sign[-\\s]?in|security|auth(?:entication)?|passcode|secret\\s+code|2fa|pin)\\b"
    )
    private val otpUseEnterRegex = Regex(
        "(?i)\\b(?:use|enter|input|type)\\s+(?:code\\s+|otp\\s+)?(?:G-)?(\\d{3}-\\d{3}|\\d{4,8})\\s+(?:to\\s+(?:verify|login|log\\s+in|sign\\s+in|reset|confirm|complete|authenticate)|as\\s+(?:your\\s+)?(?:otp|verification|security|login|auth))\\b"
    )
    private val otpAppCodeRegex = Regex(
        "(?i)(?:your\\s+)?(?:whatsapp|telegram|signal|google|facebook|instagram|twitter|x|amazon|flipkart|swiggy|zomato|uber|ola|paytm|phonepe|hdfc|icici|sbi|axis)\\s+(?:verification\\s+|login\\s+|security\\s+)?code(?:\\s+is)?[:\\s-]+(?:G-)?(\\d{3}-\\d{3}|\\d{4,8})\\b"
    )
    private val otpContextKeywords = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "security code", "login code", "passcode", "do not share", "never share",
        "2fa", "mfa", "authentication code"
    )
    private val standaloneCodeRegex = Regex(
        "(?<![₹$€.,\\d-])\\b(\\d{4,8})\\b(?![.,]\\d|%|\\s*(?:am|pm|rs\\b|inr\\b|off\\b))"
    )

    private val transactionKeywords = listOf(
        "debited", "credited", "a/c", "acct", "account", "upi", "imps", "neft", "rtgs",
        "avl bal", "avail bal", "available balance", "current balance", "bal:", "balance is",
        "spent on", "card ending", "credit card", "debit card", "atm", "withdrawn",
        "epfo", "pf balance", "passbook", "paytm", "phonepe", "gpay", "amazon pay",
        "txn", "transaction", "statement", "due date", "bill due", "min due", "total due",
        "pnr", "flight", "boarding", "train", "irctc", "ticket confirmed", "booking confirmed",
        "out for delivery", "shipped", "dispatched", "delivered", "awb", "tracking",
        "appointment", "recharge successful", "plan expires", "kms-bill"
    )

    private val promoKeywords = listOf(
        "sale", "discount", "% off", "flat off", "cashback", "use code", "promo code",
        "coupon", "limited time", "hurry", "shop now", "buy now", "exclusive offer",
        "mega offer", "festive offer", "win up to", "pre-approved loan", "instant loan",
        "apply now", "unsubscribe", "opt out", "click here", "flash deal", "free delivery",
        "deal of the day", "upto", "voucher", "bonus points", "claim now"
    )

    private val billKeywords = listOf(
        "bill due", "payment due", "due date", "due on", "min due", "total due",
        "electricity bill", "broadband bill", "water bill", "gas bill", "postpaid bill",
        "credit card bill", "statement generated", "recharge due", "plan expires", "[kms-bill"
    )

    fun normalizeSender(address: String): String {
        val trimmed = address.trim()
        if (trimmed.contains(",")) {
            return trimmed.split(",")
                .map { normalizeSingleAddress(it) }
                .sorted()
                .joinToString(",")
        }
        return normalizeSingleAddress(trimmed)
    }

    private fun normalizeSingleAddress(addr: String): String {
        val cleaned = addr.trim()
        return if (phoneNumberRegex.matches(cleaned)) {
            val digits = cleaned.filter { it.isDigit() }
            if (digits.length > 10) digits.takeLast(10) else digits
        } else {
            cleaned.uppercase()
        }
    }

    fun extractOtp(body: String): String? {
        otpExplicitTightRegex.find(body)?.groupValues?.getOrNull(1)?.let {
            return it.replace("-", "")
        }
        otpReverseRegex.find(body)?.groupValues?.getOrNull(1)?.let {
            return it.replace("-", "")
        }
        otpUseEnterRegex.find(body)?.groupValues?.getOrNull(1)?.let {
            return it.replace("-", "")
        }
        otpAppCodeRegex.find(body)?.groupValues?.getOrNull(1)?.let {
            return it.replace("-", "")
        }
        otpSeparatedIsRegex.find(body)?.groupValues?.getOrNull(1)?.let {
            return it.replace("-", "")
        }
        val lower = body.lowercase()
        if (!lower.contains("no otp") && otpContextKeywords.any { lower.contains(it) }) {
            val candidate = standaloneCodeRegex.findAll(body)
                .map { it.groupValues[1] }
                .firstOrNull { code ->
                    val asInt = code.toIntOrNull()
                    val isRecentYear = code.length == 4 && asInt != null && asInt in 2020..2035
                    val isTollFree = code.startsWith("1800") || code.startsWith("1860")
                    !isRecentYear && !isTollFree
                }
            if (candidate != null) return candidate
        }
        return null
    }

    fun classify(
        address: String,
        body: String,
        contactName: String?,
        existingRule: SenderRuleEntity?
    ): ClassificationResult {
        val lowerBody = body.lowercase()
        val otp = extractOtp(body)
        val isBill = billKeywords.any { lowerBody.contains(it) }

        // Highest priority: explicit user custom rule ("Move To" or "Block")
        if (existingRule != null) {
            return ClassificationResult(
                category = existingRule.targetCategory,
                isBlocked = existingRule.isBlocked,
                otpCode = otp,
                isBillMessage = isBill
            )
        }

        // If message is a KMS-BILL forwarded message, treat as Transaction so bill workflow works
        if (lowerBody.contains("[kms-bill")) {
            return ClassificationResult(
                category = SmsCategory.TRANSACTION,
                isBlocked = false,
                otpCode = otp,
                isBillMessage = true
            )
        }

        // Known phone contact or personal multi-recipient group chat -> Personal
        if (!contactName.isNullOrBlank() || address.contains(",")) {
            return ClassificationResult(
                category = SmsCategory.PERSONAL,
                isBlocked = false,
                otpCode = otp,
                isBillMessage = isBill
            )
        }

        // Any verified OTP message always belongs in Transactions (never muted in Promotions)
        if (otp != null) {
            return ClassificationResult(
                category = SmsCategory.TRANSACTION,
                isBlocked = false,
                otpCode = otp,
                isBillMessage = isBill
            )
        }

        var txnScore = 0
        var promoScore = 0

        val senderUpper = address.trim().uppercase()
        val isBusinessHeader = dltHeaderRegex.matches(senderUpper) || alphanumericSenderRegex.matches(senderUpper)
        val isNumericPhone = phoneNumberRegex.matches(address.trim())

        // Indian DLT suffix convention: -P (Promotional), -T/-S/-G (Transactional/Service/Govt)
        if (senderUpper.endsWith("-P")) promoScore += 5
        if (senderUpper.endsWith("-T") || senderUpper.endsWith("-S") || senderUpper.endsWith("-G")) txnScore += 2

        for (kw in transactionKeywords) {
            if (lowerBody.contains(kw)) txnScore += 2
        }
        for (kw in promoKeywords) {
            if (lowerBody.contains(kw)) promoScore += 2
        }

        // Currency amount presence boosts transactional score if not overwhelmingly promo
        if (Regex("(?i)(?:rs\\.?|inr|₹|\\$|usd|eur)\\s?[\\d,]+(?:\\.\\d{1,2})?").containsMatchIn(body)) {
            txnScore += 2
        }

        val category = when {
            txnScore >= 3 && txnScore >= promoScore -> SmsCategory.TRANSACTION
            promoScore >= 3 && promoScore > txnScore -> SmsCategory.PROMOTION
            isBusinessHeader && promoScore > 0 -> SmsCategory.PROMOTION
            isBusinessHeader && txnScore > 0 -> SmsCategory.TRANSACTION
            isBusinessHeader -> SmsCategory.PROMOTION
            isNumericPhone -> SmsCategory.PERSONAL
            else -> SmsCategory.PERSONAL
        }

        return ClassificationResult(
            category = category,
            isBlocked = false,
            otpCode = otp,
            isBillMessage = isBill
        )
    }
}
