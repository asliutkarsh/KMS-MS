package com.example.data.model

enum class SmsCategory(val label: String) {
    PERSONAL("Personal"),
    TRANSACTION("Transactions"),
    PROMOTION("Promotions")
}

enum class InboxTab(val label: String) {
    PERSONAL("Personal"),
    TRANSACTIONS("Transactions"),
    PROMOTIONS("Promotions"),
    STARRED("Starred")
}

enum class AccountType(val label: String) {
    BANK("Bank Accounts"),
    CREDIT_CARD("Credit Cards"),
    DEBIT_CARD("Debit Cards"),
    WALLET("Mobile Wallets & UPI"),
    EPFO("EPFO & Provident Fund")
}

enum class TransactionType {
    DEBIT,
    CREDIT
}

enum class ReminderType(val label: String) {
    TRAIN("Train"),
    FLIGHT("Flight"),
    BUS("Bus"),
    HOTEL("Hotel"),
    BILL("Bill"),
    RECHARGE("Recharge"),
    APPOINTMENT("Appointment"),
    MOVIE("Movie"),
    CUSTOM("Reminder")
}

enum class ThemeMode(val label: String) {
    SYSTEM("Follow System"),
    LIGHT("Daylight"),
    DARK("Slate Dark"),
    AMOLED("True AMOLED Black")
}

enum class RetentionPeriod(val label: String, val durationMillis: Long) {
    DAYS_3("After 3 days", 3L * 24L * 60L * 60L * 1000L),
    WEEK_1("After 1 week", 7L * 24L * 60L * 60L * 1000L),
    MONTH_1("After 1 month", 30L * 24L * 60L * 60L * 1000L),
    NEVER("Never", Long.MAX_VALUE)
}

enum class SwipeAction(val label: String) {
    MARK_READ("Mark Read / Unread"),
    MOVE_TO("Move To Folder"),
    STAR("Star / Unstar"),
    DELETE("Delete Thread"),
    NONE("Disabled")
}

enum class BackupReminderCadence(val label: String, val intervalDays: Int) {
    MANUAL("Manual only", 0),
    DAILY("Daily reminder", 1),
    WEEKLY("Weekly reminder", 7),
    MONTHLY("Monthly reminder", 30)
}

enum class ScheduledStatus {
    PENDING,
    SENT,
    FAILED,
    CANCELLED
}
