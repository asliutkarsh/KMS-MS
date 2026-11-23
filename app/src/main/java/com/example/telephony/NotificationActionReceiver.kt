package com.example.telephony

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.KmsMsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notifId = intent.getIntExtra(KmsNotificationManager.EXTRA_NOTIFICATION_ID, -1)
        val app = context.applicationContext as? KmsMsApplication

        when (action) {
            KmsNotificationManager.ACTION_COPY_OTP -> {
                val otp = intent.getStringExtra(KmsNotificationManager.EXTRA_OTP_CODE)
                if (!otp.isNullOrBlank()) {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("OTP Code", otp))
                    Toast.makeText(context, "OTP $otp copied", Toast.LENGTH_SHORT).show()
                }
            }

            KmsNotificationManager.ACTION_DELETE_OTP -> {
                val smsId = intent.getLongExtra(KmsNotificationManager.EXTRA_SMS_ID, -1L)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (app != null && smsId > 0L) {
                            app.repository.deleteMessagePermanently(smsId)
                        }
                        if (notifId > 0) {
                            NotificationManagerCompat.from(context).cancel(notifId)
                        }
                    } finally {
                        pending.finish()
                    }
                }
                Toast.makeText(context, "OTP message deleted", Toast.LENGTH_SHORT).show()
            }

            KmsNotificationManager.ACTION_DIRECT_REPLY -> {
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KmsNotificationManager.KEY_DIRECT_REPLY)
                    ?.toString()
                    ?.trim()
                val address = intent.getStringExtra(KmsNotificationManager.EXTRA_ADDRESS)
                if (!replyText.isNullOrBlank() && !address.isNullOrBlank() && app != null) {
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            app.repository.sendSms(address, replyText, -1, null)
                            if (notifId > 0) {
                                NotificationManagerCompat.from(context).cancel(notifId)
                            }
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
        }
    }
}
