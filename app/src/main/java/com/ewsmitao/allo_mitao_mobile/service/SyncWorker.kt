package com.ewsmitao.allo_mitao_mobile.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

// Worker WorkManager délégant la synchronisation à SyncManager
// Conservé pour compatibilité ; retente en cas d'échec
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val tag = "SyncWorker"

    override suspend fun doWork(): Result {
        return try {
            Log.i(tag, "SyncWorker démarré")
            val db          = com.ewsmitao.allo_mitao_mobile.database.AppDatabase.getInstance(applicationContext)
            val syncManager = SyncManager(db)
            val success     = syncManager.syncAll()

            if (success) {
                Log.i(tag, "✅ SyncWorker terminé avec succès")
                Result.success()
            } else {
                Log.w(tag, "⚠️ SyncWorker : sync partielle, nouvelle tentative...")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ SyncWorker erreur : ${e.message}", e)
            Result.retry()
        }
    }
}