package com.example

import android.app.Application
import com.example.backup.EncryptedBackupManager
import com.example.data.local.KmsDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.KmsRepository
import com.example.telephony.DeviceSmsSyncManager
import com.example.telephony.KmsNotificationManager
import com.example.worker.AutoCleanupWorker

class KmsMsApplication : Application() {

    lateinit var database: KmsDatabase
        private set
    lateinit var preferencesRepository: UserPreferencesRepository
        private set
    lateinit var deviceSmsSyncManager: DeviceSmsSyncManager
        private set
    lateinit var notificationManager: KmsNotificationManager
        private set
    lateinit var encryptedBackupManager: EncryptedBackupManager
        private set
    lateinit var repository: KmsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = KmsDatabase.getInstance(this)
        preferencesRepository = UserPreferencesRepository(this)
        deviceSmsSyncManager = DeviceSmsSyncManager(this)
        notificationManager = KmsNotificationManager(this).apply {
            ensureChannelsCreated()
        }
        encryptedBackupManager = EncryptedBackupManager(this)
        repository = KmsRepository(
            context = this,
            db = database,
            prefsRepo = preferencesRepository,
            syncManager = deviceSmsSyncManager,
            notificationManager = notificationManager,
            backupManager = encryptedBackupManager
        )
        AutoCleanupWorker.schedulePeriodic(this)
    }
}
