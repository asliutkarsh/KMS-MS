package com.example.intelligence

import com.example.data.local.FinanceAccountEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.OfferEntity
import com.example.data.local.OrderEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SmsMessageEntity
import com.example.data.model.AccountType
import com.example.data.model.ReminderType
import com.example.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ExtractionBundle(
    val account: FinanceAccountEntity? = null,
    val transaction: FinanceTransactionEntity? = null,
    val reminder: ReminderEntity? = null,
    val order: OrderEntity? = null,
    val offer: OfferEntity? = null
)

/**
 * 100% On-Device Financial, Travel/Bill Reminder, Order, and Offer Extraction Engine.
 */
object SmartExtractorEngine {

    private val amountRegex = Regex(
        "(?i)(?:rs\\.?|inr|₹|\\$|usd|eur)\\s*([\\d,]+(?:\\.\\d{1,2})?)"
    )
    private val balanceRegex = Regex(
        "(?i)(?:avl\\.?\\s*bal(?:ance)?|avail(?:able)?\\s*bal(?:ance)?|bal(?:ance)?(?:\\s+is)?|closing\\s+bal(?:ance)?|epf\\s+bal(?:ance)?)[:\\s\\-]*(?:rs\\.?|inr|₹|\\$)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"
    )
    private val accountNumRegex = Regex(
        "(?i)(?:a/c|acct|account|card|xx|x{2,4}|ending\\s*(?:in|with)?)\\s*(?:no\\.?\\s*)?[*xX\\-]*(\\d{3,4})\\b"
    )
    private val merchantRegex = Regex(
        "(?i)(?:at|to|towards|for|via|on|info[:\\s]+|vpa\\s+)\\s*([A-Za-z0-9&._\\- ]{2,28})(?=\\s+(?:on|via|ref|avl|bal|upi|using|dated|from|\\.)|$)"
    )
    private val pnrRegex = Regex("(?i)pnr[:\\s\\-#]*([A-Z0-9]{6,10})\\b")
    private val trainNumberRegex = Regex("(?i)(?:train\\s*(?:no\\.?)?\\s*[:\\-#]?\\s*)(\\d{5})\\b")
    private val flightNumberRegex = Regex("\\b([A-Z0-9]{2}[-\\s]?\\d{3,4})\\b")
    private val couponRegex = Regex(
        "(?i)(?:use\\s+code|promo\\s*code|coupon\\s*code|code)[:\\s\\-\"']*([A-Z0-9]{4,16})\\b"
    )
    private val discountRegex = Regex(
        "(?i)((?:flat|up\\s*to|upto|get|save|extra)\\s*(?:\\d{1,2}%\\s*off|(?:rs\\.?|inr|₹|\\$)\\s*[\\d,]+\\s*(?:off|cashback|discount)))"
    )
    private val orderIdRegex = Regex(
        "(?i)(?:order\\s*(?:id|no\\.?|#)?|awb|tracking\\s*(?:id|no\\.?)?)[:\\s\\-#]*([A-Z0-9\\-]{5,20})\\b"
    )
    private val dateRegex = Regex(
        "(?i)(\\d{1,2}[-/\\s](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|\\d{1,2})(?:[-/\\s]\\d{2,4})?)"
    )
    private val forwardedBillRegex = Regex(
        "\\[KMS-BILL\\s*\\|\\s*Title:\\s*([^|\\]]+?)\\s*\\|\\s*Amt:\\s*([^|\\]]+?)\\s*\\|\\s*Due:\\s*(\\d+)\\]"
    )

    fun extractAll(sms: SmsMessageEntity): ExtractionBundle {
        val (account, txn) = extractFinance(sms)
        val reminder = extractReminder(sms)
        val order = extractOrder(sms)
        val offer = extractOffer(sms)
        return ExtractionBundle(
            account = account,
            transaction = txn,
            reminder = reminder,
            order = order,
            offer = offer
        )
    }

    private fun extractFinance(sms: SmsMessageEntity): Pair<FinanceAccountEntity?, FinanceTransactionEntity?> {
        val body = sms.body
        val lower = body.lowercase()

        val isDebit = listOf("debited", "spent", "paid", "withdrawn", "deducted", "charged", "purchase of", "sent rs", "sent inr")
            .any { lower.contains(it) }
        val isCredit = listOf("credited", "received", "deposited", "refunded", "cashback of", "salary credited")
            .any { lower.contains(it) }
        val isEpfo = lower.contains("epfo") || lower.contains("epf balance") || lower.contains("pf passbook")
        val hasBalance = balanceRegex.containsMatchIn(body)

        if (!isDebit && !isCredit && !isEpfo && !hasBalance) return null to null

        val currency = when {
            body.contains("$") || lower.contains("usd") -> "$"
            body.contains("€") || lower.contains("eur") -> "€"
            else -> "₹"
        }

        val amount = amountRegex.find(body)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
        val balanceAfter = balanceRegex.find(body)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()

        if (amount == null && balanceAfter == null) return null to null

        val accountType = when {
            isEpfo -> AccountType.EPFO
            lower.contains("credit card") -> AccountType.CREDIT_CARD
            lower.contains("debit card") || lower.contains("atm") -> AccountType.DEBIT_CARD
            lower.contains("wallet") || lower.contains("paytm") || lower.contains("phonepe") ||
                lower.contains("gpay") || lower.contains("amazon pay") || lower.contains("mobikwik") -> AccountType.WALLET
            else -> AccountType.BANK
        }

        val institution = detectInstitution(sms.address, body, accountType)
        val digits = accountNumRegex.find(body)?.groupValues?.getOrNull(1)
            ?: if (accountType == AccountType.EPFO) "EPF" else "MAIN"
        val masked = if (digits.all { it.isDigit() }) "XX$digits" else digits
        val accountKey = "${accountType.name}_${institution.uppercase().replace(" ", "_")}_$masked"

        val account = FinanceAccountEntity(
            accountKey = accountKey,
            institutionName = institution,
            accountType = accountType,
            maskedNumber = masked,
            lastKnownBalance = balanceAfter ?: if (isEpfo) amount else null,
            currencySymbol = currency,
            lastUpdated = sms.timestamp
        )

        val transaction = if (amount != null && (isDebit || isCredit)) {
            val type = if (isCredit && !isDebit) TransactionType.CREDIT else TransactionType.DEBIT
            val merchant = cleanMerchant(merchantRegex.find(body)?.groupValues?.getOrNull(1), institution)
            val paymentMode = when {
                lower.contains("upi") -> "UPI"
                lower.contains("atm") -> "ATM"
                lower.contains("neft") -> "NEFT"
                lower.contains("imps") -> "IMPS"
                lower.contains("rtgs") -> "RTGS"
                lower.contains("credit card") -> "Credit Card"
                lower.contains("debit card") -> "Debit Card"
                else -> "Bank Transfer"
            }
            val expenseCategory = inferSpendingCategory(lower, merchant.lowercase(), paymentMode)
            FinanceTransactionEntity(
                smsId = sms.id,
                accountKey = accountKey,
                amount = amount,
                currencySymbol = currency,
                type = type,
                merchant = merchant,
                paymentMode = paymentMode,
                expenseCategory = expenseCategory,
                balanceAfter = balanceAfter,
                timestamp = sms.timestamp
            )
        } else null

        return account to transaction
    }

    private fun extractReminder(sms: SmsMessageEntity): ReminderEntity? {
        val body = sms.body
        val lower = body.lowercase()

        // 1. KMS-MS Forwarded Bill Tag
        forwardedBillRegex.find(body)?.let { m ->
            val title = m.groupValues[1].trim()
            val amt = m.groupValues[2].replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            val dueTs = m.groupValues[3].toLongOrNull() ?: (sms.timestamp + 3L * 86_400_000L)
            return ReminderEntity(
                smsId = sms.id,
                type = ReminderType.BILL,
                title = title,
                subtitle = "Forwarded bill from ${sms.contactName ?: sms.address}",
                dueTimestamp = dueTs,
                amount = amt,
                isForwardedBill = true
            )
        }

        val amount = amountRegex.find(body)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
        val parsedDateTs = parseApproximateFutureDate(body, sms.timestamp)

        // 2. Indian Railways / Train
        if (lower.contains("irctc") || lower.contains("train") && (lower.contains("pnr") || lower.contains("berth") || lower.contains("coach"))) {
            val pnr = pnrRegex.find(body)?.groupValues?.getOrNull(1)
            val trainNo = trainNumberRegex.find(body)?.groupValues?.getOrNull(1)
            val stationCode = OfflineRailwayIndex.detectStationCodeInText(body)
            return ReminderEntity(
                smsId = sms.id,
                type = ReminderType.TRAIN,
                title = buildString {
                    append("Train ")
                    if (trainNo != null) append("#$trainNo ")
                    append("Journey")
                }.trim(),
                subtitle = buildString {
                    if (pnr != null) append("PNR: $pnr")
                    if (stationCode != null) {
                        if (isNotEmpty()) append(" • ")
                        append("Station: $stationCode")
                    }
                    if (isEmpty()) append(body.take(80))
                },
                dueTimestamp = parsedDateTs,
                pnrNumber = pnr,
                trainNumber = trainNo,
                stationCode = stationCode
            )
        }

        // 3. Flight
        if ((lower.contains("flight") || lower.contains("boarding") || lower.contains("web check-in")) &&
            (lower.contains("pnr") || lower.contains("confirmed") || lower.contains("depart"))
        ) {
            val pnr = pnrRegex.find(body)?.groupValues?.getOrNull(1)
            val flightNo = flightNumberRegex.find(body)?.groupValues?.getOrNull(1)
            return ReminderEntity(
                smsId = sms.id,
                type = ReminderType.FLIGHT,
                title = if (flightNo != null) "Flight $flightNo" else "Upcoming Flight",
                subtitle = if (pnr != null) "PNR: $pnr • Tap for Web Check-in" else body.take(80),
                dueTimestamp = parsedDateTs,
                pnrNumber = pnr
            )
        }

        // 4. Bus / Hotel
        if (lower.contains("bus") && (lower.contains("booking") || lower.contains("boarding") || lower.contains("seat"))) {
            return ReminderEntity(
                smsId = sms.id,
                type = ReminderType.BUS,
                title = "Bus Trip Booking",
                subtitle = body.take(85),
                dueTimestamp = parsedDateTs
            )
        }
        if (lower.contains("hotel") && (lower.contains("check-in") || lower.contains("booking confirmed") || lower.contains("reservation"))) {
            return ReminderEntity(
                smsId = sms.id,
                type = ReminderType.HOTEL,
                title = "Hotel Check-in",
                subtitle = body.take(85),
                dueTimestamp = parsedDateTs
            )
        }

        // 5. Credit Card / Utility Bills
        if (listOf("bill due", "payment due", "due date", "total due", "electricity bill", "broadband bill", "postpaid bill")
                .any { lower.contains(it) }
        ) {
            val issuer = detectInstitution(sms.address, body, AccountType.BANK)
            return ReminderEntity(
                smsId = sms.id,
                type = ReminderType.BILL,
                title = "$issuer Bill Due",
                subtitle = body.take(90),
                dueTimestamp = parsedDateTs,
                amount = amount
            )
        }

        // 6. Mobile / DTH Recharge Expiry
        if (listOf("plan expires", "pack expires", "validity expires", "recharge due", "dth recharge")
                .any { lower.contains(it) }
        ) {
            val issuer = detectInstitution(sms.address, body, AccountType.WALLET)
            return ReminderEntity(
                smsId = sms.id,
                type = ReminderType.RECHARGE,
                title = "$issuer Plan Expiry",
                subtitle = body.take(85),
                dueTimestamp = parsedDateTs,
                amount = amount
            )
        }

        // 7. Movie Tickets & Doctor Appointments
        if ((lower.contains("movie") || lower.contains("bookmyshow") || lower.contains("pvr") || lower.contains("inox")) &&
            (lower.contains("ticket") || lower.contains("seat") || lower.contains("booking"))
        ) {
            return ReminderEntity(
                smsId = sms.id,
                type = ReminderType.MOVIE,
                title = "Movie Showtime",
                subtitle = body.take(85),
                dueTimestamp = parsedDateTs
            )
        }
        if (lower.contains("appointment") && (lower.contains("dr.") || lower.contains("doctor") || lower.contains("clinic") || lower.contains("hospital") || lower.contains("confirmed"))) {
            return ReminderEntity(
                smsId = sms.id,
                type = ReminderType.APPOINTMENT,
                title = "Appointment Reminder",
                subtitle = body.take(85),
                dueTimestamp = parsedDateTs
            )
        }

        return null
    }

    private fun extractOrder(sms: SmsMessageEntity): OrderEntity? {
        val lower = sms.body.lowercase()
        val status = when {
            lower.contains("delivered") -> "Delivered"
            lower.contains("out for delivery") -> "Out for Delivery"
            lower.contains("arriving today") -> "Arriving Today"
            lower.contains("shipped") || lower.contains("dispatched") -> "Shipped"
            lower.contains("order confirmed") || lower.contains("order placed") -> "Order Confirmed"
            else -> return null
        }
        val merchant = detectMerchantForOrder(sms.address, lower)
        val orderId = orderIdRegex.find(sms.body)?.groupValues?.getOrNull(1) ?: "ORD-${sms.id}"
        return OrderEntity(
            smsId = sms.id,
            merchant = merchant,
            orderId = orderId,
            status = status,
            trackingSummary = sms.body.take(120),
            timestamp = sms.timestamp
        )
    }

    private fun extractOffer(sms: SmsMessageEntity): OfferEntity? {
        val body = sms.body
        val lower = body.lowercase()
        val coupon = couponRegex.find(body)?.groupValues?.getOrNull(1)?.uppercase()
        val discount = discountRegex.find(body)?.groupValues?.getOrNull(1)?.trim()

        if (coupon == null && discount == null) return null
        if (lower.contains("otp") || lower.contains("verification code")) return null

        val merchant = detectInstitution(sms.address, body, AccountType.WALLET)
        val expiry = Regex("(?i)(?:valid\\s+(?:till|until|upto)|expires?\\s+on|ends?\\s+on)\\s+([A-Za-z0-9\\s,/\\-]{3,18})")
            .find(body)?.groupValues?.getOrNull(1)?.trim() ?: "Limited period"

        return OfferEntity(
            smsId = sms.id,
            merchant = merchant,
            couponCode = coupon ?: "AUTO-APPLY",
            discountSummary = discount ?: body.take(75),
            expiryText = expiry,
            timestamp = sms.timestamp
        )
    }

    private fun detectInstitution(address: String, body: String, fallbackType: AccountType): String {
        val combined = "$address $body".lowercase()
        val known = listOf(
            "hdfc" to "HDFC Bank",
            "icici" to "ICICI Bank",
            "sbi" to "State Bank of India",
            "axis" to "Axis Bank",
            "kotak" to "Kotak Mahindra",
            "indusind" to "IndusInd Bank",
            "yesbank" to "YES Bank",
            "pnb" to "Punjab National Bank",
            "bob" to "Bank of Baroda",
            "canara" to "Canara Bank",
            "idfc" to "IDFC FIRST Bank",
            "amex" to "American Express",
            "citi" to "Citibank",
            "chase" to "Chase Bank",
            "epfo" to "EPFO",
            "paytm" to "Paytm",
            "phonepe" to "PhonePe",
            "gpay" to "Google Pay",
            "amazon" to "Amazon",
            "flipkart" to "Flipkart",
            "swiggy" to "Swiggy",
            "zomato" to "Zomato",
            "jio" to "Jio",
            "airtel" to "Airtel",
            "vi " to "Vi Telecom",
            "myntra" to "Myntra",
            "uber" to "Uber",
            "ola" to "Ola"
        )
        known.firstOrNull { combined.contains(it.first) }?.let { return it.second }
        val cleanHeader = address.substringAfter("-").filter { it.isLetterOrDigit() }
        return if (cleanHeader.length >= 3) cleanHeader.uppercase() else fallbackType.label
    }

    private fun detectMerchantForOrder(address: String, lowerBody: String): String {
        val map = listOf(
            "amazon" to "Amazon",
            "flipkart" to "Flipkart",
            "myntra" to "Myntra",
            "ajio" to "AJIO",
            "nykaa" to "Nykaa",
            "meesho" to "Meesho",
            "bluedart" to "Blue Dart",
            "delhivery" to "Delhivery",
            "ekart" to "Ekart Logistics",
            "fedex" to "FedEx",
            "dhl" to "DHL",
            "bigbasket" to "BigBasket",
            "blinkit" to "Blinkit"
        )
        map.firstOrNull { lowerBody.contains(it.first) || address.lowercase().contains(it.first) }
            ?.let { return it.second }
        return address.substringAfter("-").ifBlank { "Courier Delivery" }
    }

    private fun cleanMerchant(raw: String?, fallback: String): String {
        val cleaned = raw?.trim()?.trimEnd('.', ',', ';', ':')
        return if (!cleaned.isNullOrBlank() && cleaned.length >= 2) cleaned else fallback
    }

    private fun inferSpendingCategory(lowerBody: String, lowerMerchant: String, mode: String): String {
        val text = "$lowerBody $lowerMerchant"
        return when {
            mode == "ATM" || text.contains("atm") -> "ATM Cash"
            listOf("swiggy", "zomato", "starbucks", "cafe", "restaurant", "pizza", "dining").any { text.contains(it) } -> "Dining"
            listOf("amazon", "flipkart", "myntra", "mart", "store", "shopping").any { text.contains(it) } -> "Shopping"
            listOf("uber", "ola", "irctc", "indigo", "airline", "metro", "fuel", "petrol").any { text.contains(it) } -> "Travel"
            listOf("electricity", "broadband", "jio", "airtel", "recharge", "bill", "water", "gas").any { text.contains(it) } -> "Utilities"
            listOf("salary", "refund", "cashback", "interest").any { text.contains(it) } -> "Income"
            else -> "General"
        }
    }

    private fun parseApproximateFutureDate(body: String, fallbackBase: Long): Long {
        val raw = dateRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
        if (raw != null) {
            val formats = listOf(
                "dd-MMM-yyyy", "dd MMM yyyy", "dd/MM/yyyy", "dd-MM-yyyy",
                "dd-MMM-yy", "dd MMM yy", "dd/MM/yy", "dd-MMM", "dd MMM"
            )
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            for (fmt in formats) {
                try {
                    val sdf = SimpleDateFormat(fmt, Locale.ENGLISH).apply { isLenient = false }
                    val parsed = sdf.parse(raw) ?: continue
                    val cal = Calendar.getInstance().apply { time = parsed }
                    if (!fmt.contains("y")) cal.set(Calendar.YEAR, currentYear)
                    cal.set(Calendar.HOUR_OF_DAY, 9)
                    cal.set(Calendar.MINUTE, 0)
                    return cal.timeInMillis
                } catch (_: Exception) {
                    // Try next format
                }
            }
        }
        return fallbackBase + 3L * 24L * 60L * 60L * 1000L
    }
}
