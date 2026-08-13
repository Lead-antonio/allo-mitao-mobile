package com.ewsmitao.allo_mitao_mobile.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de données Room — version UNIFIÉE.
 * [MODIFIÉ] La table Whitelist est réintégrée (nécessaire pour filtrer les SMS).
 * Version incrémentée à 4 pour gérer la migration proprement.
 */
@Database(
    entities = [
        AlerteAudio::class,
        Whitelist::class,      // [AJOUTÉ] réintégré pour la whitelist SMS
        AlertsHistory::class
    ],
    version = 5,               // ← incrémenté
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alerteAudioDao(): AlerteAudioDao
    abstract fun whitelistDao(): WhitelistDao  // [AJOUTÉ] réintégré
    abstract fun alertsHistoryDao(): AlertsHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sirene_cerveau_database"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
