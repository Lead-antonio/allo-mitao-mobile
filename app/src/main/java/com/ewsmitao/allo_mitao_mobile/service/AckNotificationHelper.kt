package com.ewsmitao.allo_mitao_mobile.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ewsmitao.allo_mitao_mobile.R

object AckNotificationHelper {

    private const val CHANNEL_ID = "playback_ack_channel"

    fun show(context: Context, idWeb: String, success: Boolean, detail: String? = null) {
        // Vérif permission (Android 13+) — sans elle, on ignore silencieusement,
        // ça ne doit jamais faire planter le flux de lecture.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val title = if (success) "✅ Diffusion confirmée" else "⚠️ Échec de confirmation"
        val text  = if (success)
            "$idWeb — audio joué, accusé envoyé au serveur"
        else
            "$idWeb — ${detail ?: "l'accusé n'a pas pu être transmis"}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background) // adapter au drawable existant de l'app
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        // ID unique basé sur idWeb+timestamp pour ne pas écraser les notifs précédentes
        val notifId = (idWeb + System.currentTimeMillis()).hashCode()
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}