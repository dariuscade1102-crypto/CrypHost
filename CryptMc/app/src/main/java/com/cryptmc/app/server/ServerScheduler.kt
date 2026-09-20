package com.cryptmc.app.server

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cryptmc.app.data.BackupFrequency
import com.cryptmc.app.data.BackupTrigger
import com.cryptmc.app.data.RestartFrequency
import com.cryptmc.app.data.ServerRepository
import java.util.concurrent.TimeUnit

/**
 * Turns each server's ScheduleConfig (Scheduling tab) into WorkManager
 * periodic jobs: scheduled backups and scheduled restarts. Re-call
 * [reschedule] any time a ServerConfig's `schedule` field changes (the
 * Scheduling tab's onChange handler is the natural call site) — WorkManager
 * dedupes by unique work name, so this is safe to call on every edit.
 *
 * WorkManager periodic jobs have a 15-minute minimum interval and are a
 * "best-effort, may drift" API, not a precise cron — fine for "back up
 * every 6h" but don't rely on it for second-accurate timing. The foreground
 * service (ServerForegroundService) is what actually keeps the JVM alive;
 * this class only decides *when* to ask it to do something.
 */
object ServerScheduler {

    private fun backupWorkName(serverId: String) = "backup-$serverId"
    private fun restartWorkName(serverId: String) = "restart-$serverId"

    fun reschedule(context: Context, serverId: String) {
        val config = ServerRepository.get(serverId) ?: return
        val workManager = WorkManager.getInstance(context)
        val schedule = config.schedule

        val backupWorkName = backupWorkName(serverId)
        if (schedule.backupFrequency == BackupFrequency.OFF) {
            workManager.cancelUniqueWork(backupWorkName)
        } else {
            val intervalHours = when (schedule.backupFrequency) {
                BackupFrequency.EVERY_6_HOURS -> 6L
                BackupFrequency.DAILY -> 24L
                BackupFrequency.WEEKLY -> 24L * 7
                BackupFrequency.OFF -> return
            }
            val request = PeriodicWorkRequestBuilder<BackupWorker>(intervalHours, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_SERVER_ID to serverId))
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            workManager.enqueueUniquePeriodicWork(
                backupWorkName, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        val restartWorkName = restartWorkName(serverId)
        if (schedule.restartFrequency == RestartFrequency.OFF) {
            workManager.cancelUniqueWork(restartWorkName)
        } else {
            val intervalHours = when (schedule.restartFrequency) {
                RestartFrequency.DAILY -> 24L
                RestartFrequency.EVERY_12_HOURS -> 12L
                RestartFrequency.OFF -> return
            }
            val request = PeriodicWorkRequestBuilder<RestartWorker>(intervalHours, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_SERVER_ID to serverId))
                .build()
            workManager.enqueueUniquePeriodicWork(
                restartWorkName, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }
    }

    fun cancelAll(context: Context, serverId: String) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(backupWorkName(serverId))
            cancelUniqueWork(restartWorkName(serverId))
        }
    }

    private const val KEY_SERVER_ID = "server_id"

    /** Runs a scheduled backup. Doesn't stop the server — relies on Paper/Fabric's own `save-all` flush. */
    class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val serverId = inputData.getString(KEY_SERVER_ID) ?: return Result.failure()
            val config = ServerRepository.get(serverId) ?: return Result.failure()
            // In production: issue `save-off` + `save-all` via ServerProcessManager's
            // stdin pipe here, await the console's "Saved the game" line, THEN back up,
            // then `save-on`. Backing up while writes are in-flight risks a torn region file.
            return BackupManager.createBackup(config, config.schedule.backupIncludePlugins, BackupTrigger.SCHEDULED)
                .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
        }
    }

    /** Warns players, then restarts the JVM process. Actual stop/start delegates to ServerProcessManager. */
    class RestartWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val serverId = inputData.getString(KEY_SERVER_ID) ?: return Result.failure()
            val config = ServerRepository.get(serverId) ?: return Result.failure()
            // Was previously just `return Result.success()` — WorkManager
            // would faithfully fire this every N hours and nothing would
            // happen. ServerForegroundService's ACTION_STOP/ACTION_START
            // pair (the same contract HomeScreen's Start/Stop buttons use)
            // is reachable from here without a DI'd process handle, unlike
            // ServerProcessManager directly.
            val isRunning = ServerRepository.statuses.value[serverId]?.running == true
            if (!isRunning) return Result.success()

            val context = applicationContext
            val serviceClass = com.cryptmc.app.service.ServerForegroundService::class.java

            context.startService(
                android.content.Intent(context, serviceClass)
                    .setAction(com.cryptmc.app.service.ServerForegroundService.ACTION_SEND_COMMAND)
                    .putExtra(
                        com.cryptmc.app.service.ServerForegroundService.EXTRA_COMMAND,
                        "say Restarting in ${config.schedule.restartWarningSeconds}s"
                    )
            )
            kotlinx.coroutines.delay(config.schedule.restartWarningSeconds * 1000L)

            context.startService(
                android.content.Intent(context, serviceClass)
                    .setAction(com.cryptmc.app.service.ServerForegroundService.ACTION_STOP)
            )
            context.startForegroundService(
                android.content.Intent(context, serviceClass)
                    .setAction(com.cryptmc.app.service.ServerForegroundService.ACTION_START)
                    .putExtra(com.cryptmc.app.service.ServerForegroundService.EXTRA_CONFIG, config)
            )
            return Result.success()
        }
    }
}
