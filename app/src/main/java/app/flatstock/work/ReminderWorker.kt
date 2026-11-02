package app.flatstock.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.flatstock.FlatStockApp
import java.util.concurrent.TimeUnit

/**
 * Runs about every 30 minutes while online: alerts when a flatmate marked something low or out,
 * and sends the daily/weekly "what's low" summary once it is past 9:00.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as FlatStockApp).repository ?: return Result.success()
        val uid = repo.auth.currentUser?.uid ?: return Result.success()
        return try {
            val wid = repo.activeGroupOnce(uid) ?: return Result.success()
            val wg = repo.wgOnce(wid) ?: return Result.success()
            val supplies = repo.suppliesOnce(wid)
            Reminders.checkAlerts(applicationContext, wg, supplies, uid)
            Reminders.checkSummary(applicationContext, wg, supplies)
            Result.success()
        } catch (e: Exception) {
            Log.w("ReminderWorker", "reminder failed", e)
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
