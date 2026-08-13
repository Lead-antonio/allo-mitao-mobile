package com.ewsmitao.allo_mitao_mobile.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// Entité représentant l'historique des alertes reçues
@Entity(tableName = "alerts_history")
data class AlertsHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,              // ID local auto-généré
    val id_web: String,           // ID de l'alerte côté serveur
    val phone_number: String,     // Numéro ayant envoyé le SMS
    val date: String,             // Date de réception/traitement
    val status: String            // Statut (SUCCESS, FAIL, BLOCKED, etc.)
)