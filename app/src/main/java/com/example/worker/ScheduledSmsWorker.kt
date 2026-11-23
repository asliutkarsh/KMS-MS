package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.KmsMsApplication
import com.example.data.model.ScheduledStatus
import java.util.UUID
import java.util.concurrent.TimeUnit

class ScheduledSmsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val scheduledId = inputData.getLong(KEY_SCHEDULED_ID, -1L)
        if (scheduledId <= 0L) return Result.failure()

        val app = applicationContext as? KmsMsApplication ?: return Result.failure()
        val item = app.database.scheduledSmsDao().getById(scheduledId) ?: return Result.success()
        if (item.status != ScheduledStatus.PENDING) return Result.success()

        val sendResult = app.repository.sendSms(
            recipientAddress = item.recipientAddress,
            body = item.body,
            simSubscriptionId = item.simSubscriptionId,
            attachmentUri = null
        )
        return if (sendResult.isSuccess) {
            app.database.scheduledSmsDao().updateStatus(scheduledId, ScheduledStatus.SENT)
            Result.success()
        } else {
            app.database.scheduledSmsDao().updateStatus(scheduledId, ScheduledStatus.FAILED)
            Result.failure()
        }
    }

    companion object {
        const val KEY_SCHEDULED_ID = "scheduled_sms_id"

        fun enqueue(context: Context, scheduledId: Long, targetTimestamp: Long): String {
            val delayMs = (targetTimestamp - System.currentTimeMillis()).coerceAtLeast(1_000L)
            val data = Data.Builder().putLong(KEY_SCHEDULED_ID, scheduledId).build()
            val request = OneTimeWorkRequestBuilder<ScheduledSmsWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return request.id.toString()
        }

        fun cancel(context: Context, workRequestId: String?) {
            if (workRequestId.isNullOrBlank()) return
            runCatching {
                WorkManager.getInstance(context).cancelWorkById(UUID.fromString(workRequestId))
            }
        }
    }
}
