package com.ewsmitao.allo_mitao_mobile.service

import com.google.firebase.messaging.FirebaseMessagingService
import android.util.Log
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMService", "Nouveau token: $token")
        CoroutineScope(Dispatchers.IO).launch {
            val uuid = CerveauIdentity.getOrCreateUuid(applicationContext)
            DeviceSyncService.syncToBackend(applicationContext, uuid, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCMService", "Message reçu: ${remoteMessage.data}")
    }
}