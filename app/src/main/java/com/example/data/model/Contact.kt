package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val hash: String,
    val name: String,
    val photoBase64: String? = null,
    val lastSeen: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val unreadCount: Int = 0
)
