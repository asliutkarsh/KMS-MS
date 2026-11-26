package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.backup.EncryptedBackupManager
import com.example.backup.ExportFolder
import com.example.backup.KmsBackupPayload
import com.example.backup.SmsXmlExporter
import com.example.data.local.FinanceAccountEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.KmsDatabase
import com.example.data.local.OfferEntity
import com.example.data.local.OrderEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.ScheduledSmsEntity
import com.example.data.local.SenderRuleEntity
import com.example.data.local.SmsMessageEntity
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.ReminderType
import com.example.data.model.RetentionPeriod
import com.example.data.model.ScheduledStatus
import com.example.data.model.SmsCategory
import com.example.intelligence.SmartExtractorEngine
import com.example.intelligence.SmsClassifierEngine
import com.example.telephony.DeviceSmsSyncManager
import com.example.telephony.KmsNotificationManager
import com.example.telephony.RawDeviceSms
import com.example.telephony.SimSlotInfo
import com.example.worker.FullSmsSyncWorker
import com.example.worker.ScheduledSmsWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class CleanupSummary(
    val otpDeleted: Int,
    val promoDeleted: Int,
    val blockedDeleted: Int
) {
    val total: Int get() = otpDeleted + promoDeleted + blockedDeleted
}

data class ForegroundSyncResult(
    val newlyImported: Int,
    val totalDeviceMessages: Int,
    val scheduledBackgroundSync: Boolean
)

class KmsRepository(
    private val context: Context,
    private val db: KmsDatabase,
    private val prefsRepo: UserPreferencesRepository,
    private val syncManager: DeviceSmsSyncManager,
    private val notificationManager: KmsNotificationManager,
    private val backupManager: EncryptedBackupManager,
    private val xmlExporter: SmsXmlExporter = SmsXmlExporter(context)
) {
    val allActiveMessages: Flow<List<SmsMessageEntity>> = db.smsDao().observeAllActiveMessages()
    val blockedMessages: Flow<List<SmsMessageEntity>> = db.smsDao().observeBlockedMessages()
    val senderRules: Flow<List<SenderRuleEntity>> = db.senderRuleDao().observeAllRules()
    val blockedRules: Flow<List<SenderRuleEntity>> = db.senderRuleDao().observeBlockedRules()
    val financeAccounts: Flow<List<FinanceAccountEntity>> = db.financeDao().observeAccounts()
    val allTransactions: Flow<List<FinanceTransactionEntity>> = db.financeDao().observeAllTransactions()
    val reminders: Flow<List<ReminderEntity>> = db.reminderDao().observeAllReminders()
    val orders: Flow<List<OrderEntity>> = db.orderDao().observeOrders()
    val offers: Flow<List<OfferEntity>> = db.offerDao().observeOffers()
    val scheduledMessages: Flow<List<ScheduledSmsEntity>> = db.scheduledSmsDao().observeScheduled()
    val preferences: Flow<UserPreferences> = prefsRepo.preferencesFlow

    fun observeThread(threadKey: String): Flow<List<SmsMessageEntity>> =
        db.smsDao().observeMessagesForThread(threadKey)

    fun observeAccount(accountKey: String): Flow<FinanceAccountEntity?> =
        db.financeDao().observeAccount(accountKey)

    fun observeTransactionsForAccount(accountKey: String): Flow<List<FinanceTransactionEntity>> =
        db.financeDao().observeTransactionsForAccount(accountKey)

    fun isDefaultSmsApp(): Boolean = syncManager.isDefaultSmsApp()
    fun hasReadSmsPermission(): Boolean = syncManager.hasReadSmsPermission()
    fun getActiveSimSlots(): List<SimSlotInfo> = syncManager.getActiveSimSlots()

    suspend fun getTotalDeviceSmsCount(): Int = withContext(Dispatchers.IO) {
        syncManager.getTotalDeviceSmsCount()
    }

    /**
     * Fast foreground pass: syncs up to the newest 600 messages immediately so the UI is responsive,
     * checks total device SMS count, and schedules [FullSmsSyncWorker] in the background when
     * older messages remain beyond the 600-message foreground window.
     */
    suspend fun syncFromDeviceInbox(): ForegroundSyncResult = withContext(Dispatchers.IO) {
        if (!syncManager.hasReadSmsPermission()) {
            return@withContext ForegroundSyncResult(0, 0, false)
        }
        val totalDeviceCount = syncManager.getTotalDeviceSmsCount()
        val rawList = syncManager.readDeviceSmsInbox(limit = FOREGROUND_SYNC_LIMIT)
        val existingIds = db.smsDao().getExistingSystemIds().toHashSet()
        val ruleCache = mutableMapOf<String, SenderRuleEntity?>()
        val newlyIndexed = insertSyncedMessages(rawList, existingIds, ruleCache)

        val syncedLinkedCount = db.smsDao().getSystemLinkedCount()
        val hasRemainingOlder = totalDeviceCount > FOREGROUND_SYNC_LIMIT && syncedLinkedCount < totalDeviceCount
        if (hasRemainingOlder) {
            FullSmsSyncWorker.enqueue(
                context = context,
                startOffset = FOREGROUND_SYNC_LIMIT,
                totalEstimate = totalDeviceCount,
                replaceExisting = false
            )
        }
        ForegroundSyncResult(
            newlyImported = newlyIndexed,
            totalDeviceMessages = totalDeviceCount,
            scheduledBackgroundSync = hasRemainingOlder
        )
    }

    /**
     * Unlimited background sync invoked by [FullSmsSyncWorker]. Streams all SMS messages
     * from the device telephony database in batches without any count cap.
     */
    suspend fun syncAllRemainingInBackground(
        startOffset: Int = 0,
        onProgress: suspend (processed: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        if (!syncManager.hasReadSmsPermission()) return@withContext 0
        val existingIds = db.smsDao().getExistingSystemIds().toHashSet()
        val ruleCache = mutableMapOf<String, SenderRuleEntity?>()
        var newlyIndexedTotal = 0

        syncManager.streamDeviceSmsBatches(
            startOffset = startOffset,
            batchSize = BACKGROUND_BATCH_SIZE
        ) { processedSoFar, totalCount, batch ->
            newlyIndexedTotal += insertSyncedMessages(batch, existingIds, ruleCache)
            onProgress(processedSoFar, totalCount)
        }
        newlyIndexedTotal
    }

    private suspend fun insertSyncedMessages(
        batch: List<RawDeviceSms>,
        existingIds: MutableSet<Long>,
        ruleCache: MutableMap<String, SenderRuleEntity?>
    ): Int {
        var insertedCount = 0
        for (raw in batch) {
            if (!existingIds.add(raw.systemSmsId)) continue
            val normalized = SmsClassifierEngine.normalizeSender(raw.address)
            val rule = ruleCache.getOrPut(normalized) { db.senderRuleDao().getRule(normalized) }
            val classification = SmsClassifierEngine.classify(
                address = raw.address,
                body = raw.body,
                contactName = raw.contactName,
                existingRule = rule
            )
            val entity = SmsMessageEntity(
                systemSmsId = raw.systemSmsId,
                threadKey = normalized,
                address = raw.address,
                contactName = raw.contactName,
                body = raw.body,
                timestamp = raw.timestamp,
                isIncoming = raw.isIncoming,
                isRead = raw.isRead,
                category = classification.category,
                isBlocked = classification.isBlocked,
                otpCode = classification.otpCode,
                simSubscriptionId = raw.simSubscriptionId,
                groupName = rule?.customGroupName,
                isBillMessage = classification.isBillMessage
            )
            val insertedId = db.smsDao().insert(entity)
            if (insertedId > 0) {
                insertedCount++
                runSmartExtractions(entity.copy(id = insertedId))
            }
        }
        return insertedCount
    }

    companion object {
        const val FOREGROUND_SYNC_LIMIT = 600
        const val BACKGROUND_BATCH_SIZE = 150
    }

    suspend fun ingestIncomingSms(
        address: String,
        body: String,
        timestamp: Long,
        simSubscriptionId: Int,
        writeToSystemProvider: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val contactName = syncManager.resolveContactName(address)
        val normalized = SmsClassifierEngine.normalizeSender(address)
        val rule = db.senderRuleDao().getRule(normalized)
        val classification = SmsClassifierEngine.classify(address, body, contactName, rule)

        val systemId = if (writeToSystemProvider) {
            syncManager.insertIntoSystemProvider(
                address = address,
                body = body,
                timestamp = timestamp,
                isIncoming = true,
                isRead = false,
                simSubscriptionId = simSubscriptionId
            )
        } else null

        val entity = SmsMessageEntity(
            systemSmsId = systemId,
            threadKey = normalized,
            address = address,
            contactName = contactName,
            body = body,
            timestamp = timestamp,
            isIncoming = true,
            isRead = false,
            category = classification.category,
            isBlocked = classification.isBlocked,
            otpCode = classification.otpCode,
            simSubscriptionId = simSubscriptionId,
            groupName = rule?.customGroupName,
            isBillMessage = classification.isBillMessage
        )
        val insertedId = db.smsDao().insert(entity)
        val saved = entity.copy(id = insertedId)
        runSmartExtractions(saved)

        val prefs = prefsRepo.getSnapshot()
        notificationManager.notifyIncomingSms(saved, prefs)
        insertedId
    }

    suspend fun sendSms(
        recipientAddress: String,
        body: String,
        simSubscriptionId: Int,
        attachmentUri: String?
    ): Result<Long> = withContext(Dispatchers.IO) {
        val prefs = prefsRepo.getSnapshot()
        val finalBody = if (prefs.signatureEnabled && prefs.signatureText.isNotBlank() &&
            !body.endsWith(prefs.signatureText)
        ) {
            "$body\n— ${prefs.signatureText.trim()}"
        } else {
            body
        }

        val sendResult = syncManager.sendSms(recipientAddress, finalBody, simSubscriptionId)
        if (sendResult.isFailure) {
            return@withContext Result.failure(sendResult.exceptionOrNull() ?: IllegalStateException("Send failed"))
        }

        val systemId = sendResult.getOrNull()
        val normalized = SmsClassifierEngine.normalizeSender(recipientAddress)
        val rule = db.senderRuleDao().getRule(normalized)
        val contactName = syncManager.resolveContactName(recipientAddress)
        val now = System.currentTimeMillis()
        val entity = SmsMessageEntity(
            systemSmsId = systemId,
            threadKey = normalized,
            address = recipientAddress,
            contactName = contactName,
            body = finalBody,
            timestamp = now,
            isIncoming = false,
            isRead = true,
            category = rule?.targetCategory ?: SmsCategory.PERSONAL,
            isBlocked = false,
            simSubscriptionId = simSubscriptionId,
            attachmentUri = attachmentUri,
            groupName = rule?.customGroupName
        )
        val id = db.smsDao().insert(entity)
        Result.success(id)
    }

    suspend fun forwardBillToContact(
        recipientAddress: String,
        billTitle: String,
        amount: Double?,
        dueTimestamp: Long,
        originalSnippet: String,
        simSubscriptionId: Int = -1
    ): Result<Long> {
        val amtText = amount?.let { String.format("%.2f", it) } ?: "0"
        val formattedBody = buildString {
            append("Bill payment reminder: $billTitle\n")
            if (amount != null && amount > 0) append("Amount: ₹$amtText\n")
            if (originalSnippet.isNotBlank()) append("${originalSnippet.take(100)}\n")
            append("[KMS-BILL | Title: $billTitle | Amt: $amtText | Due: $dueTimestamp]")
        }
        return sendSms(recipientAddress, formattedBody, simSubscriptionId, null)
    }

    suspend fun scheduleSms(
        recipientAddress: String,
        recipientName: String?,
        body: String,
        scheduledTimestamp: Long,
        simSubscriptionId: Int
    ): Long = withContext(Dispatchers.IO) {
        val id = db.scheduledSmsDao().insert(
            ScheduledSmsEntity(
                recipientAddress = recipientAddress,
                recipientName = recipientName,
                body = body,
                scheduledTimestamp = scheduledTimestamp,
                simSubscriptionId = simSubscriptionId,
                status = ScheduledStatus.PENDING
            )
        )
        val workId = ScheduledSmsWorker.enqueue(context, id, scheduledTimestamp)
        db.scheduledSmsDao().updateWorkId(id, workId)
        id
    }

    suspend fun cancelScheduledSms(item: ScheduledSmsEntity) = withContext(Dispatchers.IO) {
        ScheduledSmsWorker.cancel(context, item.workRequestId)
        db.scheduledSmsDao().updateStatus(item.id, ScheduledStatus.CANCELLED)
    }

    suspend fun deleteScheduledSms(item: ScheduledSmsEntity) = withContext(Dispatchers.IO) {
        ScheduledSmsWorker.cancel(context, item.workRequestId)
        db.scheduledSmsDao().deleteById(item.id)
    }

    suspend fun setThreadRead(threadKey: String, isRead: Boolean) = withContext(Dispatchers.IO) {
        db.smsDao().setThreadRead(threadKey, isRead)
    }

    suspend fun setThreadStarred(threadKey: String, starred: Boolean) = withContext(Dispatchers.IO) {
        db.smsDao().setThreadStarred(threadKey, starred)
    }

    suspend fun setMessageStarred(id: Long, starred: Boolean) = withContext(Dispatchers.IO) {
        db.smsDao().setMessageStarred(id, starred)
    }

    suspend fun moveSenderToCategory(threadKey: String, displayAddress: String, targetCategory: SmsCategory) =
        withContext(Dispatchers.IO) {
            val existing = db.senderRuleDao().getRule(threadKey)
            db.senderRuleDao().upsert(
                SenderRuleEntity(
                    normalizedSender = threadKey,
                    displayAddress = displayAddress,
                    targetCategory = targetCategory,
                    isBlocked = false,
                    customGroupName = existing?.customGroupName
                )
            )
            db.smsDao().moveThreadToCategory(threadKey, targetCategory)
        }

    suspend fun setSenderBlocked(threadKey: String, displayAddress: String, blocked: Boolean, currentCategory: SmsCategory) =
        withContext(Dispatchers.IO) {
            val existing = db.senderRuleDao().getRule(threadKey)
            db.senderRuleDao().upsert(
                SenderRuleEntity(
                    normalizedSender = threadKey,
                    displayAddress = displayAddress,
                    targetCategory = existing?.targetCategory ?: currentCategory,
                    isBlocked = blocked,
                    customGroupName = existing?.customGroupName
                )
            )
            db.smsDao().setThreadBlocked(threadKey, blocked)
        }

    suspend fun renameGroupThread(threadKey: String, displayAddress: String, newName: String) =
        withContext(Dispatchers.IO) {
            val existing = db.senderRuleDao().getRule(threadKey)
            db.senderRuleDao().upsert(
                SenderRuleEntity(
                    normalizedSender = threadKey,
                    displayAddress = displayAddress,
                    targetCategory = existing?.targetCategory ?: SmsCategory.PERSONAL,
                    isBlocked = existing?.isBlocked ?: false,
                    customGroupName = newName.trim().ifBlank { null }
                )
            )
            db.smsDao().setThreadGroupName(threadKey, newName.trim().ifBlank { null })
        }

    suspend fun softDeleteThread(threadKey: String) = withContext(Dispatchers.IO) {
        db.smsDao().softDeleteThread(threadKey)
    }

    suspend fun restoreThread(threadKey: String) = withContext(Dispatchers.IO) {
        db.smsDao().restoreThread(threadKey)
    }

    suspend fun deleteMessagePermanently(smsId: Long) = withContext(Dispatchers.IO) {
        val msg = db.smsDao().getMessageById(smsId)
        if (msg?.systemSmsId != null) {
            syncManager.deleteFromSystemProvider(msg.systemSmsId)
        }
        db.smsDao().softDeleteMessage(smsId)
    }

    suspend fun restoreMessage(smsId: Long) = withContext(Dispatchers.IO) {
        db.smsDao().restoreMessage(smsId)
    }

    suspend fun addCustomReminder(
        title: String,
        subtitle: String,
        type: ReminderType,
        dueTimestamp: Long,
        amount: Double?
    ) = withContext(Dispatchers.IO) {
        db.reminderDao().insert(
            ReminderEntity(
                smsId = null,
                type = type,
                title = title.trim(),
                subtitle = subtitle.trim().ifBlank { type.label },
                dueTimestamp = dueTimestamp,
                amount = amount
            )
        )
    }

    suspend fun setReminderCompleted(id: Long, completed: Boolean) = withContext(Dispatchers.IO) {
        db.reminderDao().setCompleted(id, completed)
    }

    suspend fun deleteReminder(id: Long) = withContext(Dispatchers.IO) {
        db.reminderDao().deleteById(id)
    }

    suspend fun deleteOffer(id: Long) = withContext(Dispatchers.IO) {
        db.offerDao().deleteById(id)
    }

    suspend fun runAutoCleanupNow(): CleanupSummary = withContext(Dispatchers.IO) {
        val prefs = prefsRepo.getSnapshot()
        val now = System.currentTimeMillis()
        var otpDeleted = 0
        var promoDeleted = 0
        var blockedDeleted = 0

        if (prefs.cleanupOtp != RetentionPeriod.NEVER) {
            val cutoff = now - prefs.cleanupOtp.durationMillis
            db.smsDao().getOldOtpSystemIds(cutoff).forEach { syncManager.deleteFromSystemProvider(it) }
            otpDeleted = db.smsDao().purgeOldOtps(cutoff)
        }
        if (prefs.cleanupPromotions != RetentionPeriod.NEVER) {
            val cutoff = now - prefs.cleanupPromotions.durationMillis
            db.smsDao().getOldPromoSystemIds(cutoff).forEach { syncManager.deleteFromSystemProvider(it) }
            promoDeleted = db.smsDao().purgeOldPromotions(cutoff)
        }
        if (prefs.cleanupBlocked != RetentionPeriod.NEVER) {
            val cutoff = now - prefs.cleanupBlocked.durationMillis
            db.smsDao().getOldBlockedSystemIds(cutoff).forEach { syncManager.deleteFromSystemProvider(it) }
            blockedDeleted = db.smsDao().purgeOldBlocked(cutoff)
        }
        CleanupSummary(otpDeleted, promoDeleted, blockedDeleted)
    }

    suspend fun exportEncryptedBackup(uri: Uri, passphrase: String): Result<Int> = withContext(Dispatchers.IO) {
        val payload = KmsBackupPayload(
            messages = db.smsDao().getAllActiveSnapshot(),
            senderRules = db.senderRuleDao().getAllSnapshot(),
            accounts = db.financeDao().getAllAccountsSnapshot(),
            transactions = db.financeDao().getAllTransactionsSnapshot(),
            reminders = db.reminderDao().getAllSnapshot(),
            orders = db.orderDao().getAllSnapshot(),
            offers = db.offerDao().getAllSnapshot(),
            scheduledMessages = db.scheduledSmsDao().getAllSnapshot(),
            preferences = prefsRepo.getSnapshot()
        )
        val result = backupManager.exportEncryptedBackup(uri, passphrase, payload)
        if (result.isSuccess) {
            prefsRepo.setLastBackupTimestamp(System.currentTimeMillis())
        }
        result
    }

    suspend fun importEncryptedBackup(uri: Uri, passphrase: String): Result<Int> = withContext(Dispatchers.IO) {
        val result = backupManager.importEncryptedBackup(uri, passphrase)
        val payload = result.getOrElse { return@withContext Result.failure(it) }
        db.smsDao().insertAllReplace(payload.messages)
        db.senderRuleDao().upsertAll(payload.senderRules)
        db.financeDao().upsertAccounts(payload.accounts)
        db.financeDao().insertTransactions(payload.transactions)
        db.reminderDao().insertAll(payload.reminders)
        db.orderDao().insertAll(payload.orders)
        db.offerDao().insertAll(payload.offers)
        db.scheduledSmsDao().insertAll(payload.scheduledMessages)
        prefsRepo.restoreFromBackup(payload.preferences)
        Result.success(payload.messages.size)
    }

    suspend fun getExportFolderCounts(): Map<ExportFolder, Int> = withContext(Dispatchers.IO) {
        SmsXmlExporter.countPerFolder(db.smsDao().getAllActiveOrderedSnapshot())
    }

    suspend fun exportMessagesToXml(uri: Uri, selectedFolders: Set<ExportFolder>): Result<Int> =
        withContext(Dispatchers.IO) {
            val allMessages = db.smsDao().getAllActiveOrderedSnapshot()
            xmlExporter.exportToUri(uri, allMessages, selectedFolders)
        }

    private suspend fun runSmartExtractions(sms: SmsMessageEntity) {
        if (sms.isBlocked) return
        val extracted = SmartExtractorEngine.extractAll(sms)
        extracted.account?.let { account ->
            val existing = db.financeDao().getAccount(account.accountKey)
            val merged = if (existing == null || account.lastUpdated >= existing.lastUpdated) {
                account.copy(lastKnownBalance = account.lastKnownBalance ?: existing?.lastKnownBalance)
            } else {
                existing.copy(lastKnownBalance = existing.lastKnownBalance ?: account.lastKnownBalance)
            }
            db.financeDao().upsertAccount(merged)
        }
        extracted.transaction?.let { txn ->
            if (db.financeDao().countForSms(sms.id) == 0) {
                db.financeDao().insertTransaction(txn)
            }
        }
        extracted.reminder?.let { rem ->
            if (db.reminderDao().countForSms(sms.id) == 0) {
                db.reminderDao().insert(rem)
            }
        }
        extracted.order?.let { order ->
            if (db.orderDao().countForSms(sms.id) == 0) {
                db.orderDao().insert(order)
            }
        }
        extracted.offer?.let { offer ->
            if (db.offerDao().countForSms(sms.id) == 0) {
                db.offerDao().insert(offer)
            }
        }
    }
}
