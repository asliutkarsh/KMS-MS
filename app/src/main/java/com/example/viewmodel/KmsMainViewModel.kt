package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.KmsMsApplication
import com.example.backup.ExportFolder
import com.example.backup.SmsXmlExporter
import com.example.data.local.FinanceAccountEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.OfferEntity
import com.example.data.local.OrderEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.ScheduledSmsEntity
import com.example.data.local.SenderRuleEntity
import com.example.data.local.SmsMessageEntity
import com.example.data.local.UserPreferences
import com.example.data.model.BackupReminderCadence
import com.example.data.model.InboxTab
import com.example.data.model.ReminderType
import com.example.data.model.RetentionPeriod
import com.example.data.model.SmsCategory
import com.example.data.model.SwipeAction
import com.example.data.model.ThemeMode
import com.example.data.model.TransactionType
import com.example.telephony.SimSlotInfo
import com.example.worker.FullSmsSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ThreadSummary(
    val threadKey: String,
    val displayTitle: String,
    val address: String,
    val lastSnippet: String,
    val lastTimestamp: Long,
    val unreadCount: Int,
    val isStarred: Boolean,
    val category: SmsCategory,
    val isBlocked: Boolean,
    val latestOtp: String?,
    val isGroup: Boolean,
    val messageCount: Int
)

data class FinanceDashboardState(
    val accounts: List<FinanceAccountEntity> = emptyList(),
    val recentTransactions: List<FinanceTransactionEntity> = emptyList(),
    val monthlyDebitTotal: Double = 0.0,
    val monthlyCreditTotal: Double = 0.0,
    val primaryCurrency: String = "₹"
)

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val undoThreadKey: String? = null,
    val undoThreadKeys: List<String> = emptyList(),
    val undoMessageId: Long? = null
)

data class BackgroundSyncProgress(
    val isRunning: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0
)

class KmsMainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KmsMsApplication
    private val repository = app.repository
    private val prefsRepo = app.preferencesRepository

    val preferences: StateFlow<UserPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _selectedInboxTab = MutableStateFlow(InboxTab.PERSONAL)
    val selectedInboxTab: StateFlow<InboxTab> = _selectedInboxTab.asStateFlow()

    private val _unreadOnly = MutableStateFlow(false)
    val unreadOnly: StateFlow<Boolean> = _unreadOnly.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isDefaultSmsApp = MutableStateFlow(repository.isDefaultSmsApp())
    val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    private val _hasSmsPermission = MutableStateFlow(repository.hasReadSmsPermission())
    val hasSmsPermission: StateFlow<Boolean> = _hasSmsPermission.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _simSlots = MutableStateFlow(repository.getActiveSimSlots())
    val simSlots: StateFlow<List<SimSlotInfo>> = _simSlots.asStateFlow()

    private val _financeUnlocked = MutableStateFlow(false)
    val financeUnlocked: StateFlow<Boolean> = _financeUnlocked.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<SnackbarEvent> = _snackbarEvents.asSharedFlow()

    val backgroundSyncProgress: StateFlow<BackgroundSyncProgress> = WorkManager.getInstance(app)
        .getWorkInfosForUniqueWorkFlow(FullSmsSyncWorker.UNIQUE_WORK_NAME)
        .map { infos ->
            val active = infos.firstOrNull {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }
            if (active != null) {
                BackgroundSyncProgress(
                    isRunning = true,
                    processed = active.progress.getInt(FullSmsSyncWorker.KEY_PROCESSED, 0),
                    total = active.progress.getInt(FullSmsSyncWorker.KEY_TOTAL, 0)
                )
            } else {
                BackgroundSyncProgress()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackgroundSyncProgress())

    val exportFolderCounts: StateFlow<Map<ExportFolder, Int>> = repository.allActiveMessages
        .map { messages -> SmsXmlExporter.countPerFolder(messages) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val totalIndexedMessageCount: StateFlow<Int> = repository.allActiveMessages
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val allThreads: StateFlow<List<ThreadSummary>> = repository.allActiveMessages
        .combine(repository.senderRules) { messages, rules ->
            val ruleMap = rules.associateBy { it.normalizedSender }
            messages
                .filter { !it.isBlocked }
                .groupBy { it.threadKey }
                .map { (key, list) ->
                    val latest = list.maxBy { it.timestamp }
                    val rule = ruleMap[key]
                    val title = rule?.customGroupName?.takeIf { it.isNotBlank() }
                        ?: latest.groupName?.takeIf { it.isNotBlank() }
                        ?: latest.contactName?.takeIf { it.isNotBlank() }
                        ?: latest.address
                    ThreadSummary(
                        threadKey = key,
                        displayTitle = title,
                        address = latest.address,
                        lastSnippet = latest.body,
                        lastTimestamp = latest.timestamp,
                        unreadCount = list.count { !it.isRead },
                        isStarred = list.any { it.isStarred },
                        category = rule?.targetCategory ?: latest.category,
                        isBlocked = false,
                        latestOtp = latest.otpCode,
                        isGroup = latest.address.contains(","),
                        messageCount = list.size
                    )
                }
                .sortedByDescending { it.lastTimestamp }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredThreads: StateFlow<List<ThreadSummary>> = combine(
        allThreads,
        selectedInboxTab,
        unreadOnly,
        searchQuery
    ) { threads, tab, onlyUnread, query ->
        val trimmed = query.trim().lowercase()
        threads.filter { thread ->
            val matchesTab = when (tab) {
                InboxTab.PERSONAL -> thread.category == SmsCategory.PERSONAL
                InboxTab.TRANSACTIONS -> thread.category == SmsCategory.TRANSACTION
                InboxTab.PROMOTIONS -> thread.category == SmsCategory.PROMOTION
                InboxTab.STARRED -> thread.isStarred
            }
            val matchesUnread = !onlyUnread || thread.unreadCount > 0
            val matchesQuery = trimmed.isBlank() ||
                thread.displayTitle.lowercase().contains(trimmed) ||
                thread.address.lowercase().contains(trimmed) ||
                thread.lastSnippet.lowercase().contains(trimmed) ||
                (thread.latestOtp?.contains(trimmed) == true)
            matchesTab && matchesUnread && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tabUnreadCounts: StateFlow<Map<InboxTab, Int>> = allThreads
        .combine(_unreadOnly) { threads, _ ->
            mapOf(
                InboxTab.PERSONAL to threads.filter { it.category == SmsCategory.PERSONAL }.sumOf { it.unreadCount },
                InboxTab.TRANSACTIONS to threads.filter { it.category == SmsCategory.TRANSACTION }.sumOf { it.unreadCount },
                InboxTab.PROMOTIONS to threads.filter { it.category == SmsCategory.PROMOTION }.sumOf { it.unreadCount },
                InboxTab.STARRED to threads.count { it.isStarred && it.unreadCount > 0 }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val blockedThreads: StateFlow<List<ThreadSummary>> = combine(
        repository.blockedMessages,
        repository.blockedRules
    ) { messages, rules ->
        val byKey = messages.groupBy { it.threadKey }
        val keys = (byKey.keys + rules.map { it.normalizedSender }).distinct()
        keys.map { key ->
            val msgs = byKey[key].orEmpty()
            val latest = msgs.maxByOrNull { it.timestamp }
            val rule = rules.firstOrNull { it.normalizedSender == key }
            ThreadSummary(
                threadKey = key,
                displayTitle = latest?.contactName ?: rule?.displayAddress ?: latest?.address ?: key,
                address = rule?.displayAddress ?: latest?.address ?: key,
                lastSnippet = latest?.body ?: "Blocked sender — future texts will be filtered here",
                lastTimestamp = latest?.timestamp ?: rule?.updatedAt ?: 0L,
                unreadCount = msgs.count { !it.isRead },
                isStarred = false,
                category = rule?.targetCategory ?: latest?.category ?: SmsCategory.PROMOTION,
                isBlocked = true,
                latestOtp = null,
                isGroup = false,
                messageCount = msgs.size
            )
        }.sortedByDescending { it.lastTimestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val financeState: StateFlow<FinanceDashboardState> = combine(
        repository.financeAccounts,
        repository.allTransactions
    ) { accounts, txns ->
        val monthAgo = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
        val recent30d = txns.filter { it.timestamp >= monthAgo }
        FinanceDashboardState(
            accounts = accounts,
            recentTransactions = txns.take(40),
            monthlyDebitTotal = recent30d.filter { it.type == TransactionType.DEBIT }.sumOf { it.amount },
            monthlyCreditTotal = recent30d.filter { it.type == TransactionType.CREDIT }.sumOf { it.amount },
            primaryCurrency = accounts.firstOrNull()?.currencySymbol ?: "₹"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceDashboardState())

    val reminders: StateFlow<List<ReminderEntity>> = repository.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val orders: StateFlow<List<OrderEntity>> = repository.orders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val offers: StateFlow<List<OfferEntity>> = repository.offers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scheduledMessages: StateFlow<List<ScheduledSmsEntity>> = repository.scheduledMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeThread(threadKey: String): Flow<List<SmsMessageEntity>> =
        repository.observeThread(threadKey)

    fun observeAccount(accountKey: String): Flow<FinanceAccountEntity?> =
        repository.observeAccount(accountKey)

    fun observeTransactionsForAccount(accountKey: String): Flow<List<FinanceTransactionEntity>> =
        repository.observeTransactionsForAccount(accountKey)

    fun selectInboxTab(tab: InboxTab) {
        _selectedInboxTab.value = tab
    }

    fun toggleUnreadOnly() {
        _unreadOnly.value = !_unreadOnly.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshDeviceStatusAndSync(silent: Boolean = true) {
        _isDefaultSmsApp.value = repository.isDefaultSmsApp()
        _hasSmsPermission.value = repository.hasReadSmsPermission()
        _simSlots.value = repository.getActiveSimSlots()
        if (_hasSmsPermission.value) {
            viewModelScope.launch {
                _isSyncing.value = true
                val result = repository.syncFromDeviceInbox()
                _isSyncing.value = false
                if (!silent) {
                    val msg = when {
                        result.scheduledBackgroundSync ->
                            "Synced recent messages (${result.newlyImported} new). Syncing all ${result.totalDeviceMessages} messages in background…"
                        result.newlyImported > 0 ->
                            "Synced ${result.newlyImported} new messages on-device"
                        else ->
                            "Inbox is up to date"
                    }
                    _snackbarEvents.tryEmit(SnackbarEvent(msg))
                } else if (result.scheduledBackgroundSync) {
                    _snackbarEvents.tryEmit(
                        SnackbarEvent("Syncing ${result.totalDeviceMessages} SMS messages in background (check notification bar)")
                    )
                }
            }
        }
    }

    fun triggerFullBackgroundSync() {
        _hasSmsPermission.value = repository.hasReadSmsPermission()
        if (!_hasSmsPermission.value) {
            _snackbarEvents.tryEmit(SnackbarEvent("SMS permission is required to sync device messages"))
            return
        }
        viewModelScope.launch {
            val total = repository.getTotalDeviceSmsCount()
            if (total == 0) {
                _snackbarEvents.tryEmit(SnackbarEvent("No SMS messages found on device"))
                return@launch
            }
            FullSmsSyncWorker.enqueue(
                context = app,
                startOffset = 0,
                totalEstimate = total,
                replaceExisting = true
            )
            _snackbarEvents.tryEmit(
                SnackbarEvent("Full sync started for $total messages — progress shown in notification bar")
            )
        }
    }

    fun markPromptedDefaultSms() {
        viewModelScope.launch { prefsRepo.markPromptedDefaultSms() }
    }

    fun markThreadRead(threadKey: String, isRead: Boolean = true, notifyUser: Boolean = false) {
        viewModelScope.launch {
            repository.setThreadRead(threadKey, isRead)
            if (notifyUser) {
                _snackbarEvents.tryEmit(
                    SnackbarEvent(if (isRead) "Marked as read" else "Marked as unread")
                )
            }
        }
    }

    fun toggleThreadStar(thread: ThreadSummary) {
        viewModelScope.launch {
            repository.setThreadStarred(thread.threadKey, !thread.isStarred)
            _snackbarEvents.tryEmit(
                SnackbarEvent(if (!thread.isStarred) "Starred ${thread.displayTitle}" else "Removed star")
            )
        }
    }

    fun toggleMessageStar(msg: SmsMessageEntity) {
        viewModelScope.launch { repository.setMessageStarred(msg.id, !msg.isStarred) }
    }

    fun moveThreadToCategory(thread: ThreadSummary, target: SmsCategory) {
        viewModelScope.launch {
            repository.moveSenderToCategory(thread.threadKey, thread.address, target)
            _snackbarEvents.tryEmit(
                SnackbarEvent("Moved ${thread.displayTitle} to ${target.label}. Future texts will auto-sort here.")
            )
        }
    }

    fun blockSender(thread: ThreadSummary) {
        viewModelScope.launch {
            repository.setSenderBlocked(thread.threadKey, thread.address, true, thread.category)
            _snackbarEvents.tryEmit(SnackbarEvent("Blocked ${thread.displayTitle}"))
        }
    }

    fun unblockSender(thread: ThreadSummary) {
        viewModelScope.launch {
            repository.setSenderBlocked(thread.threadKey, thread.address, false, thread.category)
            _snackbarEvents.tryEmit(SnackbarEvent("Unblocked ${thread.displayTitle}"))
        }
    }

    fun deleteThread(thread: ThreadSummary) {
        viewModelScope.launch {
            repository.softDeleteThread(thread.threadKey)
            _snackbarEvents.tryEmit(
                SnackbarEvent(
                    message = "Deleted conversation with ${thread.displayTitle}",
                    actionLabel = "Undo",
                    undoThreadKey = thread.threadKey
                )
            )
        }
    }

    fun undoDeleteThread(threadKey: String) {
        viewModelScope.launch { repository.restoreThread(threadKey) }
    }

    fun undoDeleteThreads(threadKeys: List<String>) {
        viewModelScope.launch {
            for (key in threadKeys) {
                repository.restoreThread(key)
            }
        }
    }

    fun markThreadsRead(threads: List<ThreadSummary>, isRead: Boolean) {
        if (threads.isEmpty()) return
        viewModelScope.launch {
            for (t in threads) {
                repository.setThreadRead(t.threadKey, isRead)
            }
            val count = threads.size
            val label = if (count == 1) threads.first().displayTitle else "$count conversations"
            _snackbarEvents.tryEmit(
                SnackbarEvent(if (isRead) "Marked $label as read" else "Marked $label as unread")
            )
        }
    }

    fun setThreadsStarred(threads: List<ThreadSummary>, starred: Boolean) {
        if (threads.isEmpty()) return
        viewModelScope.launch {
            for (t in threads) {
                repository.setThreadStarred(t.threadKey, starred)
            }
            val count = threads.size
            val msg = if (count == 1) {
                if (starred) "Starred ${threads.first().displayTitle}" else "Removed star"
            } else {
                if (starred) "Starred $count conversations" else "Removed star from $count conversations"
            }
            _snackbarEvents.tryEmit(SnackbarEvent(msg))
        }
    }

    fun moveThreadsToCategory(threads: List<ThreadSummary>, target: SmsCategory) {
        if (threads.isEmpty()) return
        viewModelScope.launch {
            for (t in threads) {
                repository.moveSenderToCategory(t.threadKey, t.address, target)
            }
            val count = threads.size
            val label = if (count == 1) threads.first().displayTitle else "$count senders"
            _snackbarEvents.tryEmit(
                SnackbarEvent("Moved $label to ${target.label}. Future texts will auto-sort here.")
            )
        }
    }

    fun blockSenders(threads: List<ThreadSummary>) {
        if (threads.isEmpty()) return
        viewModelScope.launch {
            for (t in threads) {
                repository.setSenderBlocked(t.threadKey, t.address, true, t.category)
            }
            val count = threads.size
            val label = if (count == 1) threads.first().displayTitle else "$count senders"
            _snackbarEvents.tryEmit(SnackbarEvent("Blocked $label"))
        }
    }

    fun deleteThreads(threads: List<ThreadSummary>) {
        if (threads.isEmpty()) return
        val keys = threads.map { it.threadKey }
        viewModelScope.launch {
            for (key in keys) {
                repository.softDeleteThread(key)
            }
            val msg = if (threads.size == 1) {
                "Deleted conversation with ${threads.first().displayTitle}"
            } else {
                "Deleted ${threads.size} conversations"
            }
            _snackbarEvents.tryEmit(
                SnackbarEvent(
                    message = msg,
                    actionLabel = "Undo",
                    undoThreadKeys = keys
                )
            )
        }
    }

    fun setMessagesStarred(messages: List<SmsMessageEntity>, starred: Boolean) {
        if (messages.isEmpty()) return
        viewModelScope.launch {
            for (m in messages) {
                repository.setMessageStarred(m.id, starred)
            }
        }
    }

    fun deleteMessages(messages: List<SmsMessageEntity>) {
        if (messages.isEmpty()) return
        viewModelScope.launch {
            for (m in messages) {
                repository.deleteMessagePermanently(m.id)
            }
            _snackbarEvents.tryEmit(
                SnackbarEvent(
                    if (messages.size == 1) "Message deleted" else "Deleted ${messages.size} messages"
                )
            )
        }
    }

    fun deleteMessage(smsId: Long) {
        viewModelScope.launch {
            repository.deleteMessagePermanently(smsId)
            _snackbarEvents.tryEmit(
                SnackbarEvent(
                    message = "Message deleted",
                    actionLabel = "Undo",
                    undoMessageId = smsId
                )
            )
        }
    }

    fun undoDeleteMessage(smsId: Long) {
        viewModelScope.launch { repository.restoreMessage(smsId) }
    }

    fun renameGroup(threadKey: String, address: String, newName: String) {
        viewModelScope.launch {
            repository.renameGroupThread(threadKey, address, newName)
            _snackbarEvents.tryEmit(SnackbarEvent("Group renamed"))
        }
    }

    fun sendSms(
        recipientAddress: String,
        body: String,
        simSubscriptionId: Int = -1,
        attachmentUri: String? = null,
        onSuccess: (String) -> Unit = {}
    ) {
        if (recipientAddress.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            val result = repository.sendSms(recipientAddress, body, simSubscriptionId, attachmentUri)
            if (result.isSuccess) {
                val threadKey = com.example.intelligence.SmsClassifierEngine.normalizeSender(recipientAddress)
                onSuccess(threadKey)
            } else {
                _snackbarEvents.tryEmit(
                    SnackbarEvent(result.exceptionOrNull()?.message ?: "Could not send SMS. Check SMS permissions.")
                )
            }
        }
    }

    fun forwardBill(
        recipientAddress: String,
        billTitle: String,
        amount: Double?,
        dueTimestamp: Long,
        originalSnippet: String
    ) {
        viewModelScope.launch {
            val result = repository.forwardBillToContact(
                recipientAddress = recipientAddress,
                billTitle = billTitle,
                amount = amount,
                dueTimestamp = dueTimestamp,
                originalSnippet = originalSnippet
            )
            if (result.isSuccess) {
                _snackbarEvents.tryEmit(SnackbarEvent("Bill reminder forwarded to $recipientAddress"))
            } else {
                _snackbarEvents.tryEmit(
                    SnackbarEvent(result.exceptionOrNull()?.message ?: "Failed to forward bill")
                )
            }
        }
    }

    fun scheduleSms(
        recipientAddress: String,
        recipientName: String?,
        body: String,
        scheduledTimestamp: Long,
        simSubscriptionId: Int = -1
    ) {
        if (recipientAddress.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            repository.scheduleSms(recipientAddress, recipientName, body, scheduledTimestamp, simSubscriptionId)
            _snackbarEvents.tryEmit(SnackbarEvent("Message scheduled"))
        }
    }

    fun cancelScheduledSms(item: ScheduledSmsEntity) {
        viewModelScope.launch {
            repository.cancelScheduledSms(item)
            _snackbarEvents.tryEmit(SnackbarEvent("Scheduled message cancelled"))
        }
    }

    fun deleteScheduledSms(item: ScheduledSmsEntity) {
        viewModelScope.launch { repository.deleteScheduledSms(item) }
    }

    // Finance Security & Privacy
    fun unlockFinanceVault() {
        _financeUnlocked.value = true
    }

    fun verifyPinAndUnlock(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val valid = prefsRepo.verifyFinancePin(pin)
            if (valid) _financeUnlocked.value = true
            onResult(valid)
        }
    }

    fun toggleShowBalance() {
        viewModelScope.launch {
            prefsRepo.setShowBalance(!preferences.value.showBalance)
        }
    }

    // Reminders & Offers
    fun addCustomReminder(title: String, subtitle: String, type: ReminderType, dueTs: Long, amount: Double?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addCustomReminder(title, subtitle, type, dueTs, amount)
            _snackbarEvents.tryEmit(SnackbarEvent("Reminder created"))
        }
    }

    fun setReminderCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch { repository.setReminderCompleted(id, completed) }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { repository.deleteReminder(id) }
    }

    fun deleteOffer(id: Long) {
        viewModelScope.launch { repository.deleteOffer(id) }
    }

    fun postTrainStatusNotification(title: String, body: String) {
        app.notificationManager.notifyTrainStatus(title, body, preferences.value)
    }

    // Settings & Preferences
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    fun setFontScale(scale: Float) = viewModelScope.launch { prefsRepo.setFontScale(scale) }
    fun setFinanceLockEnabled(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setFinanceLockEnabled(enabled)
        if (!enabled) _financeUnlocked.value = true
    }
    fun setFinancePin(pin: String) = viewModelScope.launch {
        prefsRepo.setFinancePin(pin)
        _snackbarEvents.tryEmit(SnackbarEvent("Finance PIN saved & lock enabled"))
    }

    fun updateNotificationToggle(
        defaultMsg: Boolean? = null,
        promotions: Boolean? = null,
        otp: Boolean? = null,
        reminders: Boolean? = null,
        trainStatus: Boolean? = null,
        lockScreenWake: Boolean? = null
    ) = viewModelScope.launch {
        prefsRepo.setNotificationToggle(defaultMsg, promotions, otp, reminders, trainStatus, lockScreenWake)
    }

    fun updateCleanupRule(
        otp: RetentionPeriod? = null,
        promotions: RetentionPeriod? = null,
        blocked: RetentionPeriod? = null
    ) = viewModelScope.launch {
        prefsRepo.setCleanupRule(otp, promotions, blocked)
    }

    fun runCleanupNow() {
        viewModelScope.launch {
            val summary = repository.runAutoCleanupNow()
            _snackbarEvents.tryEmit(
                SnackbarEvent(
                    if (summary.total > 0) {
                        "Cleaned up ${summary.total} messages (${summary.otpDeleted} OTPs, ${summary.promoDeleted} promos, ${summary.blockedDeleted} blocked)"
                    } else {
                        "No messages exceeded your auto-cleanup rules"
                    }
                )
            )
        }
    }

    fun updateSwipeSettings(
        tabSwipeEnabled: Boolean? = null,
        leftAction: SwipeAction? = null,
        rightAction: SwipeAction? = null
    ) = viewModelScope.launch {
        prefsRepo.setSwipeSettings(tabSwipeEnabled, leftAction, rightAction)
    }

    fun updateQuickReplies(replies: List<String>) = viewModelScope.launch {
        prefsRepo.setQuickReplies(replies)
        _snackbarEvents.tryEmit(SnackbarEvent("Quick replies updated"))
    }

    fun updateSignature(enabled: Boolean, text: String) = viewModelScope.launch {
        prefsRepo.setSignature(enabled, text)
    }

    fun setBackupCadence(cadence: BackupReminderCadence) = viewModelScope.launch {
        prefsRepo.setBackupCadence(cadence)
    }

    fun exportEncryptedBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val res = repository.exportEncryptedBackup(uri, passphrase)
            if (res.isSuccess) {
                _snackbarEvents.tryEmit(SnackbarEvent("Encrypted backup exported (${res.getOrDefault(0)} messages)"))
            } else {
                _snackbarEvents.tryEmit(SnackbarEvent(res.exceptionOrNull()?.message ?: "Backup export failed"))
            }
        }
    }

    fun importEncryptedBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val res = repository.importEncryptedBackup(uri, passphrase)
            if (res.isSuccess) {
                _snackbarEvents.tryEmit(SnackbarEvent("Backup restored (${res.getOrDefault(0)} messages)"))
            } else {
                _snackbarEvents.tryEmit(SnackbarEvent(res.exceptionOrNull()?.message ?: "Backup restore failed"))
            }
        }
    }

    fun exportMessagesToXml(uri: Uri, selectedFolders: Set<ExportFolder>) {
        viewModelScope.launch {
            val res = repository.exportMessagesToXml(uri, selectedFolders)
            if (res.isSuccess) {
                _snackbarEvents.tryEmit(SnackbarEvent("Exported ${res.getOrDefault(0)} messages to XML"))
            } else {
                _snackbarEvents.tryEmit(SnackbarEvent(res.exceptionOrNull()?.message ?: "XML export failed"))
            }
        }
    }

    fun emitSnackbar(message: String) {
        _snackbarEvents.tryEmit(SnackbarEvent(message))
    }
}
