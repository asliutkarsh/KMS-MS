package com.example.ui.navigation

import android.net.Uri

object KmsRoutes {
    const val ROUTE_INBOX = "inbox"
    const val ROUTE_FINANCE = "finance"
    const val ROUTE_REMINDERS = "reminders"
    const val ROUTE_OFFERS = "offers"

    const val ROUTE_CONVERSATION_PATTERN = "conversation/{threadKey}"
    const val ROUTE_NEW_MESSAGE = "new_message?recipient={recipient}"
    const val ROUTE_FINANCE_LEDGER_PATTERN = "finance_ledger/{accountKey}"
    const val ROUTE_BLOCKED = "blocked_senders"
    const val ROUTE_SCHEDULED = "scheduled_messages"
    const val ROUTE_ARCHIVE_PATTERN = "archive/{initialTab}"
    const val ROUTE_SETTINGS = "settings"

    fun conversation(threadKey: String): String = "conversation/${Uri.encode(threadKey)}"
    fun newMessage(recipient: String = ""): String = "new_message?recipient=${Uri.encode(recipient)}"
    fun financeLedger(accountKey: String): String = "finance_ledger/${Uri.encode(accountKey)}"
    fun archive(initialTab: Int = 0): String = "archive/$initialTab"
}
