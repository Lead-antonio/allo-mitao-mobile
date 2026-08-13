package com.ewsmitao.allo_mitao_mobile.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// DAO pour gérer l'historique des alertes
@Dao
interface AlertsHistoryDao {

    // Insérer un nouvel historique
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: AlertsHistory)

    // Récupérer tous les historiques (liste simple)
    @Query("SELECT * FROM alerts_history ORDER BY date DESC")
    suspend fun getAll(): List<AlertsHistory>

    // Récupérer les historiques en temps réel (Flow)
    @Query("SELECT * FROM alerts_history ORDER BY date DESC")
    fun getAllFlow(): Flow<List<AlertsHistory>>

    // Supprimer tout l'historique
    @Query("DELETE FROM alerts_history")
    suspend fun deleteAll()
}