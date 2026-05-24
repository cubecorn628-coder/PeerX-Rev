package com.example.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AccountDataStore
import com.example.data.model.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val accountDataStore: AccountDataStore
) : ViewModel() {

    class Factory(private val accountDataStore: AccountDataStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(accountDataStore) as T
        }
    }

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    init {
        loadAccount()
    }

    private fun loadAccount() {
        viewModelScope.launch {
            accountDataStore.getAccount().collect {
                _account.value = it
            }
        }
    }

    fun updateName(name: String) {
        viewModelScope.launch {
            accountDataStore.updateName(name)
        }
    }

    fun updatePhoto(base64: String?) {
        viewModelScope.launch {
            accountDataStore.updatePhoto(base64)
        }
    }

    fun updateHideInfo(hide: Boolean) {
        viewModelScope.launch {
            accountDataStore.updateHideInfo(hide)
        }
    }

    fun updateTheme(isDark: Boolean) {
        viewModelScope.launch {
            accountDataStore.updateTheme(isDark)
        }
    }

    fun clearUserData() {
        viewModelScope.launch {
            accountDataStore.clear()
            _account.value = null
        }
    }
}
