package com.ewsmitao.allo_mitao_mobile.service

import android.content.Context
import android.util.Log
import androidx.work.*

// Worker WorkManager : remet en file une boucle d'alerte après un délai
// Survit aux redémarrages du téléphone grâce à WorkManager
class AlerteWorker(
    context: Context,
    params:  WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Récupérer les paramètres passés par SmsProcessor
        val sender        = inputData.getString(KEY_SENDER)      ?: return Result.failure()
        val idWeb         = inputData.getString(KEY_ID_WEB)      ?: return Result.failure()
        val nbrBoucle     = inputData.getInt(KEY_NBR_BOUCLE, 1)
        val intervalleMin = inputData.getLong(KEY_INTERVALLE_MIN, 0L)
        val boucleNext    = inputData.getInt(KEY_BOUCLE_NEXT, 1)
        val audioPath     = inputData.getString(KEY_AUDIO_PATH)  ?: ""
        val priority      = inputData.getInt(KEY_PRIORITY, 2)

        Log.i("AlerteWorker", "⏰ Reprise après redémarrage/délai : $idWeb boucle $boucleNext/$nbrBoucle [P$priority]")

        // Reconstruire la commande et la remettre dans la file
        val cmd = SmsQueue.SmsCommand(
            sender         = sender,
            message        = "",
            context        = applicationContext,
            priority       = priority,
            arrivalOrder   = System.currentTimeMillis(),
            idWeb          = idWeb,
            nbrBoucle      = nbrBoucle,
            intervalleMin  = intervalleMin,
            boucleActuelle = boucleNext,
            audioPath      = audioPath
        )

        // S'assurer que la file est démarrée (cas de redémarrage du téléphone)
        SmsQueue.start()
        SmsQueue.enqueueCommand(cmd)

        return Result.success()
    }

    // Clés des données d'entrée du Worker
    companion object {
        const val KEY_SENDER         = "sender"
        const val KEY_ID_WEB         = "id_web"
        const val KEY_NBR_BOUCLE     = "nbr_boucle"
        const val KEY_INTERVALLE_MIN = "intervalle_min"
        const val KEY_BOUCLE_NEXT    = "boucle_next"
        const val KEY_AUDIO_PATH     = "audio_path"
        const val KEY_PRIORITY       = "priority"
    }
}
