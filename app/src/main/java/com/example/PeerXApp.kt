package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.local.AccountDataStore
import com.example.data.local.AppDatabase
import com.example.data.repository.PeerRepository
import com.example.network.SignalingClient
import com.example.webrtc.PeerConnectionManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PeerXApp : Application() {
    lateinit var database: AppDatabase
    lateinit var accountDataStore: AccountDataStore
    lateinit var peerRepository: PeerRepository
    lateinit var signalingClient: SignalingClient
    lateinit var peerConnectionManager: PeerConnectionManager

    override fun onCreate() {
        super.onCreate()
        
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "peerx_db"
        )
        .fallbackToDestructiveMigration()
        .build()

        accountDataStore = AccountDataStore(this)
        
        peerRepository = PeerRepository(
            database.contactDao(),
            database.chatMessageDao()
        )

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        signalingClient = SignalingClient(okHttpClient)

        peerConnectionManager = PeerConnectionManager(this, signalingClient)
    }
}
