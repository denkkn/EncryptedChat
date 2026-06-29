package com.example.encryptedchat.storage

import android.content.Context
import com.example.encryptedchat.model.Conversation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 会话列表存储 — 首页卡片数据 */
class ConversationManager(private val ctx: Context) {
    private val f get() = File(ctx.filesDir, "conversations.json")

    fun load(): List<Conversation> {
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Conversation(o.getString("uid"), o.getString("name"),
                o.optString("msg", ""), o.optLong("time"), o.optInt("unread", 0))
        }.sortedByDescending { it.lastTime }
    }

    fun upsert(uid: String, name: String, msg: String, time: Long, unread: Int = 0) {
        val list = load().toMutableList()
        val idx = list.indexOfFirst { it.friendUid == uid }
        val conv = Conversation(uid, name, msg, time, unread)
        if (idx >= 0) list[idx] = conv else list.add(conv)
        save(list)
    }

    fun markRead(uid: String) {
        val list = load().map { if (it.friendUid == uid) it.copy(unread = 0) else it }
        save(list)
    }

    private fun save(list: List<Conversation>) {
        f.writeText(JSONArray().apply {
            list.forEach { c -> put(JSONObject().apply {
                put("uid", c.friendUid); put("name", c.friendName)
                put("msg", c.lastMsg); put("time", c.lastTime); put("unread", c.unread)
            }) }
        }.toString(2))
    }
}
