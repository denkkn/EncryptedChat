package com.example.encryptedchat.model

/** 好友 */
data class Friend(val name: String, val uid: String, val pubBase64: String)

/** 消息 */
data class Message(
    val time: Long,
    val type: String,                   // "msg" | "file"
    val content: String? = null,        // 文本内容
    val filename: String? = null,       // 文件名
    val fileHash: String? = null,       // 文件哈希
    val size: Long? = null,             // 文件大小
    val isImage: Boolean = false,       // 是否为图片
    val aesKeyBase64: String? = null    // AES密钥(文件解密)
) {
    val displayHash get() = "${type}_${time}_${content ?: fileHash ?: ""}"
}
