package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.ChatMessage
import com.example.data.model.Contact

@Database(entities = [Contact::class, ChatMessage::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun chatMessageDao(): ChatMessageDao
}
