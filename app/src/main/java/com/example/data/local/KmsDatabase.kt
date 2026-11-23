package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.AccountType
import com.example.data.model.ReminderType
import com.example.data.model.ScheduledStatus
import com.example.data.model.SmsCategory
import com.example.data.model.TransactionType

class KmsTypeConverters {
    @TypeConverter fun fromSmsCategory(value: SmsCategory): String = value.name
    @TypeConverter fun toSmsCategory(value: String): SmsCategory =
        runCatching { SmsCategory.valueOf(value) }.getOrDefault(SmsCategory.PERSONAL)

    @TypeConverter fun fromAccountType(value: AccountType): String = value.name
    @TypeConverter fun toAccountType(value: String): AccountType =
        runCatching { AccountType.valueOf(value) }.getOrDefault(AccountType.BANK)

    @TypeConverter fun fromTransactionType(value: TransactionType): String = value.name
    @TypeConverter fun toTransactionType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.DEBIT)

    @TypeConverter fun fromReminderType(value: ReminderType): String = value.name
    @TypeConverter fun toReminderType(value: String): ReminderType =
        runCatching { ReminderType.valueOf(value) }.getOrDefault(ReminderType.CUSTOM)

    @TypeConverter fun fromScheduledStatus(value: ScheduledStatus): String = value.name
    @TypeConverter fun toScheduledStatus(value: String): ScheduledStatus =
        runCatching { ScheduledStatus.valueOf(value) }.getOrDefault(ScheduledStatus.PENDING)
}

@Database(
    entities = [
        SmsMessageEntity::class,
        SenderRuleEntity::class,
        FinanceAccountEntity::class,
        FinanceTransactionEntity::class,
        ReminderEntity::class,
        OrderEntity::class,
        OfferEntity::class,
        ScheduledSmsEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(KmsTypeConverters::class)
abstract class KmsDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun senderRuleDao(): SenderRuleDao
    abstract fun financeDao(): FinanceDao
    abstract fun reminderDao(): ReminderDao
    abstract fun orderDao(): OrderDao
    abstract fun offerDao(): OfferDao
    abstract fun scheduledSmsDao(): ScheduledSmsDao

    companion object {
        @Volatile
        private var INSTANCE: KmsDatabase? = null

        fun getInstance(context: Context): KmsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KmsDatabase::class.java,
                    "kms_ms_local.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
