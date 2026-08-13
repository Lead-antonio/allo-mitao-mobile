package com.ewsmitao.allo_mitao_mobile.service

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.ewsmitao.allo_mitao_mobile.AppConfig
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * FcmTokenManager — gère le token FCM de ce cerveau.
 *
 * - Affiche le token COMPLET dans le Logcat (tag FCM_TOKEN) pour les tests Firebase Console.
 * - Envoie le token au backend via POST /{androidId} avec header x-api-key.
 *
 * URL finale : https://mitao-forecast.com/backend/sirenes/fcm-token/{androidId}
 * Header     : x-api-key: mdsjdkiedndeijizezoeioz98oiccdwxx
 * Body JSON  : { "token": "FCM_TOKEN_ICI" }
 */
object FcmTokenManager {

    private val TAG       = "FcmTokenManager"
    // Filtrer avec tag:FCM_TOKEN dans Android Studio Logcat pour retrouver le token rapidement
    private val TAG_TOKEN = "FCM_TOKEN"

    /**
     * Récupère l'AndroidID unique de cet appareil.
     * Stable tant que l'app n'est pas désinstallée / les données effacées.
     */
    private fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    /**
     * Récupère le token FCM courant :
     *   1. L'affiche en entier dans le Logcat (tag FCM_TOKEN).
     *   2. L'envoie au backend.
     * À appeler dans MainActivity.onCreate().
     */
    fun registerCurrentToken(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                logToken(token, "DEMARRAGE")
                sendTokenToBackend(context, token)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Impossible de récupérer le token FCM : ${e.message}")
            }
    }

    /**
     * Affiche le token dans le Logcat de façon bien visible.
     * Appelé au démarrage et à chaque refresh du token.
     */
    private fun logToken(token: String, raison: String) {
        Log.i(TAG_TOKEN, "════════════════════════════════════════════════════")
        Log.i(TAG_TOKEN, "TOKEN FCM — $raison")
        Log.i(TAG_TOKEN, token)
        Log.i(TAG_TOKEN, "════════════════════════════════════════════════════")
    }

    /**
     * Envoie le token FCM au backend via POST.
     * Appelé depuis registerCurrentToken() et FcmCommandReceiver.onNewToken().
     *
     * URL  : POST https://mitao-forecast.com/backend/sirenes/fcm-token/{androidId}
     * Body : { "fcmToken": "FCM_TOKEN" }
     * Header x-api-key : 9f3c2a7b8e1d4c6f0a2b9d5e7f8c1a3b6d4e2f9a0c7b1d3e5f6a8c9b0d2e4f6
     */
    fun sendTokenToBackend(context: Context, token: String) {
        // Affichage aussi à chaque envoi (refresh via onNewToken)
        logToken(token, "ENVOI BACKEND")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val androidId = getAndroidId(context)
                val urlString = "${AppConfig.FCM_REGISTER_BASE_URL}/$androidId"
                val url       = URL(urlString)

                // Corps de la requête : uniquement le token
                val body = JSONObject().apply {
                    put("fcmToken", token)
                }.toString()

                Log.i(TAG, "📤 POST $urlString")

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod  = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout    = 15_000
                connection.doOutput       = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept",       "application/json")
                connection.setRequestProperty("x-api-key",   AppConfig.FCM_API_KEY) // ← header auth

                connection.outputStream.use { os ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED) {
                    Log.i(TAG, "✅ Token enregistré sur le backend (HTTP $code) — androidId=$androidId")
                } else {
                    Log.w(TAG, "⚠️ Backend HTTP $code pour androidId=$androidId")
                }
                connection.disconnect()

            } catch (e: Exception) {
                // Non bloquant : sera renvoyé au prochain démarrage ou via onNewToken
                Log.e(TAG, "❌ Erreur envoi token : ${e.message}")
            }
        }
    }
}