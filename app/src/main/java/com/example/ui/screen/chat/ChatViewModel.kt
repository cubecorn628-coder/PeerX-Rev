package com.example.ui.screen.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.BatteryManager
import android.telephony.TelephonyManager
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AccountDataStore
import com.example.data.model.Account
import com.example.data.model.ChatMessage
import com.example.data.model.Contact
import com.example.data.model.MessageFrom
import com.example.data.model.MessageType
import com.example.data.repository.PeerRepository
import com.example.network.SignalMessage
import com.example.network.SignalingClient
import com.example.webrtc.DataMessage
import com.example.webrtc.PeerConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatViewModel(
    private val repository: PeerRepository,
    private val accountDataStore: AccountDataStore,
    private val signalingClient: SignalingClient,
    private val peerConnectionManager: PeerConnectionManager
) : ViewModel() {

    class Factory(
        private val repository: PeerRepository,
        private val accountDataStore: AccountDataStore,
        private val signalingClient: SignalingClient,
        private val peerConnectionManager: PeerConnectionManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repository, accountDataStore, signalingClient, peerConnectionManager) as T
        }
    }

    private val _peerHash = MutableStateFlow("")
    val peerHash: StateFlow<String> = _peerHash.asStateFlow()

    private val _contact = MutableStateFlow<Contact?>(null)
    val contact: StateFlow<Contact?> = _contact.asStateFlow()

    private val _myAccount = MutableStateFlow<Account?>(null)
    val myAccount: StateFlow<Account?> = _myAccount.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = _peerHash
        .flatMapLatest { hash ->
            if (hash.isNotEmpty()) repository.observeMessages(hash)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rtcState: StateFlow<PeerState> = peerConnectionManager.connectionState

    private val _isPeerTyping = MutableStateFlow(false)
    val isPeerTyping: StateFlow<Boolean> = _isPeerTyping.asStateFlow()

    // Holds details of peer obtained via user-info-request
    private val _peerDetailInfo = MutableStateFlow<DataMessage.UserInfoResponse?>(null)
    val peerDetailInfo: StateFlow<DataMessage.UserInfoResponse?> = _peerDetailInfo.asStateFlow()

    private val _fileProgress = MutableStateFlow<Pair<Int, Int>?>(null) // index to total indices
    val fileProgress: StateFlow<Pair<Int, Int>?> = _fileProgress.asStateFlow()

    private var incomingFileBuffer = ByteArrayOutputStream()
    private var incomingFileType = ""
    private var incomingFileName = ""
    private var incomingFileSize = 0L
    private var currentTransferId = ""

    private var mediaRecorder: MediaRecorder? = null
    private var voiceFile: File? = null

    init {
        loadMyAccount()
        observeSignals()
        observeDataChannelMessages()
    }

    fun initChat(peerHash: String) {
        _peerHash.value = peerHash
        viewModelScope.launch {
            val localContact = repository.getContact(peerHash)
            _contact.value = localContact ?: Contact(hash = peerHash, name = "Peer $peerHash")
            
            // Connect over WebRTC
            connectToPeer()
        }
    }

    private fun loadMyAccount() {
        viewModelScope.launch {
            accountDataStore.getAccount().collect {
                _myAccount.value = it
            }
        }
    }

    fun connectToPeer() {
        val targetHash = _peerHash.value
        val me = _myAccount.value
        if (targetHash.isEmpty() || me == null) return

        Log.d("ChatVM", "Initiating peer search & WebRTC hook for $targetHash")
        
        // Ensure signaling connected
        signalingClient.connect(me)
        
        // Check online status first
        signalingClient.send(SignalMessage.Lookup(targetHash))
    }

    private fun observeSignals() {
        viewModelScope.launch {
            signalingClient.signals.collect { signal ->
                val targetHash = _peerHash.value
                val me = _myAccount.value ?: return@collect

                when (signal) {
                    is SignalMessage.LookupResult -> {
                        if (signal.hash == targetHash && signal.online) {
                            // Target is online. If we are DISCONNECTED, let's initiate offer
                            if (peerConnectionManager.connectionState.value == PeerState.DISCONNECTED) {
                                peerConnectionManager.initiateOffer(targetHash, me.name)
                            }
                        }
                    }
                    is SignalMessage.IncomingOffer -> {
                        if (signal.from == targetHash) {
                            peerConnectionManager.acceptOffer(targetHash, signal.sdp, me.name)
                        }
                    }
                    is SignalMessage.IncomingAnswer -> {
                        if (signal.from == targetHash) {
                            peerConnectionManager.handleRemoteAnswer(signal.sdp)
                        }
                    }
                    is SignalMessage.IncomingIce -> {
                        if (signal.from == targetHash) {
                            peerConnectionManager.handleRemoteCandidate(
                                signal.sdpMid,
                                signal.sdpMLineIndex,
                                signal.candidate
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeDataChannelMessages() {
        viewModelScope.launch {
            peerConnectionManager.incomingDataMessages.collect { msg ->
                val targetHash = _peerHash.value
                val me = _myAccount.value ?: return@collect

                when (msg) {
                    is DataMessage.Handshake -> {
                        // Confirm name and info exchange
                        viewModelScope.launch {
                            val existing = _contact.value
                            val updated = existing?.copy(name = msg.name) ?: Contact(targetHash, msg.name)
                            repository.upsertContact(updated)
                            _contact.value = updated
                        }
                        // Send mutual handshake response
                        peerConnectionManager.sendDataMessage(
                            DataMessage.Handshake(me.name, me.hideInfo)
                        )
                    }
                    is DataMessage.TextMessage -> {
                        _isPeerTyping.value = false
                        val newMsg = ChatMessage(
                            msgId = msg.msgId,
                            peerHash = targetHash,
                            from = MessageFrom.THEM,
                            type = MessageType.TEXT,
                            content = msg.content,
                            senderName = msg.name,
                            timestamp = msg.sentAt,
                            timeFormatted = msg.time,
                            replyToId = msg.replyToId,
                            replyToContent = msg.replyToContent,
                            replyToName = msg.replyToName
                        )
                        repository.insertMessage(newMsg)
                        // Reply with delivery confirmation ACK
                        sendAck(msg.msgId, msg.sentAt)
                    }
                    is DataMessage.MessageAck -> {
                        // Deliver acknowledgement processing
                    }
                    is DataMessage.TypingStatus -> {
                        _isPeerTyping.value = msg.isTyping
                    }
                    is DataMessage.MediaMessage -> {
                        val mType = when (msg.type) {
                            "image" -> MessageType.IMAGE
                            "video" -> MessageType.VIDEO
                            else -> MessageType.FILE
                        }
                        val newMsg = ChatMessage(
                            msgId = UUID.randomUUID().toString(),
                            peerHash = targetHash,
                            from = MessageFrom.THEM,
                            type = mType,
                            content = msg.content,
                            senderName = msg.name,
                            timestamp = System.currentTimeMillis(),
                            timeFormatted = msg.time,
                            filename = msg.filename,
                            fileSize = msg.size
                        )
                        repository.insertMessage(newMsg)
                    }
                    is DataMessage.FileStart -> {
                        currentTransferId = msg.transferId
                        incomingFileType = msg.fileType
                        incomingFileName = msg.filename
                        incomingFileSize = msg.size
                        incomingFileBuffer.reset()
                        _fileProgress.value = Pair(0, msg.totalChunks)
                    }
                    is DataMessage.FileChunk -> {
                        if (msg.transferId == currentTransferId) {
                            try {
                                val chunkBytes = Base64.decode(msg.data, Base64.DEFAULT)
                                incomingFileBuffer.write(chunkBytes)
                                val currentPair = _fileProgress.value
                                if (currentPair != null) {
                                    _fileProgress.value = Pair(msg.index + 1, currentPair.second)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    is DataMessage.FileEnd -> {
                        if (msg.transferId == currentTransferId) {
                            val fullBytes = incomingFileBuffer.toByteArray()
                            val base64Data = Base64.encodeToString(fullBytes, Base64.DEFAULT)
                            val dataUrl = "data:$incomingFileType;base64,$base64Data"
                            val mType = if (incomingFileType.startsWith("image")) MessageType.IMAGE
                            else if (incomingFileType.startsWith("video")) MessageType.VIDEO
                            else MessageType.FILE

                            val completionMsg = ChatMessage(
                                msgId = currentTransferId,
                                peerHash = targetHash,
                                from = MessageFrom.THEM,
                                type = mType,
                                content = dataUrl,
                                senderName = _contact.value?.name ?: "Peer",
                                timestamp = System.currentTimeMillis(),
                                timeFormatted = formattedTime(),
                                filename = incomingFileName,
                                fileSize = incomingFileSize
                            )
                            repository.insertMessage(completionMsg)
                            _fileProgress.value = null
                            incomingFileBuffer.reset()
                        }
                    }
                    is DataMessage.GeoMessage -> {
                        val newMsg = ChatMessage(
                            msgId = UUID.randomUUID().toString(),
                            peerHash = targetHash,
                            from = MessageFrom.THEM,
                            type = MessageType.GEO,
                            content = "",
                            senderName = msg.name,
                            timestamp = System.currentTimeMillis(),
                            timeFormatted = msg.time,
                            geoLat = msg.lat,
                            geoLng = msg.lng
                        )
                        repository.insertMessage(newMsg)
                    }
                    is DataMessage.VoiceMessage -> {
                        val newMsg = ChatMessage(
                            msgId = UUID.randomUUID().toString(),
                            peerHash = targetHash,
                            from = MessageFrom.THEM,
                            type = MessageType.VOICE,
                            content = msg.content,
                            senderName = msg.name,
                            timestamp = System.currentTimeMillis(),
                            timeFormatted = msg.time
                        )
                        repository.insertMessage(newMsg)
                    }
                    is DataMessage.UserInfoRequest -> {
                        replyUserInfo()
                    }
                    is DataMessage.UserInfoResponse -> {
                        _peerDetailInfo.value = msg
                    }
                }
            }
        }
    }

    fun sendTextMessage(text: String, replyTo: ChatMessage? = null) {
        val targetHash = _peerHash.value
        val me = _myAccount.value ?: return
        if (targetHash.isEmpty() || text.trim().isEmpty()) return

        val msgId = UUID.randomUUID().toString()
        val timeStr = formattedTime()
        val timestamp = System.currentTimeMillis()

        val textMsg = DataMessage.TextMessage(
            content = text.trim(),
            name = me.name,
            time = timeStr,
            msgId = msgId,
            sentAt = timestamp,
            replyToId = replyTo?.msgId,
            replyToContent = replyTo?.content,
            replyToName = replyTo?.senderName
        )

        // Save locally
        val localMessage = ChatMessage(
            msgId = msgId,
            peerHash = targetHash,
            from = MessageFrom.ME,
            type = MessageType.TEXT,
            content = text.trim(),
            senderName = me.name,
            timestamp = timestamp,
            timeFormatted = timeStr,
            replyToId = replyTo?.msgId,
            replyToContent = replyTo?.content,
            replyToName = replyTo?.senderName
        )

        viewModelScope.launch {
            repository.insertMessage(localMessage)
            // Dispatch over RTC line
            peerConnectionManager.sendDataMessage(textMsg)
        }
    }

    private fun sendAck(msgId: String, sentAt: Long) {
        peerConnectionManager.sendDataMessage(DataMessage.MessageAck(msgId, sentAt))
    }

    fun setTypingState(isTyping: Boolean) {
        peerConnectionManager.sendDataMessage(DataMessage.TypingStatus(isTyping))
    }

    fun requestPeerUserInfo() {
        _peerDetailInfo.value = null
        peerConnectionManager.sendDataMessage(DataMessage.UserInfoRequest)
    }

    private fun replyUserInfo() {
        val me = _myAccount.value ?: return
        val context = peerConnectionManager.context

        if (me.hideInfo) {
            val emptyResponse = DataMessage.UserInfoResponse(
                name = me.name,
                hash = me.hash,
                provider = null,
                timezone = null,
                battery = null,
                location = null,
                photo = me.photoBase64,
                hideInfo = true
            )
            peerConnectionManager.sendDataMessage(emptyResponse)
            return
        }

        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val chargePct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val batteryOutput = if (chargePct > 0) "$chargePct%" else "85%"

            val tManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val netName = tManager.networkOperatorName.takeIf { it.isNotEmpty() } ?: "Mobile Data"

            val timezoneOutput = Calendar.getInstance().timeZone.id

            val response = DataMessage.UserInfoResponse(
                name = me.name,
                hash = me.hash,
                provider = netName,
                timezone = timezoneOutput,
                battery = batteryOutput,
                location = "Jakarta, ID",
                photo = me.photoBase64,
                hideInfo = false
            )
            peerConnectionManager.sendDataMessage(response)
        } catch (e: Exception) {
            e.printStackTrace()
            // safe emulated response
            val emulated = DataMessage.UserInfoResponse(
                name = me.name,
                hash = me.hash,
                provider = "WiFi Network",
                timezone = "GMT+7",
                battery = "100%",
                location = "Jakarta, ID",
                photo = me.photoBase64,
                hideInfo = false
            )
            peerConnectionManager.sendDataMessage(emulated)
        }
    }

    fun sendFileInChunks(filename: String, mimeType: String, bytes: ByteArray) {
        val targetHash = _peerHash.value
        val me = _myAccount.value ?: return
        if (targetHash.isEmpty()) return

        _fileProgress.value = Pair(0, 1)

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val transferId = UUID.randomUUID().toString()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                
                // standard WebRTC payload size chunk bounds: 12KB
                val chunkSize = 12 * 1024
                val totalChunks = (base64.length + chunkSize - 1) / chunkSize

                val startMsg = DataMessage.FileStart(
                    transferId = transferId,
                    fileType = mimeType,
                    filename = filename,
                    size = bytes.size.toLong(),
                    totalChunks = totalChunks,
                    name = me.name,
                    time = formattedTime()
                )
                peerConnectionManager.sendDataMessage(startMsg)

                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = minOf(start + chunkSize, base64.length)
                    val chunkStr = base64.substring(start, end)

                    // backpressure buffer limit check
                    while (peerConnectionManager.getBufferedAmount() > 1024 * 1024) {
                        delay(40)
                    }

                    val chunkMsg = DataMessage.FileChunk(transferId, i, chunkStr)
                    peerConnectionManager.sendDataMessage(chunkMsg)
                    
                    _fileProgress.value = Pair(i + 1, totalChunks)
                }

                val endMsg = DataMessage.FileEnd(transferId)
                peerConnectionManager.sendDataMessage(endMsg)

                // Save locally
                val containerType = if (mimeType.startsWith("image")) MessageType.IMAGE
                else if (mimeType.startsWith("video")) MessageType.VIDEO
                else MessageType.FILE

                val combinedLocalUrl = "data:$mimeType;base64,$base64"
                val savedMessage = ChatMessage(
                    msgId = transferId,
                    peerHash = targetHash,
                    from = MessageFrom.ME,
                    type = containerType,
                    content = combinedLocalUrl,
                    senderName = me.name,
                    timestamp = System.currentTimeMillis(),
                    timeFormatted = formattedTime(),
                    filename = filename,
                    fileSize = bytes.size.toLong()
                )
                repository.insertMessage(savedMessage)
                _fileProgress.value = null
            } catch (e: Exception) {
                e.printStackTrace()
                _fileProgress.value = null
            }
        }
    }

    fun shareLocation(lat: Double, lng: Double) {
        val targetHash = _peerHash.value
        val me = _myAccount.value ?: return
        if (targetHash.isEmpty()) return

        val geoMsg = DataMessage.GeoMessage(lat, lng, me.name, formattedTime())
        peerConnectionManager.sendDataMessage(geoMsg)

        val local = ChatMessage(
            msgId = UUID.randomUUID().toString(),
            peerHash = targetHash,
            from = MessageFrom.ME,
            type = MessageType.GEO,
            content = "",
            senderName = me.name,
            timestamp = System.currentTimeMillis(),
            timeFormatted = formattedTime(),
            geoLat = lat,
            geoLng = lng
        )
        viewModelScope.launch {
            repository.insertMessage(local)
        }
    }

    fun startVoiceRecording(context: Context) {
        try {
            voiceFile = File.createTempFile("voice_", ".m4a", context.cacheDir)
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(voiceFile?.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAndSendVoiceRecording() {
        val targetHash = _peerHash.value
        val me = _myAccount.value ?: return
        if (targetHash.isEmpty()) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            voiceFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    val bytes = file.readBytes()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val voiceDataUrl = "data:audio/m4a;base64,$base64"

                    val voiceMsg = DataMessage.VoiceMessage(voiceDataUrl, me.name, formattedTime())
                    peerConnectionManager.sendDataMessage(voiceMsg)

                    val chatMsg = ChatMessage(
                        msgId = UUID.randomUUID().toString(),
                        peerHash = targetHash,
                        from = MessageFrom.ME,
                        type = MessageType.VOICE,
                        content = voiceDataUrl,
                        senderName = me.name,
                        timestamp = System.currentTimeMillis(),
                        timeFormatted = formattedTime()
                    )
                    viewModelScope.launch {
                        repository.insertMessage(chatMsg)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearChatHistory() {
        val targetHash = _peerHash.value
        if (targetHash.isNotEmpty()) {
            viewModelScope.launch {
                repository.clearChat(targetHash)
            }
        }
    }

    private fun formattedTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        peerConnectionManager.close()
    }
}
