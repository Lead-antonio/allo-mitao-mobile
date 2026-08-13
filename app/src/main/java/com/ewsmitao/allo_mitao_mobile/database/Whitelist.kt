package com.ewsmitao.allo_mitao_mobile.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// Entité représentant les numéros autorisés à envoyer des commandes
@Entity(tableName = "whitelist")
data class Whitelist(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,          // ID local
    val phone_number: String  // Numéro autorisé
)