package com.ewsmitao.allo_mitao_mobile.service

import android.content.Context
import java.util.UUID

// Gère l'UUID unique de cette sirène (identifiant permanent stocké en SharedPreferences)
object CerveauIdentity {

    private const val PREFS = "cerveau_identity_prefs"
    private const val KEY   = "cerveau_uuid"

    // Retourne l'UUID existant ou en génère un nouveau (une seule fois)
    fun getOrCreateUuid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        if (existing != null) return existing

        val newUuid = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, newUuid).apply()
        return newUuid
    }
}
