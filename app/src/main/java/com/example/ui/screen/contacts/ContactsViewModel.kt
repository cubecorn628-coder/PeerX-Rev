package com.example.ui.screen.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AccountDataStore
import com.example.data.model.Account
import com.example.data.model.Contact
import com.example.data.repository.PeerRepository
import com.example.network.SignalMessage
import com.example.network.SignalingClient
import com.example.network.WsState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ContactsViewModel(
    private val repository: PeerRepository,
    private val accountDataStore: AccountDataStore,
    private val signalingClient: SignalingClient
) : ViewModel() {

    class Factory(
        private val repository: PeerRepository,
        private val accountDataStore: AccountDataStore,
        private val signalingClient: SignalingClient
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ContactsViewModel(repository, accountDataStore, signalingClient) as T
        }
    }

    private val _userAccount = MutableStateFlow<Account?>(null)
    val userAccount: StateFlow<Account?> = _userAccount.asStateFlow()

    val contacts: StateFlow<List<Contact>> = repository.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wsState: StateFlow<WsState> = signalingClient.connectionState

    private val _lookupResult = MutableSharedFlow<SignalMessage.LookupResult>(extraBufferCapacity = 16)
    val lookupResult: SharedFlow<SignalMessage.LookupResult> = _lookupResult.asSharedFlow()

    init {
        loadAccount()
        observeSignals()
    }

    private fun loadAccount() {
        viewModelScope.launch {
            accountDataStore.getAccount().collect { account ->
                _userAccount.value = account
                if (account != null) {
                    signalingClient.connect(account)
                }
            }
        }
    }

    private fun observeSignals() {
        viewModelScope.launch {
            signalingClient.signals.collect { signal ->
                when (signal) {
                    is SignalMessage.LookupResult -> {
                        _lookupResult.emit(signal)
                        // Auto-add contact if successfully looked up and online
                        if (signal.online) {
                            val existing = repository.getContact(signal.hash)
                            if (existing == null) {
                                repository.upsertContact(
                                    Contact(
                                        hash = signal.hash,
                                        name = signal.name ?: "Peer ${signal.hash}",
                                        lastSeen = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                    is SignalMessage.PeerOffline -> {
                        val contact = repository.getContact(signal.hash)
                        if (contact != null) {
                            // Can update state / offline indicators
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun addContact(hash: String, name: String) {
        viewModelScope.launch {
            repository.upsertContact(
                Contact(
                    hash = hash.trim().uppercase(),
                    name = name.trim(),
                    lastSeen = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteContact(hash: String) {
        viewModelScope.launch {
            repository.deleteContact(hash)
        }
    }

    fun lookupPeer(hash: String) {
        signalingClient.send(SignalMessage.Lookup(hash.trim().uppercase()))
    }

    override fun onCleared() {
        super.onCleared()
    }
}
