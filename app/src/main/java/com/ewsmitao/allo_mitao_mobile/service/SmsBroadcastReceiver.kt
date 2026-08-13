package com.ewsmitao.allo_mitao_mobile.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * SmsBroadcastReceiver — reçoit les SMS entrants et les injecte dans SmsQueue.
 * [INCHANGÉ depuis Sirene_cerveau2]
 *
 * La vérification de la whitelist est effectuée dans SmsProcessor.processCommand()
 * pour les commandes issues de cette source (sender != "FCM_SERVER").
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress
            val body   = sms.messageBody.trim()
            Log.i("SmsBroadcastReceiver", "📱 SMS reçu de $sender : $body")
            SmsQueue.enqueue(context, sender, body)
        }
    }
}
