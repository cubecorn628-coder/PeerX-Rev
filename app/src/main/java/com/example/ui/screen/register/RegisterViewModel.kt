package com.example.ui.screen.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AccountDataStore
import com.example.data.model.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom

class RegisterViewModel(
    private val accountDataStore: AccountDataStore
) : ViewModel() {

    class Factory(private val accountDataStore: AccountDataStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RegisterViewModel(accountDataStore) as T
        }
    }

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Loading)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    init {
        checkAccount()
    }

    private fun checkAccount() {
        viewModelScope.launch {
            accountDataStore.getAccount().collect { account ->
                if (account != null) {
                    _uiState.value = RegisterUiState.AlreadyRegistered(account)
                } else {
                    _uiState.value = RegisterUiState.NotRegistered
                }
            }
        }
    }

    fun registerUser(name: String, photoBase64: String?) {
        viewModelScope.launch {
            val hash = generateHash()
            val account = Account(
                hash = hash,
                name = name,
                photoBase64 = photoBase64,
                hideInfo = false,
                isDarkTheme = true
            )
            accountDataStore.saveAccount(account)
            _uiState.value = RegisterUiState.AlreadyRegistered(account)
        }
    }

    private fun generateHash(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.map { chars[it.toInt().and(0xFF) % chars.length] }.joinToString("")
    }
}

sealed class RegisterUiState {
    object Loading : RegisterUiState()
    object NotRegistered : RegisterUiState()
    data class AlreadyRegistered(val account: Account) : RegisterUiState()
}
