package com.ewsmitao.allo_mitao_mobile.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver — redémarre NetworkService après un boot ou un reboot rapide.
 *
 * CORRECTION : les deux actions BOOT_COMPLETED et QUICKBOOT_POWERON sont
 * maintenant toutes les deux traitées dans la logique (pas seulement dans
 * le Manifest). On démarre aussi NetworkService explicitement en foreground
 * pour éviter que Android ne tue le service immédiatement sur les ROMs
 * agressives (MIUI, One UI, ColorOS…).
 */
class BootReceiver : BroadcastReceiver() {

    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val isBoot = action == Intent.ACTION_BOOT_COMPLETED
                || action == "android.intent.action.QUICKBOOT_POWERON"
                || action == "com.htc.intent.action.QUICKBOOT_POWERON"

        if (!isBoot) return

        Log.i(tag, "Boot détecté ($action) → démarrage de NetworkService")
        val serviceIntent = Intent(context, NetworkService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
