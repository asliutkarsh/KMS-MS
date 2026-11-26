package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimSlotInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String
)

data class RawDeviceSms(
    val systemSmsId: Long,
    val address: String,
    val contactName: String?,
    val body: String,
    val timestamp: Long,
    val isIncoming: Boolean,
    val isRead: Boolean,
    val simSubscriptionId: Int
)

class DeviceSmsSyncManager(private val context: Context) {

    fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    fun buildRequestDefaultSmsIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else null
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }

    fun hasReadSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasSendSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun getActiveSimSlots(): List<SimSlotInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return listOf(SimSlotInfo(-1, 0, "SIM 1", "Default SIM"))
        }
        return try {
            val subManager = context.getSystemService(SubscriptionManager::class.java)
            val list = subManager?.activeSubscriptionInfoList.orEmpty()
            if (list.isEmpty()) {
                listOf(SimSlotInfo(-1, 0, "SIM 1", "Default SIM"))
            } else {
                list.map { info ->
                    SimSlotInfo(
                        subscriptionId = info.subscriptionId,
                        slotIndex = info.simSlotIndex,
                        displayName = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                            ?: "SIM ${info.simSlotIndex + 1}",
                        carrierName = info.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "Carrier"
                    )
                }
            }
        } catch (_: Exception) {
            listOf(SimSlotInfo(-1, 0, "SIM 1", "Default SIM"))
        }
    }

    fun resolveContactName(address: String): String? {
        if (!hasContactsPermission() || address.isBlank() || address.contains(",")) return null
        return try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getTotalDeviceSmsCount(): Int {
        if (!hasReadSmsPermission()) return 0
        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                null,
                null,
                null
            )?.use { cursor ->
                cursor.count
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun readDeviceSmsInbox(limit: Int = 600): List<RawDeviceSms> {
        if (!hasReadSmsPermission()) return emptyList()
        val result = mutableListOf<RawDeviceSms>()
        val contactCache = mutableMapOf<String, String?>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SUBSCRIPTION_ID
        )

        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                val subIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val address = cursor.getString(addrIdx)?.trim().orEmpty()
                    val body = cursor.getString(bodyIdx).orEmpty()
                    if (address.isBlank() && body.isBlank()) continue

                    val contactName = contactCache.getOrPut(address) { resolveContactName(address) }
                    val type = cursor.getInt(typeIdx)
                    val isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                    val isRead = cursor.getInt(readIdx) == 1 || !isIncoming
                    val subId = if (subIdx >= 0) cursor.getInt(subIdx) else -1

                    result.add(
                        RawDeviceSms(
                            systemSmsId = cursor.getLong(idIdx),
                            address = address.ifBlank { "Unknown" },
                            contactName = contactName,
                            body = body,
                            timestamp = cursor.getLong(dateIdx),
                            isIncoming = isIncoming,
                            isRead = isRead,
                            simSubscriptionId = subId
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {
            // Gracefully return collected messages on restricted emulators/devices
        }
        return result
    }

    /**
     * Streams ALL SMS messages from the system telephony provider without any cap,
     * reading in chunks of [batchSize] starting from [startOffset].
     */
    suspend fun streamDeviceSmsBatches(
        startOffset: Int = 0,
        batchSize: Int = 150,
        onBatch: suspend (processedSoFar: Int, totalCount: Int, batch: List<RawDeviceSms>) -> Unit
    ): Int {
        if (!hasReadSmsPermission()) return 0
        val contactCache = mutableMapOf<String, String?>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SUBSCRIPTION_ID
        )

        var totalCount = 0
        var processed = 0
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                totalCount = cursor.count
                if (totalCount == 0 || startOffset >= totalCount) {
                    return totalCount
                }

                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                val subIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                if (startOffset > 0) {
                    cursor.moveToPosition(startOffset - 1)
                    processed = startOffset
                }

                val batch = ArrayList<RawDeviceSms>(batchSize)
                while (cursor.moveToNext()) {
                    processed++
                    val address = cursor.getString(addrIdx)?.trim().orEmpty()
                    val body = cursor.getString(bodyIdx).orEmpty()
                    if (address.isBlank() && body.isBlank()) continue

                    val contactName = contactCache.getOrPut(address) { resolveContactName(address) }
                    val type = cursor.getInt(typeIdx)
                    val isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                    val isRead = cursor.getInt(readIdx) == 1 || !isIncoming
                    val subId = if (subIdx >= 0) cursor.getInt(subIdx) else -1

                    batch.add(
                        RawDeviceSms(
                            systemSmsId = cursor.getLong(idIdx),
                            address = address.ifBlank { "Unknown" },
                            contactName = contactName,
                            body = body,
                            timestamp = cursor.getLong(dateIdx),
                            isIncoming = isIncoming,
                            isRead = isRead,
                            simSubscriptionId = subId
                        )
                    )

                    if (batch.size >= batchSize) {
                        onBatch(processed, totalCount, batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) {
                    onBatch(processed, totalCount, batch.toList())
                    batch.clear()
                }
            }
        } catch (_: Exception) {
            // Gracefully stop on restricted devices
        }
        return totalCount
    }

    fun sendSms(
        recipientAddress: String,
        body: String,
        simSubscriptionId: Int = -1
    ): Result<Long?> {
        if (!hasSendSmsPermission()) {
            return Result.failure(SecurityException("SEND_SMS permission is required to send messages."))
        }
        return try {
            val smsManager = getSmsManagerForSub(simSubscriptionId)
            val recipients = recipientAddress.split(",").map { it.trim() }.filter { it.isNotBlank() }
            for (recipient in recipients) {
                val parts = smsManager.divideMessage(body)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(recipient, null, body, null, null)
                }
            }
            val systemId = if (isDefaultSmsApp()) {
                insertIntoSystemProvider(
                    address = recipientAddress,
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    isIncoming = false,
                    isRead = true,
                    simSubscriptionId = simSubscriptionId
                )
            } else null
            Result.success(systemId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun insertIntoSystemProvider(
        address: String,
        body: String,
        timestamp: Long,
        isIncoming: Boolean,
        isRead: Boolean,
        simSubscriptionId: Int = -1
    ): Long? {
        if (!isDefaultSmsApp()) return null
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, if (isRead) 1 else 0)
                put(
                    Telephony.Sms.TYPE,
                    if (isIncoming) Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_SENT
                )
                if (simSubscriptionId >= 0) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, simSubscriptionId)
                }
            }
            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun deleteFromSystemProvider(systemSmsId: Long): Boolean {
        if (!isDefaultSmsApp()) return false
        return try {
            val deleted = context.contentResolver.delete(
                Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, systemSmsId.toString()),
                null,
                null
            )
            deleted > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun getSmsManagerForSub(subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            if (subId >= 0) base.createForSubscriptionId(subId) else base
        } else {
            @Suppress("DEPRECATION")
            if (subId >= 0) SmsManager.getSmsManagerForSubscriptionId(subId)
            else SmsManager.getDefault()
        }
    }
}
