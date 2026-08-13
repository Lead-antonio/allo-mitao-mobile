package com.ewsmitao.allo_mitao_mobile.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// DAO pour gérer la whitelist (numéros autorisés)
@Dao
interface WhitelistDao {

    // Récupérer tous les numéros
    @Query("SELECT * FROM whitelist")
    suspend fun getAll(): List<Whitelist>

    // Trouver un numéro précis
    @Query("SELECT * FROM whitelist WHERE phone_number = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): Whitelist?

    // Vérifier si un numéro est autorisé (retourne un count)
    @Query("SELECT COUNT(*) FROM whitelist WHERE phone_number = :phone")
    suspend fun isAllowed(phone: String): Int

    // Ajouter un numéro
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Whitelist)

    // Supprimer un numéro
    @Delete
    suspend fun delete(item: Whitelist)

    // Flux temps réel trié
    @Query("SELECT * FROM whitelist ORDER BY phone_number ASC")
    fun getAllFlow(): Flow<List<Whitelist>>
}