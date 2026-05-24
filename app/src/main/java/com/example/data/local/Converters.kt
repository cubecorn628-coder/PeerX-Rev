package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.MessageFrom
import com.example.data.model.MessageType

class Converters {
    @TypeConverter
    fun fromMessageFrom(value: MessageFrom): String {
        return value.name
    }

    @TypeConverter
    fun toMessageFrom(value: String): MessageFrom {
        return MessageFrom.valueOf(value)
    }

    @TypeConverter
    fun fromMessageType(value: MessageType): String {
        return value.name
    }

    @TypeConverter
    fun toMessageType(value: String): MessageType {
        return MessageType.valueOf(value)
    }
}
