package com.ewsmitao.allo_mitao_mobile.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ewsmitao.allo_mitao_mobile.database.AlerteAudio
import com.ewsmitao.allo_mitao_mobile.database.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SongsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).alerteAudioDao()

    // Flow Room → StateFlow Compose
    // Se met à jour automatiquement quand la DB change
    val songs: StateFlow<List<AlerteAudio>> = dao.getAllFlow()
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = emptyList()
        )

    fun insert(audio: AlerteAudio) = viewModelScope.launch {
        dao.insert(audio)
    }

    fun update(audio: AlerteAudio) = viewModelScope.launch {
        dao.update(audio)
    }

    fun delete(audio: AlerteAudio) = viewModelScope.launch {
        dao.delete(audio)
    }
}