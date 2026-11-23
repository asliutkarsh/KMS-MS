package com.example.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.KmsMsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        ) return

        val app = context.applicationContext as? KmsMsApplication ?: return
        val isDefault = app.deviceSmsSyncManager.isDefaultSmsApp()

        // Avoid double ingestion when we are default SMS app (OS broadcasts both DELIVER and RECEIVED)
        if (isDefault && action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val grouped = messages.groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "Unknown" }
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for ((sender, parts) in grouped) {
                    val fullBody = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
                    val ts = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                    val subId = intent.getIntExtra("subscription", -1)
                    app.repository.ingestIncomingSms(
                        address = sender,
                        body = fullBody,
                        timestamp = ts,
                        simSubscriptionId = subId,
                        writeToSystemProvider = isDefault && action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
