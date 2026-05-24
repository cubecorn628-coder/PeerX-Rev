package com.example.network

import android.util.Log
import com.example.data.model.Account
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalingClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "SignalingClient"
        const val WORKER_URL = "wss://nexlink.dako-sh.workers.dev"
        const val PING_INTERVAL = 20_000L
        const val RECONNECT_DELAY = 3_000L
    }

    private val _connectionState = MutableStateFlow(WsState.DISCONNECTED)
    val connectionState: StateFlow<WsState> = _connectionState

    private val _signals = MutableSharedFlow<SignalMessage>(extraBufferCapacity = 64)
    val signals: SharedFlow<SignalMessage> = _signals

    private var webSocket: WebSocket? = null
    private var currentAccount: Account? = null
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var isClosedManually = false

    fun connect(account: Account) {
        currentAccount = account
        isClosedManually = false
        reconnectJob?.cancel()

        if (_connectionState.value == WsState.CONNECTED || _connectionState.value == WsState.CONNECTING) {
            return
        }

        _connectionState.value = WsState.CONNECTING
        Log.d(TAG, "Connecting to WebSocket: $WORKER_URL")

        val request = Request.Builder().url(WORKER_URL).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Opened")
                _connectionState.value = WsState.CONNECTED
                isClosedManually = false
                
                // Register
                webSocket.send(SignalMessage.Register(account.hash, account.name).toJsonString())
                
                startPingLoop()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Rx: $text")
                val msg = SignalMessage.parse(text)
                if (msg != null) {
                    scope.launch {
                        _signals.emit(msg)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $reason")
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}")
                handleDisconnect()
            }
        })
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL)
                if (_connectionState.value == WsState.CONNECTED) {
                    Log.d(TAG, "Tx: Ping")
                    send(SignalMessage.Ping)
                }
            }
        }
    }

    private fun handleDisconnect() {
        _connectionState.value = WsState.DISCONNECTED
        pingJob?.cancel()
        if (!isClosedManually && currentAccount != null) {
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(RECONNECT_DELAY)
                Log.d(TAG, "Reconnecting...")
                currentAccount?.let { connect(it) }
            }
        }
    }

    fun send(message: SignalMessage) {
        val json = message.toJsonString()
        Log.d(TAG, "Tx: $json")
        webSocket?.send(json)
    }

    fun disconnect() {
        isClosedManually = true
        reconnectJob?.cancel()
        pingJob?.cancel()
        webSocket?.close(1000, "Closed by User")
        webSocket = null
        _connectionState.value = WsState.DISCONNECTED
    }
}
