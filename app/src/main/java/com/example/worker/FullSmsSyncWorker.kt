package com.example.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.KmsMsApplication
import com.example.telephony.KmsNotificationManager

class FullSmsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KmsMsApplication ?: return Result.failure()
        val startOffset = inputData.getInt(KEY_START_OFFSET, 0)
        val initialTotal = inputData.getInt(KEY_TOTAL_ESTIMATE, 0)

        try {
            setForeground(createForegroundInfo(startOffset, initialTotal))
        } catch (_: Exception) {
            // Fall back gracefully if Android 14+ foreground service restriction triggers from background
        }

        return try {
            var lastNotifiedCount = -1
            var latestTotal = initialTotal
            val newlyImported = app.repository.syncAllRemainingInBackground(
                startOffset = startOffset,
                onProgress = { processed, total ->
                    latestTotal = total
                    setProgress(
                        workDataOf(
                            KEY_PROCESSED to processed,
                            KEY_TOTAL to total
                        )
                    )
                    if (processed - lastNotifiedCount >= 100 || processed >= total) {
                        lastNotifiedCount = processed
                        app.notificationManager.notifySyncProgress(processed, total)
                    }
                }
            )
            app.notificationManager.notifySyncComplete(newlyImported, latestTotal)
            Result.success(
                workDataOf(
                    KEY_NEWLY_IMPORTED to newlyImported,
                    KEY_TOTAL to latestTotal
                )
            )
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun createForegroundInfo(processed: Int, total: Int): ForegroundInfo {
        val app = applicationContext as KmsMsApplication
        val notification = app.notificationManager.buildSyncProgressNotification(processed, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                KmsNotificationManager.NOTIF_ID_FULL_SYNC,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(KmsNotificationManager.NOTIF_ID_FULL_SYNC, notification)
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "kms_ms_full_sms_sync"
        const val KEY_START_OFFSET = "key_start_offset"
        const val KEY_TOTAL_ESTIMATE = "key_total_estimate"
        const val KEY_PROCESSED = "key_processed"
        const val KEY_TOTAL = "key_total"
        const val KEY_NEWLY_IMPORTED = "key_newly_imported"

        fun enqueue(
            context: Context,
            startOffset: Int = 0,
            totalEstimate: Int = 0,
            replaceExisting: Boolean = false
        ) {
            val request = OneTimeWorkRequestBuilder<FullSmsSyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_START_OFFSET to startOffset,
                        KEY_TOTAL_ESTIMATE to totalEstimate
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
