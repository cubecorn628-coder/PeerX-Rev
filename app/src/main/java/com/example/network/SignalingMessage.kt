package com.example.network

sealed class SignalMessage {
    // Outgoing or general message base
    abstract fun toJsonString(): String

    data class Register(val hash: String, val name: String) : SignalMessage() {
        override fun toJsonString(): String = "{\"type\":\"register\",\"hash\":\"$hash\",\"name\":\"$name\"}"
    }

    object Ping : SignalMessage() {
        override fun toJsonString(): String = "{\"type\":\"ping\"}"
    }

    data class Lookup(val hash: String) : SignalMessage() {
        override fun toJsonString(): String = "{\"type\":\"lookup\",\"hash\":\"$hash\"}"
    }

    data class Offer(val to: String, val sdp: String, val name: String) : SignalMessage() {
        override fun toJsonString(): String {
            val escapedSdp = sdp.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            return "{\"type\":\"offer\",\"to\":\"$to\",\"sdp\":{\"type\":\"offer\",\"sdp\":\"$escapedSdp\"},\"name\":\"$name\"}"
        }
    }

    data class Answer(val to: String, val sdp: String, val name: String) : SignalMessage() {
        override fun toJsonString(): String {
            val escapedSdp = sdp.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            return "{\"type\":\"answer\",\"to\":\"$to\",\"sdp\":{\"type\":\"answer\",\"sdp\":\"$escapedSdp\"},\"name\":\"$name\"}"
        }
    }

    data class IceCandidate(val to: String, val sdpMid: String, val sdpMLineIndex: Int, val candidate: String) : SignalMessage() {
        override fun toJsonString(): String {
            val escapedCand = candidate.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            return "{\"type\":\"ice-candidate\",\"to\":\"$to\",\"candidate\":{\"candidate\":\"$escapedCand\",\"sdpMid\":\"$sdpMid\",\"sdpMLineIndex\":$sdpMLineIndex}}"
        }
    }

    // Incoming messages parsed from Server
    object Registered : SignalMessage() {
        override fun toJsonString(): String = "{\"type\":\"registered\"}"
    }

    object Pong : SignalMessage() {
        override fun toJsonString(): String = "{\"type\":\"pong\"}"
    }

    data class LookupResult(val hash: String, val online: Boolean, val name: String?) : SignalMessage() {
        override fun toJsonString(): String = "{\"type\":\"lookup-result\",\"hash\":\"$hash\",\"online\":$online,\"name\":\"$name\"}"
    }

    data class IncomingOffer(val from: String, val name: String, val sdp: String) : SignalMessage() {
        override fun toJsonString(): String = "{\"type\":\"offer\",\"from\":\"$from\",\"name\":\"$name\",\"sdp\":\"$sdp\"}"
    }

    data class IncomingAnswer(val from: String, val sdp: String) : SignalMessage() {
        override fun toJsonString(): String = "{\"type\":\"answer\",\"from\":\"$from\",\"sdp\":\"$sdp\"}"
    }

    data class IncomingIce(val from: String, val sdpMid: String, val sdpMLineIndex: Int, val candidate: String) : SignalMessage() {
        override fun toJsonString(): String = "{\"type\":\"ice-candidate\",\"from\":\"$from\",\"sdpMid\":\"$sdpMid\",\"sdpMLineIndex\":$sdpMLineIndex,\"candidate\":\"$candidate\"}"
    }

    data class PeerOffline(val hash: String) : SignalMessage() {
        override fun toJsonString(): String = "{\"type\":\"peer-offline\",\"hash\":\"$hash\"}"
    }

    companion object {
        fun parse(json: String): SignalMessage? {
            try {
                val obj = org.json.JSONObject(json)
                return when (obj.optString("type")) {
                    "registered" -> Registered
                    "pong" -> Pong
                    "lookup-result" -> {
                        val hash = obj.getString("hash")
                        val online = obj.getBoolean("online")
                        val name = obj.optString("name", null)
                        LookupResult(hash, online, name)
                    }
                    "offer" -> {
                        val from = obj.getString("from")
                        val name = obj.optString("name", from)
                        val sdpObj = obj.getJSONObject("sdp")
                        val sdpString = sdpObj.getString("sdp")
                        IncomingOffer(from, name, sdpString)
                    }
                    "answer" -> {
                        val from = obj.getString("from")
                        val sdpObj = obj.getJSONObject("sdp")
                        val sdpString = sdpObj.getString("sdp")
                        IncomingAnswer(from, sdpString)
                    }
                    "ice-candidate" -> {
                        val from = obj.getString("from")
                        val candObj = obj.getJSONObject("candidate")
                        val candidate = candObj.getString("candidate")
                        val sdpMid = candObj.getString("sdpMid")
                        val sdpMLineIndex = candObj.getInt("sdpMLineIndex")
                        IncomingIce(from, sdpMid, sdpMLineIndex, candidate)
                    }
                    "peer-offline" -> {
                        PeerOffline(obj.getString("hash"))
                    }
                    else -> null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }
}

enum class WsState { CONNECTING, CONNECTED, DISCONNECTED }
