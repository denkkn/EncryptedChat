package com.example.encryptedchat.ui

import android.app.Application
import com.example.encryptedchat.crypto.CryptoManager
import com.example.encryptedchat.crypto.KeyManager
import com.example.encryptedchat.network.ApiClient
import com.example.encryptedchat.storage.FriendsManager

class ChatApp : Application() {
    lateinit var cryptoManager: CryptoManager
    lateinit var friendsManager: FriendsManager
    lateinit var apiClient: ApiClient

    override fun onCreate() {
        super.onCreate()
        val km = KeyManager(this)
        if (!km.hasKeyPair()) km.generateKeyPair()
        cryptoManager = CryptoManager(km)
        friendsManager = FriendsManager(this)
        apiClient = ApiClient(cryptoManager, friendsManager)
    }
}
