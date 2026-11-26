package com.example.backup

import android.content.Context
import android.net.Uri
import com.example.data.local.SmsMessageEntity
import com.example.data.model.SmsCategory
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFolder(val label: String, val description: String) {
    PERSONAL("Personal", "Conversations from contacts and phone numbers"),
    TRANSACTIONS("Transactions", "Bank alerts, UPI/cards, OTPs & service updates"),
    PROMOTIONS("Promotions", "Marketing blasts, deals & circulars"),
    STARRED("Starred", "Messages starred for quick reference"),
    BLOCKED("Blocked", "Filtered spam and blocked senders")
}

/**
 * Exports KMS-MS messages to a standard Android SMS Backup XML document via Storage Access Framework.
 * Supports exporting specific folders (Personal, Transactions, Promotions, Starred, Blocked).
 */
class SmsXmlExporter(private val context: Context) {

    fun exportToUri(
        uri: Uri,
        allMessages: List<SmsMessageEntity>,
        selectedFolders: Set<ExportFolder>
    ): Result<Int> {
        if (selectedFolders.isEmpty()) {
            return Result.failure(IllegalArgumentException("Select at least one folder to export."))
        }
        val filtered = filterMessages(allMessages, selectedFolders)
        if (filtered.isEmpty()) {
            return Result.failure(IllegalStateException("No messages found in the selected folders."))
        }
        return try {
            val out = context.contentResolver.openOutputStream(uri)
                ?: return Result.failure(IllegalStateException("Unable to open destination XML file for writing."))
            BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8)).use { writer ->
                writeXml(filtered, writer)
            }
            Result.success(filtered.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun filterMessages(
            allMessages: List<SmsMessageEntity>,
            selectedFolders: Set<ExportFolder>
        ): List<SmsMessageEntity> {
            if (selectedFolders.isEmpty()) return emptyList()
            return allMessages
                .asSequence()
                .filter { msg -> !msg.isDeleted && matchesAnyFolder(msg, selectedFolders) }
                .sortedByDescending { it.timestamp }
                .toList()
        }

        fun countPerFolder(allMessages: List<SmsMessageEntity>): Map<ExportFolder, Int> {
            val active = allMessages.filter { !it.isDeleted }
            return ExportFolder.entries.associateWith { folder ->
                active.count { matchesFolder(it, folder) }
            }
        }

        private fun matchesAnyFolder(msg: SmsMessageEntity, folders: Set<ExportFolder>): Boolean =
            folders.any { matchesFolder(msg, it) }

        private fun matchesFolder(msg: SmsMessageEntity, folder: ExportFolder): Boolean = when (folder) {
            ExportFolder.PERSONAL -> !msg.isBlocked && msg.category == SmsCategory.PERSONAL
            ExportFolder.TRANSACTIONS -> !msg.isBlocked && msg.category == SmsCategory.TRANSACTION
            ExportFolder.PROMOTIONS -> !msg.isBlocked && msg.category == SmsCategory.PROMOTION
            ExportFolder.STARRED -> !msg.isBlocked && msg.isStarred
            ExportFolder.BLOCKED -> msg.isBlocked
        }

        fun writeXml(messages: List<SmsMessageEntity>, writer: Writer): Int {
            val dateFmt = SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.US)
            val now = System.currentTimeMillis()
            writer.write("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
            writer.write("<!-- Exported by KMS-MS On-Device SMS Organizer -->\n")
            writer.write("<smses count=\"${messages.size}\" backup_set=\"kms-ms-$now\" backup_date=\"$now\">\n")

            for (msg in messages) {
                val type = if (msg.isIncoming) "1" else "2"
                val read = if (msg.isRead) "1" else "0"
                val locked = if (msg.isStarred) "1" else "0"
                val readableDate = dateFmt.format(Date(msg.timestamp))
                val contactName = msg.contactName?.takeIf { it.isNotBlank() } ?: "(Unknown)"
                val subId = msg.simSubscriptionId

                writer.write("  <sms ")
                writer.write("protocol=\"0\" ")
                writer.write("address=\"${escapeXmlAttr(msg.address)}\" ")
                writer.write("date=\"${msg.timestamp}\" ")
                writer.write("type=\"$type\" ")
                writer.write("subject=\"null\" ")
                writer.write("body=\"${escapeXmlAttr(msg.body)}\" ")
                writer.write("toa=\"null\" ")
                writer.write("sc_toa=\"null\" ")
                writer.write("service_center=\"null\" ")
                writer.write("read=\"$read\" ")
                writer.write("status=\"-1\" ")
                writer.write("locked=\"$locked\" ")
                writer.write("date_sent=\"0\" ")
                writer.write("sub_id=\"$subId\" ")
                writer.write("readable_date=\"${escapeXmlAttr(readableDate)}\" ")
                writer.write("contact_name=\"${escapeXmlAttr(contactName)}\" ")
                writer.write("kms_category=\"${msg.category.name}\" ")
                writer.write("kms_blocked=\"${if (msg.isBlocked) "1" else "0"}\" ")
                writer.write("/>\n")
            }

            writer.write("</smses>\n")
            writer.flush()
            return messages.size
        }

        fun escapeXmlAttr(value: String): String {
            val sb = StringBuilder(value.length + 16)
            for (ch in value) {
                if (!isValidXmlChar(ch)) continue
                when (ch) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    '"' -> sb.append("&quot;")
                    '\'' -> sb.append("&apos;")
                    '\n' -> sb.append("&#10;")
                    '\r' -> sb.append("&#13;")
                    '\t' -> sb.append("&#9;")
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        private fun isValidXmlChar(c: Char): Boolean {
            val code = c.code
            return code == 0x9 || code == 0xA || code == 0xD ||
                (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
        }
    }
}
