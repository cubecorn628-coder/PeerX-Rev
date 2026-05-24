package com.example.data.repository

import com.example.data.local.ContactDao
import com.example.data.local.ChatMessageDao
import com.example.data.model.Contact
import com.example.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeerRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val chatMessageDao: ChatMessageDao
) {
    val contacts: Flow<List<Contact>> = contactDao.observeContacts()

    suspend fun getContact(hash: String): Contact? = contactDao.getContact(hash)

    suspend fun upsertContact(contact: Contact) {
        contactDao.upsertContact(contact)
    }

    suspend fun deleteContact(hash: String) {
        contactDao.deleteContact(hash)
    }

    fun observeMessages(hash: String): Flow<List<ChatMessage>> = chatMessageDao.observeMessages(hash)

    suspend fun insertMessage(message: ChatMessage) {
        chatMessageDao.insertMessage(message)
    }

    suspend fun clearChat(hash: String) {
        chatMessageDao.clearChat(hash)
    }
}
