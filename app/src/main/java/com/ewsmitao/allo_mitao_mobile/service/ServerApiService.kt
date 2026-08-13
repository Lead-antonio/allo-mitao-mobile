package com.ewsmitao.allo_mitao_mobile.service

import android.content.Context
import android.os.Environment
import android.util.Log
import com.ewsmitao.allo_mitao_mobile.AppConfig
import com.ewsmitao.allo_mitao_mobile.database.AlerteAudio
import com.ewsmitao.allo_mitao_mobile.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * ServerApiService — synchronisation avec les deux serveurs distants.
 *
 * Deux endpoints indépendants, chacun gérant sa propre liste ET son propre
 * téléchargement de fichiers audio :
 *
 *   ANN (Annonces) → SERVER_SYNC_URL_ANN  (ancien lien)
 *     Format d'appel : {URL}?imei={uuid}
 *
 *   ALT (Alertes)  → SERVER_SYNC_URL_ALT  (nouveau lien)
 *     Format d'appel : {URL}/{uuid}
 *
 * syncSongs() appelle les deux endpoints en séquence. Les sons ALT et ANN
 * sont stockés ensemble dans Music/AlloMitaoAudio mais gérés séparément
 * côté serveur.
 *
 * Création du dossier : Music/AlloMitaoAudio est créé automatiquement à
 * l'installation (premier appel de syncSongs() ou démarrage du service).
 *
 * Sync temps réel : un message FCM avec type="sync" déclenche syncSongs()
 * immédiatement — la plateforme l'envoie à chaque ajout / modification /
 * suppression d'un son (ALT ou ANN).
 *
 * Sync de rattrapage : ServerSyncWorker tourne toutes les 15 minutes pour
 * rattraper un éventuel FCM non reçu.
 */
class ServerApiService(private val context: Context) {

    private val tag = "ServerApiService"
    private val db  = AppDatabase.getInstance(context)

    // Stockage externe public : Music/AlloMitaoAudio
    //    val audioDir: File = File(
    //        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
    //        "AlloMitaoAudio"
    //    )
//    val audioDir: File = File(context.filesDir, "AlloMitaoAudio")
    val audioDir: File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
        "AlloMitaoAudio"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Point d'entrée principal : synchronise ANN puis ALT
    // Retourne true si AU MOINS une des deux syncs a réussi
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun syncSongs(): Boolean = withContext(Dispatchers.IO) {
        ensureAudioDirExists()

        val uuid = CerveauIdentity.getOrCreateUuid(context)

        val okAnn = syncFromServer(
            label      = "ANN",
            url        = "${AppConfig.SERVER_SYNC_URL_ANN}?imei=$uuid",
            filterType = SoundType.ANN
        )

        val okAlt = syncFromServer(
            label      = "ALT",
            url        = "${AppConfig.SERVER_SYNC_URL_ALT}/$uuid",
            filterType = SoundType.ALT
        )

        Log.i(tag, "✅ Sync globale terminée — ANN: $okAnn | ALT: $okAlt")
        okAnn || okAlt
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sync d'un seul endpoint (ANN ou ALT)
    // ─────────────────────────────────────────────────────────────────────────
    private suspend fun syncFromServer(
        label:      String,
        url:        String,
        filterType: SoundType
    ): Boolean {
        Log.i(tag, "🔄 Sync [$label] → $url")

        val json = fetchJson(url)
        if (json == null) {
            Log.e(tag, "❌ [$label] Réponse nulle du serveur")
            return false
        }

        return try {
            val array = JSONArray(json)
            Log.i(tag, "✅ [$label] ${array.length()} son(s) reçu(s)")

            // Récupérer uniquement les sons du type correspondant dans la DB locale
            val allLocal    = db.alerteAudioDao().getAll()
            val localOfType = allLocal.filter { filterType.matches(it.id_web) }
            val localIdWebs = localOfType.associateBy { it.id_web }
            val serverIds   = mutableSetOf<String>()
            val toDownload = mutableListOf<Triple<String, String, String>>()

            for (i in 0 until array.length()) {
                val obj         = array.getJSONObject(i)
                val idWeb       = obj.optString("id_web", "")
                val name        = obj.optString("name", "")
                val description = obj.optString("description", "")
                val audioFile   = obj.optString("audio", "")
                val downloadUrl = obj.optString("downloadUrl", "")

                if (idWeb.isBlank()) continue
                serverIds.add(idWeb)

                val existing = localIdWebs[idWeb]

                if (existing == null) {
                    Log.i(tag, "➕ [$label] Nouveau son : $idWeb")
                    val downloaded = downloadMp3(downloadUrl, audioFile)
                    if (downloaded) {
                        db.alerteAudioDao().insert(
                            AlerteAudio(
                                name        = name,
                                description = description,
                                audio       = audioFile,
                                id_web      = idWeb
                            )
                        )
                        Log.i(tag, "✅ [$label] Ajouté en DB : $idWeb")
                    }
                } else {
                    val changed = existing.name        != name        ||
                            existing.description != description ||
                            existing.audio       != audioFile

                    if (changed) {
                        Log.i(tag, "🔄 [$label] Son modifié : $idWeb")
                        if (existing.audio != audioFile) {
                            val oldFile = File(audioDir, existing.audio)
                            if (oldFile.exists()) {
                                oldFile.delete()
                                Log.i(tag, "🗑️ [$label] Ancien fichier supprimé : ${existing.audio}")
                            }
                        }
                        val downloaded = downloadMp3(downloadUrl, audioFile)
                        if (downloaded) {
                            db.alerteAudioDao().update(
                                existing.copy(
                                    name        = name,
                                    description = description,
                                    audio       = audioFile
                                )
                            )
                            Log.i(tag, "✅ [$label] Mis à jour en DB : $idWeb")
                        }
                    } else {
                        val localFile = File(audioDir, existing.audio)
                        if (!localFile.exists()) {
                            Log.w(tag, "⚠️ [$label] Fichier manquant, re-téléchargement : ${existing.audio}")
                            downloadMp3(downloadUrl, audioFile)
                        } else {
                            Log.i(tag, "⏭ [$label] Déjà à jour : $idWeb")
                        }
                    }
                }
            }

            // Supprimer localement les sons de ce type absents du serveur
            for (local in localOfType) {
                if (local.id_web !in serverIds) {
                    val f = File(audioDir, local.audio)
                    if (f.exists()) f.delete()
                    db.alerteAudioDao().delete(local)
                    Log.i(tag, "🗑️ [$label] Son supprimé (absent du serveur) : ${local.id_web}")
                }
            }

            true
        } catch (e: Exception) {
            Log.e(tag, "❌ [$label] Erreur sync : ${e.message}", e)
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Énumération des types de sons — permet de filtrer la DB locale
    // par type lors de la suppression (évite de supprimer des ANN lors d'une
    // sync ALT et vice versa)
    // ─────────────────────────────────────────────────────────────────────────
    private enum class SoundType {
        ALT {
            override fun matches(idWeb: String) = idWeb.startsWith("ALT_", ignoreCase = true)
        },
        ANN {
            // Tout ce qui n'est pas ALT est considéré comme ANN
            override fun matches(idWeb: String) = !idWeb.startsWith("ALT_", ignoreCase = true)
        };

        abstract fun matches(idWeb: String): Boolean
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitaires
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Crée le dossier Music/AlloMitaoAudio automatiquement après installation.
     * Sans effet si le dossier existe déjà.
     */
//    fun ensureAudioDirExists() {
//        if (!audioDir.exists()) {
//            audioDir.mkdirs()
//            Log.i(tag, "📁 Dossier créé : ${audioDir.absolutePath}")
//        }
//    }
    fun ensureAudioDirExists() {
        if (!audioDir.exists()) {
            val created = audioDir.mkdirs()
            if (created) {
                Log.i(tag, "📁 Dossier créé : ${audioDir.absolutePath}")
            } else {
                Log.e(tag, "❌ Impossible de créer le dossier : ${audioDir.absolutePath}")
            }
        }
    }

    // Télécharge un fichier MP3 depuis une URL vers Music/AlloMitaoAudio
//    private fun downloadMp3(downloadUrl: String, filename: String): Boolean {
//        if (downloadUrl.isBlank()) return false
//        return synchronized(filename.intern()) {
//            try {
//                ensureAudioDirExists()
//                val dest = File(audioDir, filename)
//                // ✅ Télécharger dans un fichier .tmp puis renommer
//                val tmp = File(audioDir, "$filename.tmp")
//                tmp.delete()
//
//                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
//                connection.connectTimeout = 30_000
//                connection.readTimeout    = 60_000
//                connection.inputStream.use { input ->
//                    java.io.FileOutputStream(tmp).use { output ->
//                        input.copyTo(output)
//                    }
//                }
//                connection.disconnect()
//
//                // Renommer .tmp → nom final
//                tmp.renameTo(dest)
//                Log.i(tag, "⬇️ MP3 téléchargé : $filename (${dest.length()} bytes)")
//                true
//            } catch (e: Exception) {
//                Log.e(tag, "❌ Erreur téléchargement $filename : ${e.message}")
//                false
//            }
//        }
//    }
    private fun downloadMp3(downloadUrl: String, filename: String): Boolean {
        if (downloadUrl.isBlank()) {
            Log.w(tag, "⚠️ downloadUrl vide pour $filename")
            return false
        }
        return synchronized(filename.intern()) {
            try {
                ensureAudioDirExists()
                val dest = File(audioDir, filename)

                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout    = 60_000

                val responseCode = connection.responseCode
                val contentType  = connection.contentType
                Log.d(tag, "📥 Download $filename → HTTP $responseCode | Content-Type: $contentType | Size: ${connection.contentLength}")

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(tag, "❌ Serveur retourné $responseCode")
                    connection.disconnect()
                    return@synchronized false
                }

                connection.inputStream.use { input ->
                    java.io.FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
                connection.disconnect()
                Log.i(tag, "⬇️ MP3 téléchargé : $filename (${dest.length()} bytes)")
                true
            } catch (e: Exception) {
                Log.e(tag, "❌ Erreur téléchargement $filename : ${e.message}")
                false
            }
        }
    }

    // Effectue un GET HTTP et retourne le corps de la réponse en String
    private fun fetchJson(urlString: String): String? {
        return try {
            Log.i(tag, "🌐 GET $urlString")
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod  = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout    = 15_000
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            Log.i(tag, "🌐 HTTP $code")
            if (code == HttpURLConnection.HTTP_OK) {
                val result = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                result
            } else {
                Log.w(tag, "⚠️ HTTP $code pour $urlString")
                connection.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Erreur réseau fetchJson : ${e.message}", e)
            null
        }
    }
}