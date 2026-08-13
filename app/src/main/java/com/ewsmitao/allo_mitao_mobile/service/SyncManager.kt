package com.ewsmitao.allo_mitao_mobile.service

import android.util.Log
import com.ewsmitao.allo_mitao_mobile.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Classe conservée pour compatibilité avec SyncWorker
// La vraie synchronisation est gérée par ServerApiService
class SyncManager(private val db: AppDatabase) {

    private val tag = "SyncManager"

    suspend fun syncAll(): Boolean = withContext(Dispatchers.IO) {
        Log.i(tag, "SyncManager : rien à synchroniser (géré par ServerApiService)")
        true
    }
}
