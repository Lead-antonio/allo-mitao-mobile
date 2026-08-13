package com.ewsmitao.allo_mitao_mobile.service

import android.content.Context
import android.media.AudioManager
import android.util.Log

// Gère le volume du stream MUSIC du téléphone (lecture + écriture + persistance)
class VolumeManager(private val context: Context) {

    private val TAG   = "VolumeManager"
    private val PREFS = "sirene_volume_prefs"
    private val KEY   = "volume_global"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Retourne le volume actuel normalisé entre 0.0 et 1.0
    fun getVolume(): Float {
        val max     = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return current.toFloat() / max.toFloat()
    }

    // Applique un nouveau volume (0.0–1.0) et le sauvegarde en SharedPreferences
    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        val max     = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol  = (clamped * max).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        prefs.edit().putFloat(KEY, clamped).apply()
        Log.i(TAG, "Volume appliqué sur le Cerveau : ${(clamped * 100).toInt()}% (int=$newVol/$max)")
    }

    // Restaure le volume sauvegardé au démarrage du service
    fun restoreVolume() {
        val saved = prefs.getFloat(KEY, -1f)
        if (saved >= 0f) {
            setVolume(saved)
            Log.i(TAG, "Volume restauré : ${(saved * 100).toInt()}%")
        }
    }
}
