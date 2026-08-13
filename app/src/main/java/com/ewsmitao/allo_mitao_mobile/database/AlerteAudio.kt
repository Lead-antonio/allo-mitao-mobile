package com.ewsmitao.allo_mitao_mobile.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

// Entité représentant un son d'alerte
@Entity(
    tableName = "alerte_audio",
    indices = [Index(value = ["id_web"], unique = true)]
    )
data class AlerteAudio(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,           // ID local auto-généré
    val name: String,          // Nom du son
    val description: String,   // Description
    val audio: String,         // Nom du fichier audio
    val id_web: String         // ID provenant du serveur
)