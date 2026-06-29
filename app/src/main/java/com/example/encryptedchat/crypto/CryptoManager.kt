package com.example.encryptedchat.crypto

import android.util.Base64
import org.json.JSONObject
import java.security.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密/解密/签名 — 与Python main..py算法完全一致
 *
 * RSA-2048 OAEP(SHA-256) 加密AES密钥
 * AES-256-GCM (12B IV) 加密消息/文件
 * RSA-PKCS1v15 SHA-256 签名(规范化JSON)
 */
class CryptoManager(private val keyManager: KeyManager) {

    /** 生成AES-256密钥 */
    fun generateAesKey() = ByteArray(32).also { SecureRandom().nextBytes(it) }

    /** AES-GCM加密JSON → Base64(IV+ciphertext+tag) */
    fun encryptMessageJson(data: JSONObject, aesKey: ByteArray): String {
        val json = data.toString().toByteArray(Charsets.UTF_8)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + cipher.doFinal(json), Base64.NO_WRAP)
    }

    /** AES-GCM解密Base64消息 → JSONObject */
    fun decryptMessageJson(encB64: String, aesKey: ByteArray): JSONObject {
        val raw = Base64.decode(encB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return JSONObject(String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8))
    }

    /** RSA-OAEP加密AES密钥 */
    fun rsaEncryptKey(aesKey: ByteArray, pubKey: PublicKey): String {
        val c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        c.init(Cipher.ENCRYPT_MODE, pubKey)
        return Base64.encodeToString(c.doFinal(aesKey), Base64.NO_WRAP)
    }

    /** RSA-OAEP解密AES密钥 */
    fun rsaDecryptKey(encB64: String, privKey: PrivateKey): ByteArray {
        val c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        c.init(Cipher.DECRYPT_MODE, privKey)
        return c.doFinal(Base64.decode(encB64, Base64.NO_WRAP))
    }

    // ---- 签名(与Python normalize_data_for_signing + PKCS1v15完全一致) ----

    fun signData(data: Map<String, Any?>, privKey: PrivateKey): String {
        val json = serializeMap(data)
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privKey)
        sig.update(json.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
    }

    /** 规范化JSON序列化: 键排序,无空格,Unicode不转义 */
    private fun serialize(v: Any?): String = when (v) {
        null -> "null"
        is Boolean -> if (v) "true" else "false"
        is Number -> v.toString()
        is String -> "\"${esc(v)}\""
        is Map<*, *> -> serializeMap(v.entries.associate { it.key.toString() to it.value })
        is List<*> -> "[${v.joinToString(",") { serialize(it) }}]"
        is Array<*> -> "[${v.joinToString(",") { serialize(it) }}]"
        else -> "\"${esc(v.toString())}\""
    }

    private fun serializeMap(map: Map<String, Any?>): String =
        "{${map.keys.sorted().joinToString(",") { "\"${esc(it)}\":${serialize(map[it])}" }}}"

    private fun esc(s: String) = buildString(s.length + 16) {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '' -> append("\\f") // Form Feed (U+000C)
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u${c.code.toString(16).padStart(4, '0')}") else append(c)
        }
    }

    // ---- 文件加密/解密 ----

    fun encryptFileData(data: ByteArray, aesKey: ByteArray): Pair<ByteArray, String> {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val enc = iv + cipher.doFinal(data)
        val hash = MessageDigest.getInstance("SHA-256").digest(enc).joinToString("") { "%02x".format(it) }
        return enc to hash
    }

    fun decryptFileData(enc: ByteArray, aesKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(128, enc.copyOfRange(0, 12)))
        return cipher.doFinal(enc.copyOfRange(12, enc.size))
    }

    // ---- 完整的加解密消息 ----

    fun encryptForFriend(msgJson: JSONObject, friendPubBase64: String): Pair<String, String> {
        val aes = generateAesKey()
        val pubKey = keyManager.loadPublicKeyFromBase64(friendPubBase64)
        return encryptMessageJson(msgJson, aes) to rsaEncryptKey(aes, pubKey)
    }

    data class DecryptedMsg(
        val time: Long, val type: String,
        val content: String? = null,
        val filename: String? = null, val fileHash: String? = null,
        val size: Long? = null, val isImage: Boolean = false,
        val aesKeyBase64: String? = null
    )

    fun decryptReceivedMsg(encMsgB64: String, encKeyB64: String): DecryptedMsg {
        val aes = rsaDecryptKey(encKeyB64, keyManager.loadPrivateKey())
        val json = decryptMessageJson(encMsgB64, aes)
        val type = json.optString("type", "unknown")
        return DecryptedMsg(
            time = json.optLong("time"), type = type,
            content = if (type == "msg") json.optString("msg") else null,
            filename = if (type == "file") json.optString("filename") else null,
            fileHash = if (type == "file") json.optString("hash") else null,
            size = if (type == "file") json.optLong("size") else null,
            isImage = json.optBoolean("is_image"),
            aesKeyBase64 = if (type == "file") Base64.encodeToString(aes, Base64.NO_WRAP) else null
        )
    }

    fun getPrivateKey() = keyManager.loadPrivateKey()
    fun getMyPubBase64() = keyManager.getMyPublicKeyBase64()
    fun getMyUid() = keyManager.getMyUid()
}
