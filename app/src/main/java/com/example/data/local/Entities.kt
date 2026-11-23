package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.AccountType
import com.example.data.model.ReminderType
import com.example.data.model.ScheduledStatus
import com.example.data.model.SmsCategory
import com.example.data.model.TransactionType
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "sms_messages",
    indices = [
        Index(value = ["address"]),
        Index(value = ["threadKey"]),
        Index(value = ["systemSmsId"], unique = true)
    ]
)
data class SmsMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemSmsId: Long? = null,
    val threadKey: String,
    val address: String,
    val contactName: String? = null,
    val body: String,
    val timestamp: Long,
    val isIncoming: Boolean = true,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val category: SmsCategory = SmsCategory.PERSONAL,
    val isBlocked: Boolean = false,
    val isDeleted: Boolean = false,
    val otpCode: String? = null,
    val simSubscriptionId: Int = -1,
    val attachmentUri: String? = null,
    val groupName: String? = null,
    val isBillMessage: Boolean = false
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "sender_rules")
data class SenderRuleEntity(
    @PrimaryKey val normalizedSender: String,
    val displayAddress: String,
    val targetCategory: SmsCategory,
    val isBlocked: Boolean = false,
    val customGroupName: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "finance_accounts")
data class FinanceAccountEntity(
    @PrimaryKey val accountKey: String,
    val institutionName: String,
    val accountType: AccountType,
    val maskedNumber: String,
    val lastKnownBalance: Double? = null,
    val currencySymbol: String = "₹",
    val lastUpdated: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "finance_transactions",
    indices = [Index(value = ["accountKey"]), Index(value = ["smsId"])]
)
data class FinanceTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsId: Long,
    val accountKey: String,
    val amount: Double,
    val currencySymbol: String = "₹",
    val type: TransactionType,
    val merchant: String,
    val paymentMode: String,
    val expenseCategory: String,
    val balanceAfter: Double? = null,
    val timestamp: Long
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "reminders", indices = [Index(value = ["smsId"])])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsId: Long? = null,
    val type: ReminderType,
    val title: String,
    val subtitle: String,
    val dueTimestamp: Long,
    val amount: Double? = null,
    val currencySymbol: String = "₹",
    val pnrNumber: String? = null,
    val trainNumber: String? = null,
    val stationCode: String? = null,
    val actionUrl: String? = null,
    val isCompleted: Boolean = false,
    val isForwardedBill: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "orders", indices = [Index(value = ["smsId"])])
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsId: Long,
    val merchant: String,
    val orderId: String,
    val status: String,
    val trackingSummary: String,
    val timestamp: Long
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "offers", indices = [Index(value = ["smsId"])])
data class OfferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsId: Long,
    val merchant: String,
    val couponCode: String,
    val discountSummary: String,
    val expiryText: String,
    val timestamp: Long
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "scheduled_sms")
data class ScheduledSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipientAddress: String,
    val recipientName: String? = null,
    val body: String,
    val scheduledTimestamp: Long,
    val simSubscriptionId: Int = -1,
    val status: ScheduledStatus = ScheduledStatus.PENDING,
    val workRequestId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
