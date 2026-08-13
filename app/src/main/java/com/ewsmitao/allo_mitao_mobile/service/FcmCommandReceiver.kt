package com.ewsmitao.allo_mitao_mobile.service

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FcmCommandReceiver — reçoit les data messages FCM.
 *
 * CORRECTION :
 * - Avant d'injecter dans SmsQueue, on s'assure que NetworkService tourne.
 *   Si Android a tué le service, un FCM entrant le relance automatiquement.
 * - SmsQueue.start() est appelé pour garantir que la boucle interne est active
 *   (au cas où le service aurait été tué sans que le scope soit recréé).
 *
 * Gère deux types de messages FCM :
 *   1. type = "sync"    → sync immédiate des sons depuis le serveur
 *   2. type = "command" → déclenchement d'un son
 */
class FcmCommandReceiver : FirebaseMessagingService() {

    private val TAG   = "FcmCommandReceiver"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"] ?: "command"

        when (type) {

            // ── SYNC TEMPS RÉEL ───────────────────────────────────────────────
            "sync" -> {
                Log.i(TAG, "🔄 FCM sync reçu — déclenchement sync immédiate des sons")
                scope.launch {
                    try {
                        val success = ServerApiService(applicationContext).syncSongs()
                        if (success) {
                            Log.i(TAG, "✅ Sync FCM terminée avec succès")
                        } else {
                            Log.w(TAG, "⚠️ Sync FCM échouée (serveur indisponible ?)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur sync FCM : ${e.message}", e)
                    }
                }
            }

            // ── COMMANDE AUDIO ────────────────────────────────────────────────
            else -> {
                val sender  = data["sender"]  ?: "FCM_SERVER"
                val command = data["command"] ?: run {
                    Log.w(TAG, "⚠️ Message FCM sans champ 'command' — ignoré")
                    return
                }

                Log.i(TAG, "📡 Commande FCM reçue de '$sender' : $command")

                // CORRECTION : s'assurer que NetworkService tourne pour que
                // la notification foreground soit active (évite que Android
                // ne tue le process immédiatement après le traitement FCM).
                val serviceIntent = Intent(applicationContext, NetworkService::class.java)
                applicationContext.startForegroundService(serviceIntent)

                // S'assurer que la file est démarrée (cas app fermée / service tué)
                SmsQueue.start()

                // Injection dans la file partagée SMS+FCM
                SmsQueue.enqueue(applicationContext, sender, command)
            }
        }
    }

//    override fun onNewToken(token: String) {
//        super.onNewToken(token)
//        Log.i(TAG, "🔑 Token FCM rafraîchi")
//        FcmTokenManager.sendTokenToBackend(applicationContext, token)
//    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMService", "Nouveau token: $token")
        CoroutineScope(Dispatchers.IO).launch {
            val uuid = CerveauIdentity.getOrCreateUuid(applicationContext)
            DeviceSyncService.syncToBackend(applicationContext, uuid, token)
        }
    }
}
