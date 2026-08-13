package com.ewsmitao.allo_mitao_mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ewsmitao.allo_mitao_mobile.service.CerveauIdentity
import com.ewsmitao.allo_mitao_mobile.service.DeviceSyncService
import com.ewsmitao.allo_mitao_mobile.service.FcmTokenManager
import com.ewsmitao.allo_mitao_mobile.service.NetworkService
import com.ewsmitao.allo_mitao_mobile.service.ServerSyncWorker
import com.ewsmitao.allo_mitao_mobile.service.SyncWorker
import com.ewsmitao.allo_mitao_mobile.ui.AppNav
import com.ewsmitao.allo_mitao_mobile.ui.theme.Sirene_managerTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import android.media.AudioManager
import android.media.AudioDeviceInfo
import com.ewsmitao.allo_mitao_mobile.database.AppDatabase
import android.hardware.usb.UsbManager
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri

// Activité principale — version UNIFIÉE (SMS + FCM)
class MainActivity : ComponentActivity() {

    private val tag = "MainActivity"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // ✅ NOUVEAU : une fois les permissions accordées, on tente de créer
        // le dossier audio. Sur Android ≤12, WRITE_EXTERNAL_STORAGE est
        // nécessaire avant de pouvoir écrire dans Music/. On recrée ici
        // au cas où la permission venait d'être accordée pour la première fois.
        createAudioDirectory()
        launchSyncSequence()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions() // Demande les permissions SMS + notifications + stockage
        createAudioDirectory()
        startNetworkService()        // Lance le service HTTP en premier plan
//        logAudioDevices()
//        logUsbDevices()
        enableEdgeToEdge()
        requestBatteryOptimizationExemption() // ← déjà là
        openBatterySettingsOnce()

        // ✅ Si permissions déjà accordées → lancer directement
        // Sinon → permissionLauncher appellera launchSyncSequence()
        if (hasStoragePermission()) {
            launchSyncSequence()
        }

        setContent {
            Sirene_managerTheme {
                AppNav()
            }
        }
    }

    // ── Fonctions privées ─────────────────────────────────────────────────────

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun openBatterySettings() {
        val intent = when (Build.MANUFACTURER.lowercase()) {
            "xiaomi"  -> Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            "huawei"  -> Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
            "oppo"    -> Intent().apply {
                component = android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            }
            "samsung" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            else      -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }

    private fun openBatterySettingsOnce() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("battery_settings_shown", false)) {
            openBatterySettings()
            prefs.edit().putBoolean("battery_settings_shown", true).apply()
        }
    }

    private fun launchSyncSequence() {
        lifecycleScope.launch {
            val uuid  = CerveauIdentity.getOrCreateUuid(this@MainActivity)
            val token = FirebaseMessaging.getInstance().token.await()
            DeviceSyncService.syncToBackend(this@MainActivity, uuid, token)
            ServerSyncWorker.runNow(this@MainActivity)
            ServerSyncWorker.schedule(this@MainActivity)
        }
    }

    private fun logUsbDevices() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usbManager.deviceList

        Log.d("UsbDevices", "🔌 ${devices.size} périphérique(s) USB :")
        devices.forEach { (name, device) ->
            Log.d("UsbDevices", "  → $name | Class: ${device.deviceClass} | VendorId: ${device.vendorId} | ProductId: ${device.productId}")
        }
    }

    private fun logAudioDevices() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        Log.d("AudioDevices", "🔊 ${devices.size} sortie(s) audio disponible(s) :")
        devices.forEach { device ->
            val type = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER    -> "Haut-parleur intégré"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES   -> "Casque filaire"
                AudioDeviceInfo.TYPE_WIRED_HEADSET      -> "Headset filaire"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP     -> "Bluetooth A2DP"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO      -> "Bluetooth SCO"
                AudioDeviceInfo.TYPE_HDMI               -> "HDMI"
                AudioDeviceInfo.TYPE_HDMI_ARC           -> "HDMI ARC"
                AudioDeviceInfo.TYPE_USB_DEVICE         -> "USB Device"
                AudioDeviceInfo.TYPE_USB_ACCESSORY      -> "USB Accessory"
                AudioDeviceInfo.TYPE_USB_HEADSET        -> "USB Headset"
                AudioDeviceInfo.TYPE_LINE_ANALOG        -> "Jack 3.5mm"
                AudioDeviceInfo.TYPE_LINE_DIGITAL       -> "Ligne numérique"
                AudioDeviceInfo.TYPE_AUX_LINE           -> "AUX"
                else -> "Inconnu (type=${device.type})"
            }
            Log.d("AudioDevices", "  → ID:${device.id} | $type | nom: ${device.productName}")
        }
    }


    private fun hasStoragePermission(): Boolean {
        // Android 13+ n'a pas besoin de WRITE_EXTERNAL_STORAGE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ✅ NOUVEAU — Crée le dossier Music/AlloMitaoAudio/ s'il n'existe pas encore.
    //
    // EXPLICATION : On crée le dossier ici dans MainActivity ET dans NetworkService
    // pour couvrir deux scénarios différents :
    //   • L'utilisateur ouvre l'app normalement → MainActivity crée le dossier
    //   • Le téléphone redémarre et le service repart sans ouvrir l'UI
    //     → NetworkService crée le dossier tout seul
    // mkdirs() (avec un "s") crée aussi tous les dossiers parents manquants,
    // contrairement à mkdir() qui échoue si le parent n'existe pas.
    private fun createAudioDirectory() {
        val audioDir = File(
            getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            "AlloMitaoAudio"
        )
        if (!audioDir.exists()) {
            audioDir.mkdirs()
            Log.i(tag, "✅ Dossier audio créé : ${audioDir.absolutePath}")
        } else {
            Log.i(tag, "📁 Dossier audio déjà présent : ${audioDir.absolutePath}")
        }
    }

    // Démarre le service en premier plan (serveur HTTP + file de commandes)
    private fun startNetworkService() {
        val intent = Intent(this, NetworkService::class.java)
        startForegroundService(intent)
    }

    // Planifie une sync locale unique au démarrage (si réseau disponible)
    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "sirene_sync",
            androidx.work.ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    // Sync périodique toutes les 6h + sync immédiate
    private fun scheduleServerSync() {
        ServerSyncWorker.schedule(this)
        val request = OneTimeWorkRequestBuilder<ServerSyncWorker>().build()
        WorkManager.getInstance(this).enqueue(request)
    }

    // Récupère le token FCM et l'envoie au backend.
    // Gère le cas où le token aurait été rafraîchi entre deux démarrages.
    private fun registerFcmToken() {
        FcmTokenManager.registerCurrentToken(this)
    }

    // Demande toutes les permissions nécessaires au runtime.
    //
    // EXPLICATION : Android distingue deux types de permissions :
    //   • "Normales" (déclarées dans le Manifest) → accordées automatiquement
    //   • "Dangereuses" (SMS, stockage, notifications) → l'utilisateur doit
    //     explicitement dire OUI dans une boîte de dialogue système
    // Cette fonction ne demande QUE les permissions pas encore accordées
    // (filter { checkSelfPermission != PERMISSION_GRANTED }) pour ne pas
    // redemander à chaque démarrage ce que l'utilisateur a déjà accepté.
    //
    // WRITE_EXTERNAL_STORAGE :
    //   • Android ≤12 (SDK 32) : nécessaire pour écrire dans Music/
    //   • Android 13+ (SDK 33) : supprimée, remplacée par READ_MEDIA_AUDIO
    //     (écrire dans Music/ public ne nécessite plus de permission spéciale
    //     depuis Android 13 pour les apps qui créent leurs propres fichiers)
    private fun requestRequiredPermissions() {
        val needed = buildList {
            // Permissions SMS (déclenchement via SMS + whitelist)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.SEND_SMS)
//            add(Manifest.permission.ACCESS_FINE_LOCATION)
//            add(Manifest.permission.ACCESS_COARSE_LOCATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ : notifications FCM + accès aux fichiers audio
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                // Android ≤12 : accès au stockage externe en lecture ET écriture
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                // ✅ NOUVEAU : indispensable pour créer Music/SireneManager/ sur Android ≤12
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}