package com.ewsmitao.allo_mitao_mobile.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import com.ewsmitao.allo_mitao_mobile.R
import com.ewsmitao.allo_mitao_mobile.database.AppDatabase
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SmsQueue — version UNIFIÉE (SMS + FCM).
 *
 * CORRECTION CRITIQUE :
 * - Le scope n'est plus annulé dans stop() car un CoroutineScope annulé
 *   ne peut jamais être relancé. On utilise un Job annulable et remplaçable.
 * - isStarted est remis à false dans stop() pour permettre un vrai redémarrage.
 * - start() recrée toujours un nouveau Job propre.
 */
object SmsQueue {

    private val TAG       = "SmsQueue"
    private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    data class SmsCommand(
        val sender:         String,
        val message:        String,
        val context:        Context,
        val priority:       Int,
        val arrivalOrder:   Long,
        val idWeb:          String,
        val nbrBoucle:      Int,
        val intervalleMin:  Long,
        val boucleActuelle: Int,
        val audioPath:      String,
        val notifId:        Long? = null,
        val scheduledTime:  LocalDateTime? = null
    ) : Comparable<SmsCommand> {

        val effectiveTime: LocalDateTime
            get() = scheduledTime ?: LocalDateTime.MIN

        override fun compareTo(other: SmsCommand): Int {
            if (this.priority != other.priority)
                return this.priority.compareTo(other.priority)
            val timeCmp = this.effectiveTime.compareTo(other.effectiveTime)
            if (timeCmp != 0) return timeCmp
            return this.arrivalOrder.compareTo(other.arrivalOrder)
        }
    }

    private val queue        = PriorityBlockingQueue<SmsCommand>()
    private val isStarted    = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)

    // CORRECTION : scope permanent qui ne sera jamais annulé.
    // On annule uniquement le Job de la boucle principale, pas le scope.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopJob: Job? = null

    @Synchronized
    fun start() {
        // Si la boucle est déjà active, ne rien faire
        if (isStarted.get() && loopJob?.isActive == true) {
            Log.i(TAG, "ℹ️ File déjà démarrée — ignoré")
            return
        }

        // Annuler l'ancien job si existant (mais PAS le scope)
        loopJob?.cancel()
        isStarted.set(true)
        isProcessing.set(false)

        Log.i(TAG, "✅ File de commandes démarrée (SMS + FCM)")

        loopJob = scope.launch {
            while (isActive) {
                try {
                    val now  = LocalDateTime.now()
                    val next = findNextReady(now)

                    if (next == null) {
                        delay(1_000L)
                        continue
                    }

                    if (isProcessing.get()) {
                        delay(500L)
                        continue
                    }

                    queue.remove(next)
                    Log.i(TAG, "▶ [P${next.priority}] ${next.idWeb} b${next.boucleActuelle}/${next.nbrBoucle} [${next.sender}]")
                    logQueueState()

                    isProcessing.set(true)
                    try {
                        SmsProcessor(next.context).processCommand(next)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur traitement : ${e.message}", e)
                    } finally {
                        isProcessing.set(false)
                    }

                } catch (e: CancellationException) {
                    // Boucle annulée proprement — on sort
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur boucle principale : ${e.message}", e)
                    delay(1_000L)
                }
            }
            isStarted.set(false)
            Log.i(TAG, "Boucle principale arrêtée")
        }
    }

    private fun findNextReady(now: LocalDateTime): SmsCommand? {
        return queue.toList()
            .sorted()
            .firstOrNull { cmd ->
                cmd.scheduledTime == null || !cmd.scheduledTime.isAfter(now)
            }
    }

    fun enqueue(context: Context, sender: String, rawMessage: String) {
        val cmd = parseCommand(context, sender, rawMessage) ?: return
        queue.offer(cmd)
        Log.i(TAG, "📥 [P${cmd.priority}] ${cmd.idWeb} ajouté [${sender}] — " +
                if (cmd.scheduledTime != null) "planifié à ${cmd.scheduledTime}"
                else "immédiat")
        logQueueState()
    }

    fun enqueueCommand(cmd: SmsCommand) {
        queue.offer(cmd)
        Log.i(TAG, "📥 Boucle suivante [P${cmd.priority}] ${cmd.idWeb} b${cmd.boucleActuelle}/${cmd.nbrBoucle}")
        logQueueState()
    }

    fun remove(cmd: SmsCommand) {
        queue.remove(cmd)
    }

    fun estimateDurationMs(cmd: SmsCommand): Long {
        var total = 0L
        total += getRawAudioDurationMs(cmd.context, cmd.idWeb)
        val audioFile = File(cmd.audioPath)
        if (audioFile.exists()) total += getFileDurationMs(audioFile)
        Log.i(TAG, "Durée estimée ${cmd.idWeb} : ${total / 1000}s")
        return total
    }

    fun getPlayableInInterval(intervalleRestantMs: Long): List<SmsCommand> {
        val now      = LocalDateTime.now()
        val snapshot = queue.toList()
            .filter { it.scheduledTime == null || !it.scheduledTime.isAfter(now) }
            .sorted()
        val playable  = mutableListOf<SmsCommand>()
        var remaining = intervalleRestantMs

        val p1 = snapshot.filter { it.priority == 1 }
        for (cmd in p1) {
            val duration = estimateDurationMs(cmd)
            if (duration <= remaining) {
                playable.add(cmd)
                remaining -= duration
            } else {
                Log.i(TAG, "P1 bloqué : ${cmd.idWeb} → P2 bloqués aussi")
                return playable
            }
        }

        val p2 = snapshot.filter { it.priority == 2 }
        for (cmd in p2) {
            val duration = estimateDurationMs(cmd)
            if (duration <= remaining) {
                playable.add(cmd)
                remaining -= duration
            } else {
                Log.i(TAG, "P2 bloqué : ${cmd.idWeb}")
                break
            }
        }

        return playable
    }

    /**
     * Parse une commande brute au format : "ID_WEB NB_BOUCLE INTERVALLE PRIORITE [DATE]"
     */
    private fun parseCommand(context: Context, sender: String, raw: String): SmsCommand? {
        val parts = raw.trim().split("\\s+".toRegex())
        if (parts.size < 5) {
            Log.w(TAG, "Format commande invalide (besoin d'au moins 4 champs) : $raw")
            return null
        }

        val idWeb         = parts[0]
        val nbrBoucle     = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val intervalleMin = SmsProcessor(context).parseIntervalle(parts[2])
        val priorityStr   = parts[3].uppercase()
        val priority      = if (priorityStr == "P1") 1 else 2
        val notifId       = parts[4].toLongOrNull()

        val scheduledTime: LocalDateTime? = parts.getOrNull(5)?.let { dateStr ->
            try {
                LocalDateTime.parse(dateStr, FORMATTER)
            } catch (e: Exception) {
                Log.w(TAG, "Date invalide '$dateStr', exécution immédiate")
                null
            }
        }

        val db       = AppDatabase.getInstance(context)

        val audioDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            "AlloMitaoAudio"
        )
        val audio = runBlocking {
            db.alerteAudioDao().getByIdWeb(idWeb)
        }
        val audioPath = if (audio != null) File(audioDir, audio.audio).absolutePath else ""

        return SmsCommand(
            sender         = sender,
            message        = raw,
            context        = context.applicationContext,
            priority       = priority,
            arrivalOrder   = System.currentTimeMillis(),
            idWeb          = idWeb,
            nbrBoucle      = nbrBoucle,
            intervalleMin  = intervalleMin,
            boucleActuelle = 1,
            audioPath      = audioPath,
            notifId = notifId,
            scheduledTime  = scheduledTime
        )
    }

    private fun getFileDurationMs(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            retriever.release()
            d
        } catch (e: Exception) {
            Log.w(TAG, "Durée inconnue ${file.name} : ${e.message}")
            30_000L
        }
    }

    private fun getRawAudioDurationMs(context: Context, idWeb: String): Long {
        val resId = if (idWeb.startsWith("ALT_", ignoreCase = true))
            R.raw.sirene else R.raw.annonce
        return try {
            val retriever = MediaMetadataRetriever()
            val afd = context.resources.openRawResourceFd(resId)
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            retriever.release()
            d
        } catch (e: Exception) {
            Log.w(TAG, "Durée intro inconnue : ${e.message}")
            10_000L
        }
    }

    private fun logQueueState() {
        val items = queue.toList().sorted()
        if (items.isEmpty()) {
            Log.i(TAG, "📋 File vide")
            return
        }
        Log.i(TAG, "📋 File (${items.size}) : ${items.joinToString(" | ") {
            "[P${it.priority}] ${it.idWeb} b${it.boucleActuelle}/${it.nbrBoucle}" +
                    if (it.scheduledTime != null) " @${it.scheduledTime}" else " (immédiat)"
        }}")
    }

    /**
     * CORRECTION : stop() annule uniquement le Job de la boucle, pas le scope.
     * Le scope reste vivant pour permettre un futur start() sans problème.
     */
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        isStarted.set(false)
        isProcessing.set(false)
        queue.clear()
        Log.i(TAG, "File arrêtée")
    }
}
