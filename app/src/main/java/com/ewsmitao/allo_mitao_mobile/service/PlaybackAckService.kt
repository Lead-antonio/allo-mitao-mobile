package com.ewsmitao.allo_mitao_mobile.service

import android.util.Log
import com.ewsmitao.allo_mitao_mobile.AppConfig
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

object PlaybackAckService {

    private val tag   = "PlaybackAck"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sendAsync(
        idWeb: String,                                    // ← doit être le 1er paramètre
        notifId: Long?,                                    // ← 2ᵉ paramètre
        status: String,
        errorReason: String? = null,
        onResult: ((success: Boolean) -> Unit)? = null
    ) {
        scope.launch {
            val success = send(idWeb, notifId, status, errorReason)
            onResult?.let { withContext(Dispatchers.Main) { it(success) } }
        }
    }

    private suspend fun send(idWeb: String, notifId: Long?, status: String, errorReason: String?): Boolean {
        if (notifId == null) {
            Log.w(tag, "⚠️ notifId absent — ack ignoré ($idWeb)")
            return false
        }
        val baseUrl = if (idWeb.startsWith("ALT_", ignoreCase = true))
            AppConfig.PLAYBACK_ACK_URL else AppConfig.PLAYBACK_ACK_URL_ANN

        return try {
            val url = URL("$baseUrl/$notifId/ack")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod  = "POST"
            connection.doOutput       = true
            connection.connectTimeout = AppConfig.CONNECT_TIMEOUT
            connection.readTimeout    = AppConfig.READ_TIMEOUT
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", AppConfig.FCM_API_KEY)

            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            val body = JSONObject().apply {
                put("status", status)
                put("timestamp", timestamp)
                if (errorReason != null) put("errorReason", errorReason)
            }
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = connection.responseCode
            connection.disconnect()
            val ok = code in 200..299
            if (!ok) Log.w(tag, "⚠️ Ack #$notifId [$status] → HTTP $code")
            else Log.i(tag, "✅ Ack #$notifId [$status] envoyé ($baseUrl)")
            ok
        } catch (e: Exception) {
            Log.e(tag, "❌ Erreur ack #$notifId [$status] : ${e.message}")
            false
        }
    }
}