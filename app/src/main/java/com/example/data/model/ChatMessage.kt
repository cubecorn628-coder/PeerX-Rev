package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val msgId: String,
    val peerHash: String,          // References Contact's hash
    val from: MessageFrom,         // ME, THEM, SYSTEM
    val type: MessageType,         // TEXT, IMAGE, VIDEO, FILE, GEO, VOICE
    val content: String,           // markdown content, or Base64 dataURL for media, or empty for geo
    val senderName: String?,
    val timestamp: Long,
    val timeFormatted: String,     // e.g. "14:30" - saved pre-formatted
    val replyToId: String? = null,
    val replyToContent: String? = null,
    val replyToName: String? = null,
    val filename: String? = null,  // used for media/document files
    val fileSize: Long? = null,    // used for file size counting
    val geoLat: Double? = null,    // latitude for GEO share
    val geoLng: Double? = null     // longitude for GEO share
)

enum class MessageFrom { ME, THEM, SYSTEM }
enum class MessageType { TEXT, IMAGE, VIDEO, FILE, GEO, VOICE }
