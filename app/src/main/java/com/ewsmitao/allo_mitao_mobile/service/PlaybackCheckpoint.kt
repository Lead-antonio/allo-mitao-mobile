package com.ewsmitao.allo_mitao_mobile.service


import android.content.Context
import android.util.Log

object PlaybackCheckpoint {
    private const val PREFS        = "playback_checkpoint_prefs"
    private const val KEY_NOTIF_ID = "notif_id"
    private const val KEY_ID_WEB   = "id_web"

    fun save(context: Context, notifId: Long?, idWeb: String) {
        if (notifId == null) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_NOTIF_ID, notifId)
            .putString(KEY_ID_WEB, idWeb)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun recoverIfNeeded(context: Context) {
        val prefs   = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val notifId = prefs.getLong(KEY_NOTIF_ID, -1L)
        if (notifId == -1L) return

        val idWeb = prefs.getString(KEY_ID_WEB, "") ?: ""
        Log.w("PlaybackCheckpoint", "⚠️ Lecture interrompue détectée (session précédente) : $idWeb #$notifId")

        PlaybackAckService.sendAsync(idWeb, notifId, "failed", "app_killed_mid_playback") { ackSent ->
            AckNotificationHelper.show(context, idWeb, success = false, detail = "lecture interrompue, app relancée")
        }
        clear(context)
    }
}