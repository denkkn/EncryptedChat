package com.example.encryptedchat.ui

import android.app.Application
import com.example.encryptedchat.crypto.CryptoManager
import com.example.encryptedchat.crypto.KeyManager
import com.example.encryptedchat.network.ApiClient
import com.example.encryptedchat.storage.ConversationManager
import com.example.encryptedchat.storage.FriendsManager

class ChatApp : Application() {
    lateinit var crypto: CryptoManager
    lateinit var friendsManager: FriendsManager
    lateinit var apiClient: ApiClient
    lateinit var convManager: ConversationManager
    lateinit var keyManager: KeyManager

    override fun onCreate() {
        super.onCreate()
        keyManager = KeyManager(this)
        crypto = CryptoManager(keyManager)
        friendsManager = FriendsManager(this)
        convManager = ConversationManager(this)
        apiClient = ApiClient(crypto, friendsManager)
    }
}
