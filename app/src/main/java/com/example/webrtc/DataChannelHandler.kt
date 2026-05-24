package com.example.webrtc

import org.json.JSONObject

sealed class DataMessage {
    abstract fun toJsonString(): String

    data class Handshake(val name: String, val hideInfo: Boolean) : DataMessage() {
        override fun toJsonString(): String {
            val obj = JSONObject().apply {
                put("type", "handshake")
                put("name", name)
                put("hideInfo", hideInfo)
            }
            return obj.toString()
        }
    }

    data class TextMessage(
        val content: String,
        val name: String,
        val time: String,
        val msgId: String,
        val sentAt: Long,
        val replyToId: String? = null,
        val replyToContent: String? = null,
        val replyToName: String? = null
    ) : DataMessage() {
        override fun toJsonString(): String {
            val obj = JSONObject().apply {
                put("type", "msg")
                put("content", content)
                put("name", name)
                put("time", time)
                put("msgId", msgId)
                put("sentAt", sentAt)
                if (replyToId != null) {
                    val repObj = JSONObject().apply {
                        put("id", replyToId)
                        put("content", replyToContent)
                        put("name", replyToName)
                    }
                    put("replyTo", repObj)
                }
            }
            return obj.toString()
        }
    }

    data class MessageAck(val msgId: String, val sentAt: Long) : DataMessage() {
        override fun toJsonString(): String = "{\"type\":\"msg-ack\",\"msgId\":\"$msgId\",\"sentAt\":$sentAt}"
    }

    data class TypingStatus(val isTyping: Boolean) : DataMessage() {
        override fun toJsonString(): String = "{\"type\":\"typing\",\"typing\":$isTyping}"
    }

    data class MediaMessage(
        val type: String, // "image", "video", "file"
        val content: String, // dataURL
        val name: String,
        val time: String,
        val filename: String? = null,
        val size: Long? = null
    ) : DataMessage() {
        override fun toJsonString(): String {
            val obj = JSONObject().apply {
                put("type", type)
                put("content", content)
                put("name", name)
                put("time", time)
                if (filename != null) put("filename", filename)
                if (size != null) put("size", size)
            }
            return obj.toString()
        }
    }

    data class FileStart(
        val transferId: String,
        val fileType: String,
        val filename: String,
        val size: Long,
        val totalChunks: Int,
        val name: String,
        val time: String
    ) : DataMessage() {
        override fun toJsonString(): String {
            val obj = JSONObject().apply {
                put("type", "file-start")
                put("transferId", transferId)
                put("fileType", fileType)
                put("filename", filename)
                put("size", size)
                put("totalChunks", totalChunks)
                put("name", name)
                put("time", time)
            }
            return obj.toString()
        }
    }

    data class FileChunk(
        val transferId: String,
        val index: Int,
        val data: String
    ) : DataMessage() {
        override fun toJsonString(): String {
            val obj = JSONObject().apply {
                put("type", "file-chunk")
                put("transferId", transferId)
                put("index", index)
                put("data", data)
            }
            return obj.toString()
        }
    }

    data class FileEnd(val transferId: String) : DataMessage() {
        override fun toJsonString(): String = "{\"type\":\"file-end\",\"transferId\":\"$transferId\"}"
    }

    data class GeoMessage(val lat: Double, val lng: Double, val name: String, val time: String) : DataMessage() {
        override fun toJsonString(): String {
            val obj = JSONObject().apply {
                put("type", "geo")
                put("lat", lat)
                put("lng", lng)
                put("name", name)
                put("time", time)
            }
            return obj.toString()
        }
    }

    data class VoiceMessage(val content: String, val name: String, val time: String) : DataMessage() {
        override fun toJsonString(): String {
            val obj = JSONObject().apply {
                put("type", "voice")
                put("content", content)
                put("name", name)
                put("time", time)
            }
            return obj.toString()
        }
    }

    object UserInfoRequest : DataMessage() {
        override fun toJsonString(): String = "{\"type\":\"user-info-request\"}"
    }

    data class UserInfoResponse(
        val name: String,
        val hash: String,
        val provider: String?,
        val timezone: String?,
        val battery: String?,
        val location: String?,
        val photo: String?,
        val hideInfo: Boolean = false
    ) : DataMessage() {
        override fun toJsonString(): String {
            val obj = JSONObject().apply {
                put("type", "user-info-response")
                put("name", name)
                put("hash", hash)
                put("provider", provider ?: "")
                put("timezone", timezone ?: "")
                put("battery", battery ?: "")
                put("location", location ?: "")
                put("photo", photo ?: "")
                put("hideInfo", hideInfo)
            }
            return obj.toString()
        }
    }

    companion object {
        fun parse(json: String): DataMessage? {
            try {
                val obj = JSONObject(json)
                return when (obj.optString("type")) {
                    "handshake" -> Handshake(obj.getString("name"), obj.optBoolean("hideInfo", false))
                    "msg" -> {
                        val reply = obj.optJSONObject("replyTo")
                        TextMessage(
                            obj.getString("content"),
                            obj.getString("name"),
                            obj.getString("time"),
                            obj.getString("msgId"),
                            obj.optLong("sentAt", System.currentTimeMillis()),
                            reply?.optString("id"),
                            reply?.optString("content"),
                            reply?.optString("name")
                        )
                    }
                    "msg-ack" -> MessageAck(obj.getString("msgId"), obj.getLong("sentAt"))
                    "typing" -> TypingStatus(obj.getBoolean("typing"))
                    "image", "video", "file" -> {
                        MediaMessage(
                            obj.getString("type"),
                            obj.getString("content"),
                            obj.getString("name"),
                            obj.getString("time"),
                            obj.optString("filename", null),
                            if (obj.has("size")) obj.getLong("size") else null
                        )
                    }
                    "file-start" -> {
                        FileStart(
                            obj.getString("transferId"),
                            obj.getString("fileType"),
                            obj.getString("filename"),
                            obj.getLong("size"),
                            obj.getInt("totalChunks"),
                            obj.getString("name"),
                            obj.getString("time")
                        )
                    }
                    "file-chunk" -> {
                        FileChunk(
                            obj.getString("transferId"),
                            obj.getInt("index"),
                            obj.getString("data")
                        )
                    }
                    "file-end" -> {
                        FileEnd(obj.getString("transferId"))
                    }
                    "geo" -> {
                        GeoMessage(
                            obj.getDouble("lat"),
                            obj.getDouble("lng"),
                            obj.getString("name"),
                            obj.getString("time")
                        )
                    }
                    "voice" -> {
                        VoiceMessage(
                            obj.getString("content"),
                            obj.getString("name"),
                            obj.getString("time")
                        )
                    }
                    "user-info-request" -> UserInfoRequest
                    "user-info-response" -> {
                        val provider = obj.optString("provider").takeIf { it.isNotEmpty() }
                        val timezone = obj.optString("timezone").takeIf { it.isNotEmpty() }
                        val battery = obj.optString("battery").takeIf { it.isNotEmpty() }
                        val loc = obj.optString("location").takeIf { it.isNotEmpty() }
                        val photo = obj.optString("photo").takeIf { it.isNotEmpty() }
                        UserInfoResponse(
                            obj.getString("name"),
                            obj.getString("hash"),
                            provider,
                            timezone,
                            battery,
                            loc,
                            photo,
                            obj.optBoolean("hideInfo", false)
                        )
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
