package com.example.data.local

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.BackupReminderCadence
import com.example.data.model.RetentionPeriod
import com.example.data.model.SwipeAction
import com.example.data.model.ThemeMode
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.kmsPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kms_ms_user_prefs"
)

@JsonClass(generateAdapter = true)
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: Float = 1.0f,
    val showBalance: Boolean = false,
    val financeLockEnabled: Boolean = false,
    val financePinHash: String = "",
    val financePinSalt: String = "",
    val notifyDefault: Boolean = true,
    val notifyPromotions: Boolean = false, // Off by default to avoid promo buzzes
    val notifyOtp: Boolean = true,
    val notifyReminders: Boolean = true,
    val notifyTrainStatus: Boolean = true,
    val notifyLockScreenWake: Boolean = false,
    val cleanupOtp: RetentionPeriod = RetentionPeriod.WEEK_1,
    val cleanupPromotions: RetentionPeriod = RetentionPeriod.MONTH_1,
    val cleanupBlocked: RetentionPeriod = RetentionPeriod.MONTH_1,
    val tabSwipeEnabled: Boolean = false,
    val leftSwipeAction: SwipeAction = SwipeAction.DELETE,
    val rightSwipeAction: SwipeAction = SwipeAction.MARK_READ,
    val quickReplies: List<String> = DEFAULT_QUICK_REPLIES,
    val signatureEnabled: Boolean = false,
    val signatureText: String = "",
    val backupCadence: BackupReminderCadence = BackupReminderCadence.MANUAL,
    val lastBackupTimestamp: Long = 0L,
    val hasPromptedDefaultSms: Boolean = false
) {
    companion object {
        val DEFAULT_QUICK_REPLIES = listOf(
            "On my way!",
            "Call you back shortly.",
            "Thanks, got it.",
            "Can we talk later?",
            "Payment done!"
        )
    }
}

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val SHOW_BALANCE = booleanPreferencesKey("show_balance")
        val FINANCE_LOCK_ENABLED = booleanPreferencesKey("finance_lock_enabled")
        val FINANCE_PIN_HASH = stringPreferencesKey("finance_pin_hash")
        val FINANCE_PIN_SALT = stringPreferencesKey("finance_pin_salt")
        val NOTIFY_DEFAULT = booleanPreferencesKey("notify_default")
        val NOTIFY_PROMOTIONS = booleanPreferencesKey("notify_promotions")
        val NOTIFY_OTP = booleanPreferencesKey("notify_otp")
        val NOTIFY_REMINDERS = booleanPreferencesKey("notify_reminders")
        val NOTIFY_TRAIN_STATUS = booleanPreferencesKey("notify_train_status")
        val NOTIFY_LOCK_WAKE = booleanPreferencesKey("notify_lock_wake")
        val CLEANUP_OTP = stringPreferencesKey("cleanup_otp")
        val CLEANUP_PROMOS = stringPreferencesKey("cleanup_promos")
        val CLEANUP_BLOCKED = stringPreferencesKey("cleanup_blocked")
        val TAB_SWIPE_ENABLED = booleanPreferencesKey("tab_swipe_enabled")
        val LEFT_SWIPE_ACTION = stringPreferencesKey("left_swipe_action")
        val RIGHT_SWIPE_ACTION = stringPreferencesKey("right_swipe_action")
        val QUICK_REPLIES = stringPreferencesKey("quick_replies")
        val SIGNATURE_ENABLED = booleanPreferencesKey("signature_enabled")
        val SIGNATURE_TEXT = stringPreferencesKey("signature_text")
        val BACKUP_CADENCE = stringPreferencesKey("backup_cadence")
        val LAST_BACKUP_TS = longPreferencesKey("last_backup_ts")
        val HAS_PROMPTED_DEFAULT = booleanPreferencesKey("has_prompted_default")
    }

    val preferencesFlow: Flow<UserPreferences> = context.kmsPreferencesDataStore.data.map { prefs ->
        val rawReplies = prefs[Keys.QUICK_REPLIES]
        val replies = if (rawReplies.isNullOrBlank()) {
            UserPreferences.DEFAULT_QUICK_REPLIES
        } else {
            rawReplies.split("\u001F").filter { it.isNotBlank() }
        }
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            fontScale = prefs[Keys.FONT_SCALE] ?: 1.0f,
            showBalance = prefs[Keys.SHOW_BALANCE] ?: false,
            financeLockEnabled = prefs[Keys.FINANCE_LOCK_ENABLED] ?: false,
            financePinHash = prefs[Keys.FINANCE_PIN_HASH] ?: "",
            financePinSalt = prefs[Keys.FINANCE_PIN_SALT] ?: "",
            notifyDefault = prefs[Keys.NOTIFY_DEFAULT] ?: true,
            notifyPromotions = prefs[Keys.NOTIFY_PROMOTIONS] ?: false,
            notifyOtp = prefs[Keys.NOTIFY_OTP] ?: true,
            notifyReminders = prefs[Keys.NOTIFY_REMINDERS] ?: true,
            notifyTrainStatus = prefs[Keys.NOTIFY_TRAIN_STATUS] ?: true,
            notifyLockScreenWake = prefs[Keys.NOTIFY_LOCK_WAKE] ?: false,
            cleanupOtp = prefs[Keys.CLEANUP_OTP]?.let { runCatching { RetentionPeriod.valueOf(it) }.getOrNull() }
                ?: RetentionPeriod.WEEK_1,
            cleanupPromotions = prefs[Keys.CLEANUP_PROMOS]?.let { runCatching { RetentionPeriod.valueOf(it) }.getOrNull() }
                ?: RetentionPeriod.MONTH_1,
            cleanupBlocked = prefs[Keys.CLEANUP_BLOCKED]?.let { runCatching { RetentionPeriod.valueOf(it) }.getOrNull() }
                ?: RetentionPeriod.MONTH_1,
            tabSwipeEnabled = prefs[Keys.TAB_SWIPE_ENABLED] ?: false,
            leftSwipeAction = prefs[Keys.LEFT_SWIPE_ACTION]?.let { runCatching { SwipeAction.valueOf(it) }.getOrNull() }
                ?: SwipeAction.DELETE,
            rightSwipeAction = prefs[Keys.RIGHT_SWIPE_ACTION]?.let { runCatching { SwipeAction.valueOf(it) }.getOrNull() }
                ?: SwipeAction.MARK_READ,
            quickReplies = replies,
            signatureEnabled = prefs[Keys.SIGNATURE_ENABLED] ?: false,
            signatureText = prefs[Keys.SIGNATURE_TEXT] ?: "",
            backupCadence = prefs[Keys.BACKUP_CADENCE]?.let { runCatching { BackupReminderCadence.valueOf(it) }.getOrNull() }
                ?: BackupReminderCadence.MANUAL,
            lastBackupTimestamp = prefs[Keys.LAST_BACKUP_TS] ?: 0L,
            hasPromptedDefaultSms = prefs[Keys.HAS_PROMPTED_DEFAULT] ?: false
        )
    }

    suspend fun getSnapshot(): UserPreferences = preferencesFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setFontScale(scale: Float) = edit { it[Keys.FONT_SCALE] = scale.coerceIn(0.85f, 1.4f) }
    suspend fun setShowBalance(show: Boolean) = edit { it[Keys.SHOW_BALANCE] = show }
    suspend fun setFinanceLockEnabled(enabled: Boolean) = edit { it[Keys.FINANCE_LOCK_ENABLED] = enabled }

    suspend fun setFinancePin(pin: String) {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val hash = hashPin(pin, salt)
        edit {
            it[Keys.FINANCE_PIN_SALT] = salt
            it[Keys.FINANCE_PIN_HASH] = hash
            it[Keys.FINANCE_LOCK_ENABLED] = true
        }
    }

    suspend fun verifyFinancePin(pin: String): Boolean {
        val prefs = getSnapshot()
        if (prefs.financePinHash.isBlank() || prefs.financePinSalt.isBlank()) return false
        return MessageDigest.isEqual(
            prefs.financePinHash.toByteArray(),
            hashPin(pin, prefs.financePinSalt).toByteArray()
        )
    }

    suspend fun setNotificationToggle(
        defaultMsg: Boolean? = null,
        promotions: Boolean? = null,
        otp: Boolean? = null,
        reminders: Boolean? = null,
        trainStatus: Boolean? = null,
        lockScreenWake: Boolean? = null
    ) = edit { prefs ->
        defaultMsg?.let { prefs[Keys.NOTIFY_DEFAULT] = it }
        promotions?.let { prefs[Keys.NOTIFY_PROMOTIONS] = it }
        otp?.let { prefs[Keys.NOTIFY_OTP] = it }
        reminders?.let { prefs[Keys.NOTIFY_REMINDERS] = it }
        trainStatus?.let { prefs[Keys.NOTIFY_TRAIN_STATUS] = it }
        lockScreenWake?.let { prefs[Keys.NOTIFY_LOCK_WAKE] = it }
    }

    suspend fun setCleanupRule(
        otp: RetentionPeriod? = null,
        promotions: RetentionPeriod? = null,
        blocked: RetentionPeriod? = null
    ) = edit { prefs ->
        otp?.let { prefs[Keys.CLEANUP_OTP] = it.name }
        promotions?.let { prefs[Keys.CLEANUP_PROMOS] = it.name }
        blocked?.let { prefs[Keys.CLEANUP_BLOCKED] = it.name }
    }

    suspend fun setSwipeSettings(
        tabSwipeEnabled: Boolean? = null,
        leftAction: SwipeAction? = null,
        rightAction: SwipeAction? = null
    ) = edit { prefs ->
        tabSwipeEnabled?.let { prefs[Keys.TAB_SWIPE_ENABLED] = it }
        leftAction?.let { prefs[Keys.LEFT_SWIPE_ACTION] = it.name }
        rightAction?.let { prefs[Keys.RIGHT_SWIPE_ACTION] = it.name }
    }

    suspend fun setQuickReplies(replies: List<String>) = edit { prefs ->
        prefs[Keys.QUICK_REPLIES] = replies.map { it.trim() }.filter { it.isNotBlank() }.joinToString("\u001F")
    }

    suspend fun setSignature(enabled: Boolean, text: String) = edit { prefs ->
        prefs[Keys.SIGNATURE_ENABLED] = enabled
        prefs[Keys.SIGNATURE_TEXT] = text
    }

    suspend fun setBackupCadence(cadence: BackupReminderCadence) = edit {
        it[Keys.BACKUP_CADENCE] = cadence.name
    }

    suspend fun setLastBackupTimestamp(ts: Long) = edit {
        it[Keys.LAST_BACKUP_TS] = ts
    }

    suspend fun markPromptedDefaultSms() = edit {
        it[Keys.HAS_PROMPTED_DEFAULT] = true
    }

    suspend fun restoreFromBackup(restored: UserPreferences) = edit { prefs ->
        prefs[Keys.THEME_MODE] = restored.themeMode.name
        prefs[Keys.FONT_SCALE] = restored.fontScale
        prefs[Keys.SHOW_BALANCE] = restored.showBalance
        prefs[Keys.FINANCE_LOCK_ENABLED] = restored.financeLockEnabled
        if (restored.financePinHash.isNotBlank()) {
            prefs[Keys.FINANCE_PIN_HASH] = restored.financePinHash
            prefs[Keys.FINANCE_PIN_SALT] = restored.financePinSalt
        }
        prefs[Keys.NOTIFY_DEFAULT] = restored.notifyDefault
        prefs[Keys.NOTIFY_PROMOTIONS] = restored.notifyPromotions
        prefs[Keys.NOTIFY_OTP] = restored.notifyOtp
        prefs[Keys.NOTIFY_REMINDERS] = restored.notifyReminders
        prefs[Keys.NOTIFY_TRAIN_STATUS] = restored.notifyTrainStatus
        prefs[Keys.NOTIFY_LOCK_WAKE] = restored.notifyLockScreenWake
        prefs[Keys.CLEANUP_OTP] = restored.cleanupOtp.name
        prefs[Keys.CLEANUP_PROMOS] = restored.cleanupPromotions.name
        prefs[Keys.CLEANUP_BLOCKED] = restored.cleanupBlocked.name
        prefs[Keys.TAB_SWIPE_ENABLED] = restored.tabSwipeEnabled
        prefs[Keys.LEFT_SWIPE_ACTION] = restored.leftSwipeAction.name
        prefs[Keys.RIGHT_SWIPE_ACTION] = restored.rightSwipeAction.name
        prefs[Keys.QUICK_REPLIES] = restored.quickReplies.joinToString("\u001F")
        prefs[Keys.SIGNATURE_ENABLED] = restored.signatureEnabled
        prefs[Keys.SIGNATURE_TEXT] = restored.signatureText
        prefs[Keys.BACKUP_CADENCE] = restored.backupCadence.name
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.kmsPreferencesDataStore.edit(block)
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
