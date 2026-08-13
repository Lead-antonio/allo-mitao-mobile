package com.ewsmitao.allo_mitao_mobile.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import com.ewsmitao.allo_mitao_mobile.AppConfig
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withTimeoutOrNull

object DeviceSyncService {
    suspend fun syncToBackend(context: Context, uuid: String, fcmToken: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(AppConfig.DEVICE_REGISTER_ENDPOINT)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = AppConfig.CONNECT_TIMEOUT
                conn.readTimeout = AppConfig.READ_TIMEOUT


                val body = JSONObject().apply {
                    put("imei", uuid)
                    put("fcmToken", fcmToken)
                }.toString()

                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val code = conn.responseCode
                if (code != 200) {
                    Log.e("DeviceSync", "Erreur backend: $code")
                } else {
                    Log.d("DeviceSync", "Sync OK — uuid=$uuid")
                }

                conn.disconnect()

            } catch (e: Exception) {
                Log.e("DeviceSync", "Échec sync: ${e.message}")
            }
        }
    }

    private suspend fun getLastLocation(context: Context): Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // 1. Essaye d'abord getLastKnownLocation
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            val lastKnown = providers
                .filter { lm.isProviderEnabled(it) }
                .mapNotNull {
                    @Suppress("MissingPermission")
                    lm.getLastKnownLocation(it)
                }
                .maxByOrNull { it.accuracy }

            if (lastKnown != null) {
                Log.d("DeviceSync", "📍 Position connue: ${lastKnown.latitude}, ${lastKnown.longitude}")
                return lastKnown
            }

            // 2. Si null → demande une position fraîche
            Log.d("DeviceSync", "📍 Pas de position connue, demande fraîche...")
            suspendCancellableCoroutine { continuation ->
                val provider = when {
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
                    else -> null
                }

                if (provider == null) {
                    Log.w("DeviceSync", "⚠️ Aucun provider disponible")
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        Log.d("DeviceSync", "📍 Position fraîche: ${location.latitude}, ${location.longitude}")
                        lm.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }
                    override fun onProviderDisabled(provider: String) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                @Suppress("MissingPermission")
                lm.requestSingleUpdate(provider, listener, null)

                // Timeout 5 secondes
                continuation.invokeOnCancellation {
                    lm.removeUpdates(listener)
                }
            }

        } catch (e: Exception) {
            Log.e("DeviceSync", "Erreur GPS: ${e.message}")
            null
        }
    }
}