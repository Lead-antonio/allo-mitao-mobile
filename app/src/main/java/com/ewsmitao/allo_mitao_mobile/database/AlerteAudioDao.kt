package com.ewsmitao.allo_mitao_mobile.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// DAO pour accéder aux sons d'alerte
@Dao
interface AlerteAudioDao {

    // Récupérer tous les sons (liste simple)
    @Query("SELECT * FROM alerte_audio")
    suspend fun getAll(): List<AlerteAudio>

    // Trouver un son via son ID serveur
    @Query("SELECT * FROM alerte_audio WHERE id_web = :idWeb LIMIT 1")
    suspend fun getByIdWeb(idWeb: String): AlerteAudio?

    // Insérer ou remplacer un son
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(audio: AlerteAudio)

    // Mettre à jour un son
    @Update
    suspend fun update(audio: AlerteAudio)

    // Supprimer un son
    @Delete
    suspend fun delete(audio: AlerteAudio)

    // Flux temps réel des sons triés par nom
    @Query("SELECT * FROM alerte_audio ORDER BY name ASC")
    fun getAllFlow(): Flow<List<AlerteAudio>>


    @Query("DELETE FROM alerte_audio WHERE id NOT IN (SELECT MIN(id) FROM alerte_audio GROUP BY id_web)")
    suspend fun removeDuplicates()
}