package com.ewsmitao.allo_mitao_mobile.service

import android.content.Context
import android.media.MediaPlayer
import android.os.Environment
import android.util.Log
import androidx.work.*
import com.ewsmitao.allo_mitao_mobile.database.AlertsHistory
import com.ewsmitao.allo_mitao_mobile.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import android.media.AudioAttributes
import com.ewsmitao.allo_mitao_mobile.R

/**
 * SmsProcessor — version UNIFIÉE (SMS + FCM).
 *
 * [MODIFIÉ] La vérification de la whitelist est réactivée pour les commandes SMS.
 * Les commandes FCM (sender = "FCM_SERVER") contournent la whitelist car elles
 * viennent du backend de confiance.
 *
 * Chemin audio : Music/SireneManager (stockage externe public, identique au projet SMS).
 */
class SmsProcessor(private val context: Context) {

    private val tag           = "SmsProcessor"
    private val db            = AppDatabase.getInstance(context)
    private val volumeManager = VolumeManager(context)

    // Dossier audio sur le stockage externe public
//    private val audioDir: File = File(
//        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
//        "AlloMitaoAudio"
//    )
    private val audioDir: File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
        "AlloMitaoAudio"
    )

    private val SEUIL_WORKMANAGER_MINUTES = 1L

    // ── Point d'entrée depuis SmsQueue ────────────────────────────────────────
    suspend fun processCommand(cmd: SmsQueue.SmsCommand): Unit = withContext(Dispatchers.IO) {

        val isFcmCommand = cmd.sender == "FCM_SERVER" || cmd.sender.startsWith("FCM_")
        if (!isFcmCommand) {
            val allowed: Boolean = db.whitelistDao().isAllowed(cmd.sender) > 0
            if (!allowed) {
                Log.w(tag, "❌ ${cmd.sender} non autorisé (whitelist SMS)")
                saveHistory(cmd.sender, "BLOCKED", "")
                return@withContext
            }
        }

        val audio = db.alerteAudioDao().getByIdWeb(cmd.idWeb)
        if (audio == null) {
            Log.w(tag, "❌ Son introuvable : ${cmd.idWeb}")
            saveHistory(cmd.sender, "NOT_FOUND", cmd.idWeb)
            PlaybackAckService.sendAsync(cmd.idWeb, cmd.notifId, "failed", "audio_not_found_in_db") { ackSent ->
                AckNotificationHelper.show(context, cmd.idWeb, success = false, detail = "son introuvable en base")
            }
            return@withContext
        }

        val audioFile: File = File(audioDir, audio.audio)
        if (!audioFile.exists()) {
            Log.w(tag, "❌ Fichier introuvable : ${audioFile.absolutePath}")
            saveHistory(cmd.sender, "FAIL", cmd.idWeb)
            PlaybackAckService.sendAsync(cmd.idWeb, cmd.notifId, "failed", "audio_file_missing") { ackSent ->
                AckNotificationHelper.show(context, cmd.idWeb, success = false, detail = "fichier audio manquant")
            }
            return@withContext
        }

        val isAlerte: Boolean = cmd.idWeb.startsWith("ALT_", ignoreCase = true)
        val introResId: Int   = if (isAlerte) R.raw.sirene else R.raw.annonce
        val volume: Float     = volumeManager.getVolume()

        Log.i(tag, "🔊 [P${cmd.priority}] ${cmd.idWeb} boucle ${cmd.boucleActuelle}/${cmd.nbrBoucle} [source: ${cmd.sender}]")

        PlaybackAckService.sendAsync(cmd.idWeb, cmd.notifId, "playing")

        playRawAudio(introResId, volume)

        Log.i(tag, "AUDIO FILE SOURCE: ${audioFile.absolutePath}")
        val success = playFileAudio(audioFile, volume)

        if (success) {
            Log.i(tag, "✅ Boucle ${cmd.boucleActuelle}/${cmd.nbrBoucle} terminée")
        } else {
            Log.w(tag, "❌ Échec lecture boucle ${cmd.boucleActuelle}/${cmd.nbrBoucle} : ${cmd.idWeb}")
            PlaybackAckService.sendAsync(cmd.idWeb,cmd.notifId, "failed", "media_player_error") { ackSent ->
                AckNotificationHelper.show(
                    context, cmd.idWeb, success = false,
                    detail = if (ackSent) "lecture échouée (accusé transmis)" else "lecture échouée + accusé non transmis"
                )
            }
        }

        // 4. Gérer les boucles suivantes
        if (cmd.boucleActuelle < cmd.nbrBoucle) {
            val intervalleMs: Long = cmd.intervalleMin * 60_000L

            if (intervalleMs > 0L && cmd.intervalleMin >= SEUIL_WORKMANAGER_MINUTES) {
                val startWait: Long = System.currentTimeMillis()
                val playable: List<SmsQueue.SmsCommand> = SmsQueue.getPlayableInInterval(intervalleMs)

                for (interCmd in playable) {
                    val elapsed: Long   = System.currentTimeMillis() - startWait
                    val remaining: Long = intervalleMs - elapsed
                    val duration: Long  = SmsQueue.estimateDurationMs(interCmd)
                    if (duration <= remaining) {
                        Log.i(tag, "🎵 Intercalé pendant intervalle : ${interCmd.idWeb}")
                        SmsQueue.remove(interCmd)
                        SmsProcessor(context).processCommand(interCmd)
                    } else {
                        break
                    }
                }

                val elapsed: Long   = System.currentTimeMillis() - startWait
                val remaining: Long = (intervalleMs - elapsed).coerceAtLeast(0L)
                scheduleNextBoucleWorkManager(cmd, remaining)
                Log.i(tag, "📅 Boucle ${cmd.boucleActuelle + 1}/${cmd.nbrBoucle} via WorkManager dans ${remaining / 1000}s")
                return@withContext
            }

            val nextCmd: SmsQueue.SmsCommand = cmd.copy(boucleActuelle = cmd.boucleActuelle + 1)
            SmsQueue.enqueueCommand(nextCmd)
            Log.i(tag, "📥 Boucle ${nextCmd.boucleActuelle}/${cmd.nbrBoucle} remise en file")

        } else {
            Log.i(tag, "🏁 Terminé : ${cmd.idWeb}")
            saveHistory(cmd.sender, if (success) "SUCCESS" else "FAIL", cmd.idWeb)   // ← corrigé
            if (success) {
                PlaybackAckService.sendAsync(cmd.idWeb, cmd.notifId, "played") { ackSent ->
                    AckNotificationHelper.show(context, cmd.idWeb, success = ackSent)
                }
            }
            // le "failed" a déjà été envoyé plus haut avec sa propre notification si !success
        }
    }


    private fun scheduleNextBoucleWorkManager(cmd: SmsQueue.SmsCommand, delayMs: Long) {
        val workData: Data = workDataOf(
            AlerteWorker.KEY_SENDER         to cmd.sender,
            AlerteWorker.KEY_ID_WEB         to cmd.idWeb,
            AlerteWorker.KEY_NBR_BOUCLE     to cmd.nbrBoucle,
            AlerteWorker.KEY_INTERVALLE_MIN to cmd.intervalleMin,
            AlerteWorker.KEY_BOUCLE_NEXT    to (cmd.boucleActuelle + 1),
            AlerteWorker.KEY_AUDIO_PATH     to cmd.audioPath,
            AlerteWorker.KEY_PRIORITY       to cmd.priority,
            AlerteWorker.KEY_NOTIF_ID       to (cmd.notifId ?: -1L)   // ← nouveau,
        )
        val work = OneTimeWorkRequestBuilder<AlerteWorker>()
            .setInputData(workData)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag("alerte_${cmd.idWeb}")
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }

    fun parseIntervalle(raw: String): Long {
        val s: String = raw.trim().lowercase()
        return when {
            s == "0" || s.isBlank() -> 0L
            s.endsWith("min") -> s.removeSuffix("min").toLongOrNull() ?: 0L
            s.endsWith("h")   -> (s.removeSuffix("h").toLongOrNull() ?: 0L) * 60L
            s.endsWith("j")   -> (s.removeSuffix("j").toLongOrNull() ?: 0L) * 60L * 24L
            else              -> s.toLongOrNull() ?: 0L
        }
    }

//    suspend fun playRawAudio(resId: Int, volume: Float): Unit = withContext(Dispatchers.Main) {
//        suspendCoroutine<Unit> { continuation ->
//            try {
//                val player: MediaPlayer = MediaPlayer.create(context, resId)
//                player.setVolume(volume, volume)
//                player.setOnCompletionListener { mp -> mp.release(); continuation.resume(Unit) }
//                player.setOnErrorListener { mp, what, extra ->
//                    Log.e(tag, "MediaPlayer raw erreur what=$what extra=$extra")
//                    mp.release(); continuation.resume(Unit); true
//                }
//                player.start()
//            } catch (e: Exception) {
//                Log.e(tag, "Erreur lecture raw : ${e.message}")
//                continuation.resume(Unit)
//            }
//        }
//    }

//    suspend fun playFileAudio(file: File, volume: Float): Unit = withContext(Dispatchers.Main) {
//        suspendCoroutine<Unit> { continuation ->
//            try {
//                val player = MediaPlayer()
//                player.setDataSource(file.absolutePath)
//                player.prepare()
//                player.setVolume(volume, volume)
//                player.setOnCompletionListener { mp -> mp.release(); continuation.resume(Unit) }
//                player.setOnErrorListener { mp, what, extra ->
//                    Log.e(tag, "MediaPlayer fichier erreur what=$what extra=$extra")
//                    mp.release(); continuation.resume(Unit); true
//                }
//                player.start()
//            } catch (e: Exception) {
//                Log.e(tag, "Erreur lecture fichier : ${e.message}")
//                continuation.resume(Unit)
//            }
//        }
//    }

    suspend fun playRawAudio(resId: Int, volume: Float): Unit = withContext(Dispatchers.Main) {
        suspendCoroutine<Unit> { continuation ->
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val player: MediaPlayer = MediaPlayer()
                player.setAudioAttributes(attrs)
                val afd = context.resources.openRawResourceFd(resId)
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                player.prepare()
                player.setVolume(volume, volume)
                player.setOnCompletionListener { mp -> mp.release(); continuation.resume(Unit) }
                player.setOnErrorListener { mp, what, extra ->
                    Log.e(tag, "MediaPlayer raw erreur what=$what extra=$extra")
                    mp.release(); continuation.resume(Unit); true
                }
                player.start()
            } catch (e: Exception) {
                Log.e(tag, "Erreur lecture raw : ${e.message}")
                continuation.resume(Unit)
            }
        }
    }

    suspend fun playFileAudio(file: File, volume: Float): Boolean = withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val player = MediaPlayer()
                player.setAudioAttributes(attrs)
                player.setDataSource(file.absolutePath)
                player.prepare()
                player.setVolume(volume, volume)
                player.setOnCompletionListener { mp -> mp.release(); continuation.resume(true) }
                player.setOnErrorListener { mp, what, extra ->
                    Log.e(tag, "MediaPlayer fichier erreur what=$what extra=$extra")
                    mp.release(); continuation.resume(false); true
                }
                player.start()
            } catch (e: Exception) {
                Log.e(tag, "Erreur lecture fichier : ${e.message}")
                continuation.resume(false)
            }
        }
    }

    suspend fun saveHistory(sender: String, status: String, idWeb: String): Unit = withContext(Dispatchers.IO) {
        val date: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        db.alertsHistoryDao().insert(
            AlertsHistory(
                id_web       = idWeb,
                phone_number = sender,
                date         = date,
                status       = status
            )
        )
    }
}