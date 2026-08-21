package com.ewsmitao.allo_mitao_mobile

// Constantes globales de configuration — version UNIFIÉE (SMS + FCM)
object AppConfig {

    // URL de synchronisation du UUID et FCM token du cerveau et l'app web
    const val BASE_URL_PREPROD = "https://sirene.manager.preprod.mitao-forecast.com/backend_sirene_preprod"
    const val BASE_URL_PROD = "https://mitao-forecast.com/backend"

    const val PLAYBACK_ACK_URL = "$BASE_URL_PREPROD/send-alerte-bngrc" // + "/{id}/ack"
    const val PLAYBACK_ACK_URL_ANN = "$BASE_URL_PREPROD/notifications"

    const val DEVICE_REGISTER_ENDPOINT = "$BASE_URL_PREPROD/sirenes/register"
    // Timeouts (ms)
    const val CONNECT_TIMEOUT = 10_000
    const val READ_TIMEOUT    = 10_000
    // ── URLs de synchronisation + téléchargement ─────────────────────────────
    // Chaque URL gère à la fois la liste des sons ET leur téléchargement.

    // ANN (Annonces) — ancien lien inchangé
    // L'IMEI/UUID est passé en query param : ?imei=xxx
    const val SERVER_SYNC_URL_ANN = "$BASE_URL_PREPROD/alerte-audios/public/sync-all"

    // ALT (Alertes) — nouveau lien
    // L'IMEI est intégré directement dans le path : /sync/{IMEI}
    const val SERVER_SYNC_URL_ALT = "$BASE_URL_PREPROD/audio-alerte-bngrc/public/sync"

    // Intervalle de sync périodique (WorkManager impose 15min minimum)
    // Sert de filet de rattrapage si un FCM "sync" n'arrive pas
    const val SYNC_INTERVAL_MINUTES = 15L

    // SharedPreferences
    const val PREFS_NAME = "sirene_config_prefs"
    const val UUID_KEY   = "cerveau_uuid"

    // ── FCM ──────────────────────────────────────────────────────────────────
    // URL d'enregistrement du token FCM — l'AndroidID est ajouté dynamiquement dans FcmTokenManager
    // Format final : https://mitao-forecast.com/backend/sirenes/fcm-token/{androidId}
    const val FCM_REGISTER_BASE_URL = "$BASE_URL_PREPROD/sirenes/fcm-token"

    // Clé API à envoyer dans le header x-api-key
    const val FCM_API_KEY = "9f3c2a7b8e1d4c6f0a2b9d5e7f8c1a3b6d4e2f9a0c7b1d3e5f6a8c9b0d2e4f6"
}