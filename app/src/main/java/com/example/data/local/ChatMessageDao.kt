package com.example.data.local

import androidx.room.*
import com.example.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE peerHash = :hash ORDER BY timestamp ASC LIMIT 500")
    fun observeMessages(hash: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE peerHash = :hash")
    suspend fun clearChat(hash: String)
}
