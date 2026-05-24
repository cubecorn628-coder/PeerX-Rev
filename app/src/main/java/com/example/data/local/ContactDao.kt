package com.example.data.local

import androidx.room.*
import com.example.data.model.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY lastSeen DESC")
    fun observeContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE hash = :hash LIMIT 1")
    suspend fun getContact(hash: String): Contact?

    @Upsert
    suspend fun upsertContact(contact: Contact)

    @Query("DELETE FROM contacts WHERE hash = :hash")
    suspend fun deleteContact(hash: String)
}
