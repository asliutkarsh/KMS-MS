package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ScheduledStatus
import com.example.data.model.SmsCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms_messages WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun observeAllActiveMessages(): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages WHERE threadKey = :threadKey AND isDeleted = 0 ORDER BY timestamp ASC")
    fun observeMessagesForThread(threadKey: String): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages WHERE isDeleted = 0 AND isBlocked = 1 ORDER BY timestamp DESC")
    fun observeBlockedMessages(): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): SmsMessageEntity?

    @Query("SELECT * FROM sms_messages WHERE systemSmsId = :systemSmsId LIMIT 1")
    suspend fun getMessageBySystemId(systemSmsId: Long): SmsMessageEntity?

    @Query("SELECT * FROM sms_messages WHERE isDeleted = 0")
    suspend fun getAllActiveSnapshot(): List<SmsMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: SmsMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(messages: List<SmsMessageEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(messages: List<SmsMessageEntity>)

    @Query("UPDATE sms_messages SET isRead = :isRead WHERE threadKey = :threadKey")
    suspend fun setThreadRead(threadKey: String, isRead: Boolean)

    @Query("UPDATE sms_messages SET isStarred = :isStarred WHERE id = :id")
    suspend fun setMessageStarred(id: Long, isStarred: Boolean)

    @Query("UPDATE sms_messages SET isStarred = :isStarred WHERE threadKey = :threadKey")
    suspend fun setThreadStarred(threadKey: String, isStarred: Boolean)

    @Query("UPDATE sms_messages SET category = :category, isBlocked = 0 WHERE threadKey = :threadKey")
    suspend fun moveThreadToCategory(threadKey: String, category: SmsCategory)

    @Query("UPDATE sms_messages SET isBlocked = :isBlocked WHERE threadKey = :threadKey")
    suspend fun setThreadBlocked(threadKey: String, isBlocked: Boolean)

    @Query("UPDATE sms_messages SET groupName = :groupName WHERE threadKey = :threadKey")
    suspend fun setThreadGroupName(threadKey: String, groupName: String?)

    @Query("UPDATE sms_messages SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteMessage(id: Long)

    @Query("UPDATE sms_messages SET isDeleted = 0 WHERE id = :id")
    suspend fun restoreMessage(id: Long)

    @Query("UPDATE sms_messages SET isDeleted = 1 WHERE threadKey = :threadKey")
    suspend fun softDeleteThread(threadKey: String)

    @Query("UPDATE sms_messages SET isDeleted = 0 WHERE threadKey = :threadKey")
    suspend fun restoreThread(threadKey: String)

    @Query("DELETE FROM sms_messages WHERE otpCode IS NOT NULL AND timestamp < :cutoffTimestamp AND isStarred = 0")
    suspend fun purgeOldOtps(cutoffTimestamp: Long): Int

    @Query("DELETE FROM sms_messages WHERE category = 'PROMOTION' AND timestamp < :cutoffTimestamp AND isStarred = 0")
    suspend fun purgeOldPromotions(cutoffTimestamp: Long): Int

    @Query("DELETE FROM sms_messages WHERE isBlocked = 1 AND timestamp < :cutoffTimestamp")
    suspend fun purgeOldBlocked(cutoffTimestamp: Long): Int

    @Query("SELECT systemSmsId FROM sms_messages WHERE otpCode IS NOT NULL AND timestamp < :cutoffTimestamp AND isStarred = 0 AND systemSmsId IS NOT NULL")
    suspend fun getOldOtpSystemIds(cutoffTimestamp: Long): List<Long>

    @Query("SELECT systemSmsId FROM sms_messages WHERE category = 'PROMOTION' AND timestamp < :cutoffTimestamp AND isStarred = 0 AND systemSmsId IS NOT NULL")
    suspend fun getOldPromoSystemIds(cutoffTimestamp: Long): List<Long>

    @Query("SELECT systemSmsId FROM sms_messages WHERE isBlocked = 1 AND timestamp < :cutoffTimestamp AND systemSmsId IS NOT NULL")
    suspend fun getOldBlockedSystemIds(cutoffTimestamp: Long): List<Long>
}

@Dao
interface SenderRuleDao {
    @Query("SELECT * FROM sender_rules ORDER BY updatedAt DESC")
    fun observeAllRules(): Flow<List<SenderRuleEntity>>

    @Query("SELECT * FROM sender_rules WHERE isBlocked = 1 ORDER BY updatedAt DESC")
    fun observeBlockedRules(): Flow<List<SenderRuleEntity>>

    @Query("SELECT * FROM sender_rules WHERE normalizedSender = :sender LIMIT 1")
    suspend fun getRule(sender: String): SenderRuleEntity?

    @Query("SELECT * FROM sender_rules")
    suspend fun getAllSnapshot(): List<SenderRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: SenderRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<SenderRuleEntity>)

    @Query("DELETE FROM sender_rules WHERE normalizedSender = :sender")
    suspend fun deleteRule(sender: String)
}

@Dao
interface FinanceDao {
    @Query("SELECT * FROM finance_accounts ORDER BY lastUpdated DESC")
    fun observeAccounts(): Flow<List<FinanceAccountEntity>>

    @Query("SELECT * FROM finance_accounts WHERE accountKey = :accountKey LIMIT 1")
    fun observeAccount(accountKey: String): Flow<FinanceAccountEntity?>

    @Query("SELECT * FROM finance_accounts WHERE accountKey = :accountKey LIMIT 1")
    suspend fun getAccount(accountKey: String): FinanceAccountEntity?

    @Query("SELECT * FROM finance_accounts")
    suspend fun getAllAccountsSnapshot(): List<FinanceAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: FinanceAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<FinanceAccountEntity>)

    @Query("SELECT * FROM finance_transactions ORDER BY timestamp DESC")
    fun observeAllTransactions(): Flow<List<FinanceTransactionEntity>>

    @Query("SELECT * FROM finance_transactions WHERE accountKey = :accountKey ORDER BY timestamp DESC")
    fun observeTransactionsForAccount(accountKey: String): Flow<List<FinanceTransactionEntity>>

    @Query("SELECT COUNT(*) FROM finance_transactions WHERE smsId = :smsId")
    suspend fun countForSms(smsId: Long): Int

    @Query("SELECT * FROM finance_transactions")
    suspend fun getAllTransactionsSnapshot(): List<FinanceTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(txn: FinanceTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(txns: List<FinanceTransactionEntity>)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY dueTimestamp ASC")
    fun observeAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT COUNT(*) FROM reminders WHERE smsId = :smsId")
    suspend fun countForSms(smsId: Long): Int

    @Query("SELECT * FROM reminders")
    suspend fun getAllSnapshot(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun observeOrders(): Flow<List<OrderEntity>>

    @Query("SELECT COUNT(*) FROM orders WHERE smsId = :smsId")
    suspend fun countForSms(smsId: Long): Int

    @Query("SELECT * FROM orders")
    suspend fun getAllSnapshot(): List<OrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: OrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<OrderEntity>)
}

@Dao
interface OfferDao {
    @Query("SELECT * FROM offers ORDER BY timestamp DESC")
    fun observeOffers(): Flow<List<OfferEntity>>

    @Query("SELECT COUNT(*) FROM offers WHERE smsId = :smsId")
    suspend fun countForSms(smsId: Long): Int

    @Query("SELECT * FROM offers")
    suspend fun getAllSnapshot(): List<OfferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(offer: OfferEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(offers: List<OfferEntity>)

    @Query("DELETE FROM offers WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ScheduledSmsDao {
    @Query("SELECT * FROM scheduled_sms ORDER BY scheduledTimestamp ASC")
    fun observeScheduled(): Flow<List<ScheduledSmsEntity>>

    @Query("SELECT * FROM scheduled_sms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduledSmsEntity?

    @Query("SELECT * FROM scheduled_sms")
    suspend fun getAllSnapshot(): List<ScheduledSmsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScheduledSmsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ScheduledSmsEntity>)

    @Query("UPDATE scheduled_sms SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ScheduledStatus)

    @Query("UPDATE scheduled_sms SET workRequestId = :workId WHERE id = :id")
    suspend fun updateWorkId(id: Long, workId: String)

    @Query("DELETE FROM scheduled_sms WHERE id = :id")
    suspend fun deleteById(id: Long)
}
