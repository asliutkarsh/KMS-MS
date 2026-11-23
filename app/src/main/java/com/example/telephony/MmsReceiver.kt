package com.example.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Required WAP_PUSH_DELIVER receiver to qualify as Android's Default SMS/MMS handler.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Handled by system telephony stack; keeps KMS-MS compliant with Android RoleManager.ROLE_SMS.
    }
}
