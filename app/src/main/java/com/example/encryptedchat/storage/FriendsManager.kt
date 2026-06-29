package com.example.encryptedchat.storage

import android.content.Context
import com.example.encryptedchat.model.Friend
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** 好友本地存储 — friends.json, 格式与Python完全一致 */
class FriendsManager(private val context: Context) {

    private val file get() = File(context.filesDir, "friends.json")

    fun loadFriends(): List<Friend> {
        if (!file.exists()) return emptyList()
        val json = JSONObject(file.readText())
        return json.keys().asSequence().map { name ->
            val info = json.getJSONObject(name)
            Friend(name, info.getString("uid"), info.getString("pub_base64"))
        }.toList()
    }

    fun saveFriends(list: List<Friend>) {
        file.writeText(JSONObject().apply {
            list.forEach { f -> put(f.name, JSONObject().apply {
                put("uid", f.uid); put("pub_base64", f.pubBase64)
            }) }
        }.toString(2))
    }

    fun addFriend(name: String, pubBase64: String): String {
        val uid = MessageDigest.getInstance("SHA-256")
            .digest(pubBase64.toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it) }
        val list = loadFriends().toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        if (idx >= 0) list[idx] = Friend(name, uid, pubBase64)
        else list.add(Friend(name, uid, pubBase64))
        saveFriends(list)
        return uid
    }

    fun deleteFriend(name: String) {
        saveFriends(loadFriends().filter { it.name != name })
    }

    fun findFriendByUid(uid: String) = loadFriends().find { it.uid == uid }
    fun findFriendByName(name: String) = loadFriends().find { it.name == name }
}
