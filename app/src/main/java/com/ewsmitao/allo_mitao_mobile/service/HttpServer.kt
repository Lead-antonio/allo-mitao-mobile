package com.ewsmitao.allo_mitao_mobile.service

import android.content.Context
import android.util.Log
import com.ewsmitao.allo_mitao_mobile.database.AlerteAudio
import com.ewsmitao.allo_mitao_mobile.database.AppDatabase
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Serveur HTTP local (port 8080) — routes /whitelist retirées.
 * Routes disponibles :
 *   GET  /songs
 *   POST /songs
 *   PUT  /songs/{id}
 *   DELETE /songs/{id}
 *   POST /upload
 *   GET  /volume
 *   PUT  /volume
 */
class HttpServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    private val TAG           = "HttpServer"
    private val db            = AppDatabase.getInstance(context)
    private val volumeManager = VolumeManager(context)

    private val audioDir = File(context.filesDir, "AlloMitaoAudio").also { it.mkdirs() }

    // Routeur principal
    override fun serve(session: IHTTPSession): Response {
        val uri    = session.uri
        val method = session.method
        Log.i(TAG, "${method} ${uri}")

        return try {
            when {
                uri == "/songs" && method == Method.GET                          -> getSongs()
                uri == "/songs" && method == Method.POST                         -> addSong(session)
                uri.matches(Regex("/songs/\\d+")) && method == Method.PUT        -> updateSong(session, uri)
                uri.matches(Regex("/songs/\\d+")) && method == Method.DELETE     -> deleteSong(uri)
                uri == "/upload" && method == Method.POST                        -> uploadAudio(session)
                uri == "/volume" && method == Method.GET                         -> getVolume()
                uri == "/volume" && method == Method.PUT                         -> updateVolume(session)
                method == Method.OPTIONS                                         -> corsResponse()
                else -> errorResponse(404, "Route non trouvée : $method $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur : ${e.message}")
            errorResponse(500, e.message ?: "Erreur interne")
        }
    }

    // ════════════════════════════════════════════════════════
    // SONGS — GET /songs
    // ════════════════════════════════════════════════════════
    private fun getSongs(): Response = runBlocking {
        val songs  = db.alerteAudioDao().getAll()
        val result = JSONArray()
        songs.forEach { s ->
            result.put(JSONObject().apply {
                put("id",          s.id)
                put("name",        s.name)
                put("description", s.description)
                put("audio",       s.audio)
                put("id_web",      s.id_web)
            })
        }
        Log.i(TAG, "GET /songs → ${songs.size} sons")
        jsonResponse(result.toString())
    }

    // ════════════════════════════════════════════════════════
    // SONGS — POST /songs
    // ════════════════════════════════════════════════════════
    private fun addSong(session: IHTTPSession): Response = runBlocking {
        val body = parseBody(session)
        val json = JSONObject(body)

        if (!json.has("name") || !json.has("id_web") || !json.has("audio")) {
            return@runBlocking errorResponse(400, "Champs obligatoires manquants : name, id_web, audio")
        }

        val song = AlerteAudio(
            name        = json.getString("name"),
            description = json.optString("description", ""),
            audio       = json.getString("audio"),
            id_web      = json.getString("id_web")
        )
        db.alerteAudioDao().insert(song)
        Log.i(TAG, "POST /songs → Son ajouté : ${song.name}")
        jsonResponse("""{"success":true,"message":"Son ajouté"}""")
    }

    // ════════════════════════════════════════════════════════
    // SONGS — PUT /songs/{id}
    // ════════════════════════════════════════════════════════
    private fun updateSong(session: IHTTPSession, uri: String): Response = runBlocking {
        val id = uri.removePrefix("/songs/").toIntOrNull()
            ?: return@runBlocking errorResponse(400, "ID invalide")

        val existing = db.alerteAudioDao().getAll().find { it.id == id }
            ?: return@runBlocking errorResponse(404, "Son non trouvé")

        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            val buffer = ByteArray(contentLength)
            session.inputStream.read(buffer, 0, contentLength)
            String(buffer, Charsets.UTF_8)
        } else {
            val map = mutableMapOf<String, String>()
            session.parseBody(map)
            map["postData"] ?: "{}"
        }

        val json     = JSONObject(body)
        val newAudio = json.optString("audio", existing.audio)

        if (newAudio != existing.audio) {
            val oldFile = File(audioDir, existing.audio)
            if (oldFile.exists()) {
                oldFile.delete()
                Log.i(TAG, "PUT /songs/$id → Ancien fichier supprimé : ${existing.audio}")
            }
        }

        val updated = existing.copy(
            name        = json.optString("name",        existing.name),
            description = json.optString("description", existing.description),
            audio       = newAudio,
            id_web      = json.optString("id_web",      existing.id_web)
        )
        db.alerteAudioDao().update(updated)
        Log.i(TAG, "PUT /songs/$id → Son modifié : ${updated.name}")
        jsonResponse("""{"success":true,"message":"Son modifié"}""")
    }

    // ════════════════════════════════════════════════════════
    // SONGS — DELETE /songs/{id}
    // ════════════════════════════════════════════════════════
    private fun deleteSong(uri: String): Response = runBlocking {
        val id = uri.removePrefix("/songs/").toIntOrNull()
            ?: return@runBlocking errorResponse(400, "ID invalide")

        val song = db.alerteAudioDao().getAll().find { it.id == id }
            ?: return@runBlocking errorResponse(404, "Son non trouvé")

        db.alerteAudioDao().delete(song)

        val audioFile = File(audioDir, song.audio)
        if (audioFile.exists()) {
            audioFile.delete()
            Log.i(TAG, "DELETE /songs/$id → Fichier supprimé : ${audioFile.name}")
        }

        Log.i(TAG, "DELETE /songs/$id → Son supprimé : ${song.name}")
        jsonResponse("""{"success":true,"message":"Son supprimé"}""")
    }

    // ════════════════════════════════════════════════════════
    // UPLOAD — POST /upload
    // ════════════════════════════════════════════════════════
    private fun uploadAudio(session: IHTTPSession): Response = runBlocking {
        try {
            val files = mutableMapOf<String, String>()
            session.parseBody(files)

            val filename =
                session.parameters["filename"]?.firstOrNull()
                    ?: session.parameters["file"]?.firstOrNull()
                    ?: files["filename"]
                    ?: files.keys.firstOrNull { it != "file" && it != "postData" }?.let { files[it] }
                    ?: run {
                        val tempPath = files["file"] ?: files.values.firstOrNull()
                        if (tempPath != null) File(tempPath).name else null
                    }
                    ?: return@runBlocking errorResponse(400, "Champ 'filename' manquant")

            val tempPath = files["file"]
                ?: files.values.firstOrNull { it.startsWith("/") && File(it).exists() }
                ?: return@runBlocking errorResponse(400, "Champ 'file' manquant")

            val tempFile = File(tempPath)
            if (!tempFile.exists()) {
                return@runBlocking errorResponse(500, "Fichier temporaire introuvable : $tempPath")
            }

            if (!audioDir.exists()) audioDir.mkdirs()

            val dest = File(audioDir, filename)
            if (dest.exists()) dest.delete()

            tempFile.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }

            Log.i(TAG, "POST /upload → Fichier sauvegardé : ${dest.absolutePath} (${dest.length()} bytes)")
            jsonResponse("""{"success":true,"message":"Fichier uploadé","filename":"$filename"}""")

        } catch (e: Exception) {
            Log.e(TAG, "Erreur upload : ${e.message}", e)
            errorResponse(500, "Erreur upload : ${e.message}")
        }
    }

    // ════════════════════════════════════════════════════════
    // VOLUME — GET /volume
    // ════════════════════════════════════════════════════════
    private fun getVolume(): Response {
        val volume = volumeManager.getVolume()
        val json   = JSONObject().apply { put("volume", volume) }
        Log.i(TAG, "GET /volume → $volume")
        return jsonResponse(json.toString())
    }

    // ════════════════════════════════════════════════════════
    // VOLUME — PUT /volume
    // ════════════════════════════════════════════════════════
    private fun updateVolume(session: IHTTPSession): Response = runBlocking {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            val buffer = ByteArray(contentLength)
            session.inputStream.read(buffer, 0, contentLength)
            String(buffer, Charsets.UTF_8)
        } else {
            val map = mutableMapOf<String, String>()
            session.parseBody(map)
            map["postData"] ?: "{}"
        }

        val json   = JSONObject(body)
        val volume = json.getDouble("volume").toFloat()
        volumeManager.setVolume(volume)
        jsonResponse("""{"success":true,"message":"Volume appliqué : ${(volume * 100).toInt()}%"}""")
    }

    // ════════════════════════════════════════════════════════
    // UTILITAIRES
    // ════════════════════════════════════════════════════════
    private fun parseBody(session: IHTTPSession): String {
        val map = mutableMapOf<String, String>()
        session.parseBody(map)
        return map["postData"] ?: "{}"
    }

    private fun jsonResponse(json: String): Response {
        val response = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        addCorsHeaders(response)
        return response
    }

    private fun corsResponse(): Response {
        val response = newFixedLengthResponse(Response.Status.OK, "application/json", "{}")
        addCorsHeaders(response)
        return response
    }

    private fun errorResponse(code: Int, message: String): Response {
        val status = when (code) {
            400  -> Response.Status.BAD_REQUEST
            404  -> Response.Status.NOT_FOUND
            else -> Response.Status.INTERNAL_ERROR
        }
        val response = newFixedLengthResponse(status, "application/json", """{"error":"$message"}""")
        addCorsHeaders(response)
        return response
    }

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin",  "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        response.addHeader("Content-Type", "application/json; charset=utf-8")
    }
}