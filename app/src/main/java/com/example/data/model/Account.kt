package com.example.data.model

data class Account(
    val hash: String,          // 8 chars, e.g. "A2B3C4D5"
    val name: String,
    val photoBase64: String?,  // JPEG base64, null if empty
    val hideInfo: Boolean = false,
    val isDarkTheme: Boolean = true
)
