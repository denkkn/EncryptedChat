package com.example.encryptedchat.model

/** 好友 */
data class Friend(val name: String, val uid: String, val pubBase64: String)

/** 消息状态 */
enum class MsgStatus { SENDING, SENT, FAILED }

/** 消息方向 */
enum class MsgDir { SENT, RECEIVED }

/** 消息 */
data class Message(
    val localId: Long = System.currentTimeMillis(), // 本地唯一ID
    val time: Long,
    val type: String,           // "msg" | "file" | "audio"
    val content: String? = null,
    val filename: String? = null,
    val fileHash: String? = null,
    val size: Long? = null,
    val isImage: Boolean = false,
    val isAudio: Boolean = false,
    val duration: Int = 0,      // 语音时长(秒)
    val aesKeyBase64: String? = null,
    val dir: MsgDir = MsgDir.RECEIVED,
    var status: MsgStatus = MsgStatus.SENT
) {
    val displayHash get() = "${dir}_${type}_${time}_${content ?: fileHash ?: ""}_$localId"
}

/** 会话卡片(首页) */
data class Conversation(
    val friendUid: String,
    val friendName: String,
    val lastMsg: String,
    val lastTime: Long,
    val unread: Int = 0
)
