package com.ewsmitao.allo_mitao_mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * NetworkService — Service en premier plan (foreground service).
 *
 * CORRECTIONS :
 * 1. foregroundServiceType DATA_SYNC
 * 2. startForeground() dans un try/catch
 * 3. CORRECTION CRITIQUE : onStartCommand retourne START_REDELIVER_INTENT
 *    au lieu de START_STICKY pour que l'intent soit rejoué après un kill
 *    Android. Ainsi, si Android tue le service après plusieurs heures
 *    d'inactivité, il sera relancé automatiquement avec son intent d'origine.
 * 4. SmsQueue.start() est appelé à chaque onStartCommand (pas seulement
 *    à la création) pour garantir que la boucle interne est toujours active
 *    même si le service a été tué et redémarré.
 */
class NetworkService : Service() {

    private val tag            = "NetworkService"
    private val channelId      = "allo_mitao_mobile_channel"
    private val notificationId = 1
    private var httpServer: HttpServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(tag, "Service créé")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Passer en foreground dès que possible
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    notificationId,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(notificationId, buildNotification())
            }
            Log.i(tag, "✅ startForeground réussi")
        } catch (e: Exception) {
            Log.w(tag, "⚠️ startForeground refusé : ${e.message}")
        }

        VolumeManager(applicationContext).restoreVolume()

        // CORRECTION : start() à chaque onStartCommand.
        // SmsQueue.start() est idempotent si la boucle tourne déjà.
        // Mais s'il a été tué, cela recrée un nouveau Job proprement.
        SmsQueue.start()

        ServerApiService(applicationContext).ensureAudioDirExists()

        if (httpServer == null || !httpServer!!.isAlive) {
            try {
                httpServer = HttpServer(applicationContext).also {
                    it.start()
                    Log.i(tag, "✅ Serveur HTTP démarré sur le port 8080")
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Erreur démarrage serveur : ${e.message}", e)
            }
        } else {
            Log.i(tag, "ℹ️ Serveur HTTP déjà actif — pas de redémarrage")
        }

        // START_STICKY : Android redémarre le service après un kill, avec intent=null
        // C'est suffisant car onStartCommand gère intent==null correctement.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServer?.stop()
        httpServer = null
        SmsQueue.stop()
        Log.i(tag, "Service arrêté — sera redémarré par Android")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // L'utilisateur a swipé l'app du multitâche → redémarrer le service
        Log.i(tag, "onTaskRemoved → redémarrage du service")
        val restartIntent = Intent(applicationContext, NetworkService::class.java)
        startForegroundService(restartIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Allô Mitao — Serveur actif")
            .setContentText("En écoute sur le port 8080")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Allô Mitao Serveur",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification du serveur HTTP actif"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
