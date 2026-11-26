package com.example.telephony

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.SmsMessageEntity
import com.example.data.local.UserPreferences
import com.example.data.model.SmsCategory

class KmsNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_PERSONAL = "kms_channel_personal"
        const val CHANNEL_OTP = "kms_channel_otp"
        const val CHANNEL_PROMO = "kms_channel_promo"
        const val CHANNEL_REMINDERS = "kms_channel_reminders"
        const val CHANNEL_TRAIN = "kms_channel_train"
        const val CHANNEL_SYNC = "kms_channel_sync"

        const val NOTIF_ID_FULL_SYNC = 9100

        const val KEY_DIRECT_REPLY = "key_direct_reply"
        const val ACTION_COPY_OTP = "com.example.kmsms.ACTION_COPY_OTP"
        const val ACTION_DELETE_OTP = "com.example.kmsms.ACTION_DELETE_OTP"
        const val ACTION_DIRECT_REPLY = "com.example.kmsms.ACTION_DIRECT_REPLY"

        const val EXTRA_SMS_ID = "extra_sms_id"
        const val EXTRA_SYSTEM_SMS_ID = "extra_system_sms_id"
        const val EXTRA_OTP_CODE = "extra_otp_code"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    fun ensureChannelsCreated() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(
                CHANNEL_PERSONAL,
                context.getString(R.string.channel_personal_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Incoming personal and transactional SMS messages" },
            NotificationChannel(
                CHANNEL_OTP,
                context.getString(R.string.channel_otp_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "One-time passwords with quick Copy and Delete buttons" },
            NotificationChannel(
                CHANNEL_PROMO,
                context.getString(R.string.channel_promo_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Promotional offers and marketing SMS" },
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Bill due dates, travel alerts, and custom reminders" },
            NotificationChannel(
                CHANNEL_TRAIN,
                context.getString(R.string.channel_train_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Offline GPS live train tracking alerts" },
            NotificationChannel(
                CHANNEL_SYNC,
                context.getString(R.string.channel_sync_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Progress updates while syncing full SMS history in the background" }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun notifyIncomingSms(sms: SmsMessageEntity, prefs: UserPreferences) {
        if (sms.isBlocked) return
        if (!hasNotificationPermission()) return

        val isOtp = !sms.otpCode.isNullOrBlank()
        val shouldNotify = when {
            isOtp -> prefs.notifyOtp
            sms.category == SmsCategory.PROMOTION -> prefs.notifyPromotions
            else -> prefs.notifyDefault
        }
        if (!shouldNotify) return

        ensureChannelsCreated()
        if (prefs.notifyLockScreenWake) {
            wakeScreenBriefly()
        }

        val notifId = sms.id.toInt().coerceAtLeast(1000)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_thread_key", sms.threadKey)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = when {
            isOtp -> CHANNEL_OTP
            sms.category == SmsCategory.PROMOTION -> CHANNEL_PROMO
            else -> CHANNEL_PERSONAL
        }

        val title = sms.contactName?.takeIf { it.isNotBlank() } ?: sms.address
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (isOtp) "OTP ${sms.otpCode} • $title" else title)
            .setContentText(sms.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(sms.body))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(
                if (isOtp || sms.category == SmsCategory.PERSONAL) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_LOW
            )

        if (isOtp && sms.otpCode != null) {
            val copyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_COPY_OTP
                putExtra(EXTRA_OTP_CODE, sms.otpCode)
                putExtra(EXTRA_NOTIFICATION_ID, notifId)
            }
            val copyPending = PendingIntent.getBroadcast(
                context,
                notifId + 100_000,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, context.getString(R.string.action_copy_otp), copyPending)

            val deleteIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_DELETE_OTP
                putExtra(EXTRA_SMS_ID, sms.id)
                putExtra(EXTRA_SYSTEM_SMS_ID, sms.systemSmsId ?: -1L)
                putExtra(EXTRA_NOTIFICATION_ID, notifId)
            }
            val deletePending = PendingIntent.getBroadcast(
                context,
                notifId + 200_000,
                deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, context.getString(R.string.action_delete_otp), deletePending)
        } else if (sms.category == SmsCategory.PERSONAL) {
            val remoteInput = RemoteInput.Builder(KEY_DIRECT_REPLY)
                .setLabel("Reply to $title")
                .build()
            val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_DIRECT_REPLY
                putExtra(EXTRA_ADDRESS, sms.address)
                putExtra(EXTRA_NOTIFICATION_ID, notifId)
            }
            val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val replyPending = PendingIntent.getBroadcast(
                context,
                notifId + 300_000,
                replyIntent,
                replyFlags
            )
            val replyAction = NotificationCompat.Action.Builder(
                0,
                context.getString(R.string.action_reply),
                replyPending
            ).addRemoteInput(remoteInput).build()
            builder.addAction(replyAction)
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }

    fun notifyTrainStatus(title: String, body: String, prefs: UserPreferences) {
        if (!prefs.notifyTrainStatus || !hasNotificationPermission()) return
        ensureChannelsCreated()
        val builder = NotificationCompat.Builder(context, CHANNEL_TRAIN)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        try {
            NotificationManagerCompat.from(context).notify(9050, builder.build())
        } catch (_: SecurityException) {
            // Ignore
        }
    }

    fun buildSyncProgressNotification(processed: Int, total: Int): Notification {
        ensureChannelsCreated()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID_FULL_SYNC,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hasTotal = total > 0
        val clamped = if (hasTotal) processed.coerceIn(0, total) else 0
        val contentText = if (hasTotal) {
            "Syncing SMS history: %,d / %,d messages".format(clamped, total)
        } else {
            "Scanning device SMS messages…"
        }

        return NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("KMS-MS Syncing All Messages")
            .setContentText(contentText)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(total.coerceAtLeast(0), clamped, !hasTotal)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifySyncProgress(processed: Int, total: Int) {
        if (!hasNotificationPermission()) return
        try {
            val notification = buildSyncProgressNotification(processed, total)
            NotificationManagerCompat.from(context).notify(NOTIF_ID_FULL_SYNC, notification)
        } catch (_: SecurityException) {
            // Ignore if notification permission is revoked
        }
    }

    fun notifySyncComplete(newlyImported: Int, totalDeviceMessages: Int) {
        if (!hasNotificationPermission()) return
        ensureChannelsCreated()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID_FULL_SYNC,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summaryText = if (newlyImported > 0) {
            "Imported %,d older messages (%,d total on device)".format(newlyImported, totalDeviceMessages)
        } else {
            "All %,d device SMS messages are synced".format(totalDeviceMessages)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SMS History Sync Complete")
            .setContentText(summaryText)
            .setContentIntent(contentPendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_FULL_SYNC, notification)
        } catch (_: SecurityException) {
            // Ignore
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly() {
        try {
            val pm = context.getSystemService(PowerManager::class.java) ?: return
            if (!pm.isInteractive) {
                val wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "kmsms:IncomingSmsWake"
                )
                wakeLock.acquire(3_000L)
            }
        } catch (_: Exception) {
            // Ignore on restricted devices
        }
    }
}
