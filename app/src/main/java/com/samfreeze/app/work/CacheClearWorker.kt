package com.samfreeze.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.samfreeze.app.root.RootShell
import com.samfreeze.app.root.Commands
import java.util.concurrent.TimeUnit

/**
 * Periodic background job that clears every app's cache directories via
 * root. Scheduled through Android's WorkManager (a system-managed periodic
 * job, not a permanently-running service this app keeps alive itself).
 * Relies on the root grant already being persisted by the root manager —
 * if it isn't, this silently no-ops rather than prompting, since a
 * background job can't show a root-grant dialog.
 */
class CacheClearWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val result = RootShell.getInstance().execute(Commands.clearAllCaches(), timeoutMs = 60000)
            if (result.success) Result.success() else Result.retry()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}

object CacheScheduler {
    private const val WORK_NAME = "samfreeze_auto_clear_cache"

    fun schedule(context: Context, intervalMinutes: Int) {
        val safeInterval = intervalMinutes.coerceAtLeast(60) // WorkManager's periodic floor is 15 min; keep a sane 1hr floor here
        val request = PeriodicWorkRequestBuilder<CacheClearWorker>(safeInterval.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
