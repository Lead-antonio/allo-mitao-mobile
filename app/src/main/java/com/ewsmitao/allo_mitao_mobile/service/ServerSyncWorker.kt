package com.ewsmitao.allo_mitao_mobile.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.ewsmitao.allo_mitao_mobile.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import androidx.work.ExistingWorkPolicy

/**
 * ServerSyncWorker — synchronisation périodique de rattrapage.
 *
 * Architecture de sync complète :
 *   - Sync temps réel  → FCM avec type="sync" (FcmCommandReceiver) → immédiat
 *     Déclenché par la plateforme à chaque ajout / modification / suppression
 *   - Sync rattrapage  → ServerSyncWorker périodique toutes les 15min
 *     Filet de sécurité si le FCM "sync" n'est pas reçu (réseau coupé, etc.)
 *   - Sync démarrage   → MainActivity au lancement de l'app → état initial
 *
 * Note : WorkManager impose un minimum de 15 minutes pour les tâches
 * périodiques (PeriodicWorkRequest). C'est exactement l'intervalle requis.
 */
class ServerSyncWorker(
    context: Context,
    params:  WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "ServerSyncWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "🔄 Démarrage sync serveur (rattrapage périodique 15min)...")
        return@withContext try {
            ServerApiService(applicationContext).syncSongs()
            Log.i(TAG, "✅ Sync complète")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Sync ignorée : ${e.message}")
            Result.success() // Ne pas bloquer — retry inutile si pas de réseau
        }
    }

    companion object {
        const val WORK_NAME = "server_sync_periodic"

        /**
         * Programme la sync périodique toutes les 15 minutes.
         * Utilise ExistingPeriodicWorkPolicy.UPDATE pour forcer la mise à jour
         * si la période a changé depuis la dernière installation.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ServerSyncWorker>(
                AppConfig.SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES  // 15 minutes
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            // UPDATE : force la mise à jour si la période a changé
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.i("ServerSyncWorker", "📅 Sync programmée toutes les ${AppConfig.SYNC_INTERVAL_MINUTES}min (rattrapage FCM)")
        }

        // Lance une synchronisation immédiate (avec contrainte réseau)
        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<ServerSyncWorker>()
                .setConstraints(constraints)
                .build()

//            WorkManager.getInstance(context).enqueue(request)
            WorkManager.getInstance(context).enqueueUniqueWork(
                "server_sync_now",
                ExistingWorkPolicy.REPLACE, // ← remplace si déjà en cours
                request
            )
            Log.i("ServerSyncWorker", "⚡ Sync immédiate lancée")
        }
    }
}