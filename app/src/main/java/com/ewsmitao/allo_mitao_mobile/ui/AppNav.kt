package com.ewsmitao.allo_mitao_mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.ewsmitao.allo_mitao_mobile.database.AlerteAudio
import com.ewsmitao.allo_mitao_mobile.database.Whitelist
import com.ewsmitao.allo_mitao_mobile.service.CerveauIdentity
import com.ewsmitao.allo_mitao_mobile.service.DeviceSyncService
import com.ewsmitao.allo_mitao_mobile.ui.theme.*
import com.ewsmitao.allo_mitao_mobile.ui.viewmodel.SongsViewModel
import com.ewsmitao.allo_mitao_mobile.ui.viewmodel.WhitelistViewModel
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Définition des onglets de navigation
private val tabs      = listOf("audios", "whitelist")
private val tabTitles = listOf("Sons / Alertes", "Whitelist")
private val tabIcons  = listOf(Icons.Filled.MusicNote, Icons.Filled.PhoneAndroid)

// Racine de la navigation : drawer latéral + contenu principal
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav() {
    val context       = LocalContext.current
    val navController = rememberNavController()
    val drawerState   = rememberDrawerState(DrawerValue.Closed)
    val scope         = rememberCoroutineScope()

    // UUID unique de cette sirène, affiché dans le drawer
    val uuid = remember { CerveauIdentity.getOrCreateUuid(context) }

    // Token FCM, chargé une fois à l'ouverture
    var fcmToken by remember { mutableStateOf("Chargement...") }
    LaunchedEffect(Unit) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> fcmToken = token }
            .addOnFailureListener { fcmToken = "Indisponible" }
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))

                // ── En-tête Drawer ────────────────────────────────────────────
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Allô Mitao",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = YellowAlert
                    )
                    Text(
                        "Cerveau",
                        fontSize = 13.sp,
                        color    = WhiteSoft.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = NavyLight)
                Spacer(Modifier.height(12.dp))

                // ── Affichage et copie de l'UUID ──────────────────────────────
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        "Identifiant sirène",
                        fontSize = 11.sp,
                        color    = WhiteSoft.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = uuid,
                            fontSize = 11.sp,
                            color    = YellowAlert,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Bouton copier l'UUID dans le presse-papiers
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("UUID Sirène", uuid))
                                Toast.makeText(context, "UUID copié !", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copier UUID",
                                tint     = WhiteSoft.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = NavyLight)
                Spacer(Modifier.height(12.dp))

                // ── Affichage et copie du Token FCM ───────────────────────────
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        "Token FCM",
                        fontSize = 11.sp,
                        color    = WhiteSoft.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = fcmToken,
                            fontSize = 11.sp,
                            color    = YellowAlert,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Bouton copier le Token FCM dans le presse-papiers
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Token FCM", fcmToken))
                                Toast.makeText(context, "Token FCM copié !", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copier Token FCM",
                                tint     = WhiteSoft.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = NavyLight)
                Spacer(Modifier.height(8.dp))

                // ── Items de navigation dans le drawer ────────────────────────
                NavigationDrawerItem(
                    icon     = { Icon(Icons.Filled.MusicNote, null) },
                    label    = { Text("Sons / Alertes") },
                    selected = true,
                    onClick  = { scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon     = { Icon(Icons.Filled.PhoneAndroid, null) },
                    label    = { Text("Whitelist") },
                    selected = false,
                    onClick  = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        // Contenu principal — TabRow avec les deux onglets
        MainContent(
            onOpenDrawer = { scope.launch { drawerState.open() } }
        )
    }
}

// Écran principal : TopBar, TabRow (Sons / Whitelist) et FAB contextuel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(onOpenDrawer: () -> Unit) {
    val context        = LocalContext.current
    val songsVm        = viewModel<SongsViewModel>()
    val whitelistVm    = viewModel<WhitelistViewModel>()
    val songs          = songsVm.songs.collectAsStateWithLifecycle().value
    val whitelist      = whitelistVm.numbers.collectAsStateWithLifecycle().value
    var selectedTab    by remember { mutableIntStateOf(0) }

    // États des dialogs d'ajout / modification / suppression
    var showAddSong      by remember { mutableStateOf(false) }
    var editSong         by remember { mutableStateOf<AlerteAudio?>(null) }
    var deleteSong       by remember { mutableStateOf<AlerteAudio?>(null) }
    var showAddWhitelist by remember { mutableStateOf(false) }
    var deleteWhitelist  by remember { mutableStateOf<Whitelist?>(null) }

    // Launcher pour sélectionner un fichier audio (non utilisé directement ici)
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { /* handled in dialog */ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Allô Mitao", fontWeight = FontWeight.Bold, color = WhiteSoft) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, null, tint = WhiteSoft)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavySurface)
            )
        },
        // FAB : "Ajouter son" sur l'onglet Sons, "Ajouter numéro" sur l'onglet Whitelist
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { if (selectedTab == 0) showAddSong = true else showAddWhitelist = true },
                containerColor = YellowAlert,
                contentColor   = NavyDeep
            ) { Icon(Icons.Filled.Add, null) }
        },
        containerColor = NavyDeep
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Onglets de navigation
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = NavySurface,
                contentColor     = YellowAlert
            ) {
                tabs.forEachIndexed { index, _ ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        icon     = { Icon(tabIcons[index], null, modifier = Modifier.size(18.dp)) },
                        text     = { Text(tabTitles[index], fontSize = 13.sp) }
                    )
                }
            }

            // Affichage conditionnel selon l'onglet sélectionné
            when (selectedTab) {
                0 -> SongsTab(
                    songs     = songs,
                    onEdit    = { editSong   = it },
                    onDelete  = { deleteSong = it }
                )
                1 -> WhitelistTab(
                    whitelist = whitelist,
                    onDelete  = { deleteWhitelist = it }
                )
            }
        }
    }

    // ── Dialogs Sons ──────────────────────────────────────────────────────────
    if (showAddSong) {
        SongDialog(
            title     = "Ajouter un son",
            song      = null,
            context   = context,
            onDismiss = { showAddSong = false },
            onConfirm = { song -> songsVm.insert(song); showAddSong = false }
        )
    }
    editSong?.let { song ->
        SongDialog(
            title     = "Modifier le son",
            song      = song,
            context   = context,
            onDismiss = { editSong = null },
            onConfirm = { updated -> songsVm.update(updated); editSong = null }
        )
    }
    // Dialog de confirmation de suppression d'un son
    deleteSong?.let { song ->
        AlertDialog(
            onDismissRequest = { deleteSong = null },
            title            = { Text("Supprimer ?", color = WhiteSoft) },
            text             = { Text("Supprimer \"${song.name}\" ?", color = WhiteSoft.copy(alpha = 0.7f)) },
            confirmButton    = {
                TextButton(onClick = { songsVm.delete(song); deleteSong = null }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { deleteSong = null }) { Text("Annuler") }
            },
            containerColor   = NavySurface
        )
    }

    // ── Dialogs Whitelist ─────────────────────────────────────────────────────
    if (showAddWhitelist) {
        WhitelistDialog(
            onDismiss = { showAddWhitelist = false },
            onConfirm = { number -> whitelistVm.insert(number); showAddWhitelist = false }
        )
    }
    // Dialog de confirmation de suppression d'un numéro
    deleteWhitelist?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteWhitelist = null },
            title            = { Text("Supprimer ?", color = WhiteSoft) },
            text             = { Text("Supprimer ${item.phone_number} ?", color = WhiteSoft.copy(alpha = 0.7f)) },
            confirmButton    = {
                TextButton(onClick = { whitelistVm.delete(item); deleteWhitelist = null }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { deleteWhitelist = null }) { Text("Annuler") }
            },
            containerColor   = NavySurface
        )
    }
}

// ── Tab Sons : liste scrollable des sons configurés ───────────────────────────
@Composable
fun SongsTab(songs: List<AlerteAudio>, onEdit: (AlerteAudio) -> Unit, onDelete: (AlerteAudio) -> Unit) {
    if (songs.isEmpty()) {
        // État vide
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.MusicOff, null, tint = NavyLight, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("Aucun son configuré", color = WhiteSoft.copy(alpha = 0.5f))
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(songs, key = { it.id }) { song ->
                Card(
                    colors    = CardDefaults.cardColors(containerColor = NavySurface),
                    modifier  = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier          = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(song.name, fontWeight = FontWeight.Bold, color = WhiteSoft)
                            Text("ID: ${song.id_web}", fontSize = 12.sp, color = YellowAlert)
                            Text("🎵 ${song.audio}", fontSize = 11.sp, color = WhiteSoft.copy(alpha = 0.5f))
                        }
                        IconButton(onClick = { onEdit(song) }) {
                            Icon(Icons.Filled.Edit, null, tint = YellowAlert)
                        }
                        IconButton(onClick = { onDelete(song) }) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// ── Tab Whitelist : liste scrollable des numéros autorisés ───────────────────
@Composable
fun WhitelistTab(whitelist: List<Whitelist>, onDelete: (Whitelist) -> Unit) {
    if (whitelist.isEmpty()) {
        // État vide
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.PhoneDisabled, null, tint = NavyLight, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("Aucun numéro autorisé", color = WhiteSoft.copy(alpha = 0.5f))
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(whitelist, key = { it.id }) { item ->
                Card(
                    colors   = CardDefaults.cardColors(containerColor = NavySurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier          = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Phone, null, tint = YellowAlert)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.phone_number, fontWeight = FontWeight.Bold, color = WhiteSoft)
                        }
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// ── Dialog Ajouter/Modifier Son ───────────────────────────────────────────────
@Composable
fun SongDialog(
    title:     String,
    song:      AlerteAudio?,  // null = ajout, non-null = modification
    context:   Context,
    onDismiss: () -> Unit,
    onConfirm: (AlerteAudio) -> Unit
) {
    // Pré-remplir les champs si modification
    var name        by remember { mutableStateOf(song?.name ?: "") }
    var idWeb       by remember { mutableStateOf(song?.id_web ?: "") }
    var audio       by remember { mutableStateOf(song?.audio ?: "") }
    var description by remember { mutableStateOf(song?.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = NavySurface,
        title            = { Text(title, color = WhiteSoft) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Génération des champs de formulaire à partir d'une liste de triplets (label, valeur, onChange)
                listOf(
                    Triple("Nom", name) { v: String -> name = v },
                    Triple("ID Web", idWeb) { v: String -> idWeb = v },
                    Triple("Fichier audio", audio) { v: String -> audio = v },
                    Triple("Description", description) { v: String -> description = v }
                ).forEach { (label, value, onChange) ->
                    OutlinedTextField(
                        value         = value,
                        onValueChange = onChange,
                        label         = { Text(label) },
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = YellowAlert,
                            unfocusedBorderColor = NavyLight,
                            focusedLabelColor    = YellowAlert,
                            unfocusedLabelColor  = WhiteSoft.copy(alpha = 0.5f),
                            focusedTextColor     = WhiteSoft,
                            unfocusedTextColor   = WhiteSoft,
                            cursorColor          = YellowAlert
                        )
                    )
                }
            }
        },
        confirmButton = {
            // Valider uniquement si les champs obligatoires sont remplis
            TextButton(onClick = {
                if (name.isNotBlank() && idWeb.isNotBlank() && audio.isNotBlank()) {
                    onConfirm(AlerteAudio(
                        id          = song?.id ?: 0,
                        name        = name,
                        id_web      = idWeb,
                        audio       = audio,
                        description = description
                    ))
                }
            }) { Text("Confirmer", color = YellowAlert) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = WhiteSoft.copy(alpha = 0.5f)) }
        }
    )
}

// ── Dialog Ajouter Whitelist ──────────────────────────────────────────────────
@Composable
fun WhitelistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = NavySurface,
        title            = { Text("Ajouter un numéro", color = WhiteSoft) },
        text = {
            OutlinedTextField(
                value         = phone,
                onValueChange = { phone = it },
                label         = { Text("Numéro (+261XXXXXXXXX)") },
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = YellowAlert,
                    unfocusedBorderColor = NavyLight,
                    focusedLabelColor    = YellowAlert,
                    focusedTextColor     = WhiteSoft,
                    unfocusedTextColor   = WhiteSoft,
                    cursorColor          = YellowAlert
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { if (phone.isNotBlank()) onConfirm(phone) }) {
                Text("Ajouter", color = YellowAlert)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = WhiteSoft.copy(alpha = 0.5f)) }
        }
    )
}