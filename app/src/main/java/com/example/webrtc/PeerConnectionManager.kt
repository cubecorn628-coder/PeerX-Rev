package com.example.webrtc

import android.content.Context
import android.util.Log
import com.example.network.SignalMessage
import com.example.network.SignalingClient
import com.example.ui.screen.chat.PeerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.*
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeerConnectionManager @Inject constructor(
    val context: Context,
    private val signalingClient: SignalingClient
) {
    companion object {
        private const val TAG = "PeerConnectionManager"
    }

    private val _connectionState = MutableStateFlow(PeerState.DISCONNECTED)
    val connectionState: StateFlow<PeerState> = _connectionState

    private val _incomingDataMessages = MutableSharedFlow<DataMessage>(extraBufferCapacity = 64)
    val incomingDataMessages: SharedFlow<DataMessage> = _incomingDataMessages

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var targetPeerHash: String? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    init {
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .createInitializationOptions()
            )
            val options = PeerConnectionFactory.Options()
            factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()
            Log.d(TAG, "PeerConnectionFactory Initialized Successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PeerConnectionFactory", e)
        }
    }

    fun initiateOffer(targetHash: String, targetName: String) {
        targetPeerHash = targetHash
        _connectionState.value = PeerState.CONNECTING
        createPeerConnection(targetHash)

        val dcInit = DataChannel.Init().apply {
            ordered = true
        }
        dataChannel = peerConnection?.createDataChannel("chat", dcInit)
        dataChannel?.let { setupDataChannel(it) }

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.d(TAG, "Offer Created Successfully")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        // Send Offer to Signaling Server
                        signalingClient.send(
                            SignalMessage.Offer(targetHash, desc.description, targetName)
                        )
                    }
                    override fun onCreateFailure(reason: String?) {
                        Log.e(TAG, "Sdp SetLocalDescription Failed: $reason")
                    }
                    override fun onSetFailure(reason: String?) {
                        Log.e(TAG, "Sdp SetLocalDescription Failed: $reason")
                    }
                }, desc)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(reason: String?) {
                Log.e(TAG, "Sdp CreateOffer Failed: $reason")
            }
            override fun onSetFailure(reason: String?) {
                Log.e(TAG, "Sdp CreateOffer Failed: $reason")
            }
        }, constraints)
    }

    fun acceptOffer(fromHash: String, remoteSdpString: String, targetName: String) {
        targetPeerHash = fromHash
        _connectionState.value = PeerState.CONNECTING
        createPeerConnection(fromHash)

        val remoteDescription = SessionDescription(SessionDescription.Type.OFFER, remoteSdpString)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "SetRemoteDescription (Offer) Successful")
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                }
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                signalingClient.send(
                                    SignalMessage.Answer(fromHash, desc.description, targetName)
                                )
                            }
                            override fun onCreateFailure(reason: String?) {}
                            override fun onSetFailure(reason: String?) {}
                        }, desc)
                    }

                    override fun onSetSuccess() {}
                    override fun onCreateFailure(reason: String?) {}
                    override fun onSetFailure(reason: String?) {}
                }, constraints)
            }

            override fun onCreateFailure(reason: String?) {}
            override fun onSetFailure(reason: String?) {
                Log.e(TAG, "SetRemoteDescription failed: $reason")
            }
        }, remoteDescription)
    }

    fun handleRemoteAnswer(remoteSdpString: String) {
        val remoteDescription = SessionDescription(SessionDescription.Type.ANSWER, remoteSdpString)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "SetRemoteDescription (Answer) Successful")
            }
            override fun onCreateFailure(reason: String?) {}
            override fun onSetFailure(reason: String?) {
                Log.e(TAG, "SetRemoteDescription failed: $reason")
            }
        }, remoteDescription)
    }

    fun handleRemoteCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        peerConnection?.addIceCandidate(candidate)
    }

    private fun createPeerConnection(targetHash: String) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "IceConnectionState Change: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        _connectionState.value = PeerState.CONNECTED
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        _connectionState.value = PeerState.DISCONNECTED
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                // Trickle candidate to peer via signaling server
                signalingClient.send(
                    SignalMessage.IceCandidate(targetHash, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                )
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel) {
                Log.d(TAG, "Receiving Remote DataChannel")
                dataChannel = channel
                setupDataChannel(channel)
            }

            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })
    }

    private fun setupDataChannel(channel: DataChannel) {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {
                Log.d(TAG, "DataChannel State Change: ${channel.state()}")
                if (channel.state() == DataChannel.State.OPEN) {
                    _connectionState.value = PeerState.CONNECTED
                } else if (channel.state() == DataChannel.State.CLOSING || channel.state() == DataChannel.State.CLOSED) {
                    _connectionState.value = PeerState.DISCONNECTED
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                val str = String(bytes, Charsets.UTF_8)
                Log.d(TAG, "DC Rx: $str")
                val msg = DataMessage.parse(str)
                if (msg != null) {
                    scope.launch {
                        _incomingDataMessages.emit(msg)
                    }
                }
            }
        })
    }

    fun sendDataMessage(message: DataMessage) {
        sendRawData(message.toJsonString())
    }

    fun sendRawData(json: String) {
        val dc = dataChannel
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            val bytes = json.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.wrap(bytes)
            dc.send(DataChannel.Buffer(buffer, false))
        } else {
            Log.e(TAG, "Snd: DataChannel not open!")
        }
    }

    fun getBufferedAmount(): Long {
        return dataChannel?.bufferedAmount() ?: 0L
    }

    fun close() {
        dataChannel?.unregisterObserver()
        dataChannel?.close()
        dataChannel = null
        peerConnection?.close()
        peerConnection = null
        targetPeerHash = null
        _connectionState.value = PeerState.DISCONNECTED
    }
}
