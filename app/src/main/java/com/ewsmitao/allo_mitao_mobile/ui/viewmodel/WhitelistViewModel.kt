package com.ewsmitao.allo_mitao_mobile.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ewsmitao.allo_mitao_mobile.database.AppDatabase
import com.ewsmitao.allo_mitao_mobile.database.Whitelist
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WhitelistViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).whitelistDao()

    // Flow Room → StateFlow Compose
    val numbers: StateFlow<List<Whitelist>> = dao.getAllFlow()
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun insert(number: String) = viewModelScope.launch {
        dao.insert(Whitelist(phone_number = number))
    }

    fun update(id: Int, number: String) = viewModelScope.launch {
        dao.insert(Whitelist(id = id, phone_number = number))
    }

    fun delete(item: Whitelist) = viewModelScope.launch {
        dao.delete(item)
    }
}
