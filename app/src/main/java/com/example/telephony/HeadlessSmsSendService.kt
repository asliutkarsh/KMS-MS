package com.example.telephony

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.KmsMsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Headless quick-response SMS service required for Android Default SMS App role.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "android.intent.action.RESPOND_VIA_MESSAGE") {
            val recipient = intent.data?.schemeSpecificPart?.trim().orEmpty()
            val message = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val app = applicationContext as? KmsMsApplication
            if (app != null && recipient.isNotBlank() && message.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    app.repository.sendSms(recipient, message, -1, null)
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
