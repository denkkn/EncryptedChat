package com.example.encryptedchat.crypto

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.*

/**
 * RSA密钥管理 — Python格式完全兼容
 * 私钥: PKCS8 PEM  公钥: SubjectPublicKeyInfo DER→Base64
 * UID = SHA256(公钥Base64)
 */
class KeyManager(private val context: Context) {

    companion object {
        private const val PRIV_FILE = "key_私钥"
        private const val PUB_FILE = "key_公钥"
    }

    fun hasKeyPair() = File(context.filesDir, PRIV_FILE).exists()

    /** 生成RSA-2048密钥对, 返回(UID, 公钥Base64) */
    fun generateKeyPair(): Pair<String, String> {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        // 写入PKCS8私钥PEM
        val privB64 = Base64.encodeToString(kp.private.encoded, Base64.DEFAULT)
        File(context.filesDir, PRIV_FILE).writeText(
            "-----BEGIN PRIVATE KEY-----\n$privB64-----END PRIVATE KEY-----\n")

        // 写入SubjectPublicKeyInfo公钥PEM
        val pubBytes = kp.public.encoded
        val pubB64 = Base64.encodeToString(pubBytes, Base64.DEFAULT)
        File(context.filesDir, PUB_FILE).writeText(
            "-----BEGIN PUBLIC KEY-----\n$pubB64-----END PUBLIC KEY-----\n")

        val pubB64NoWrap = Base64.encodeToString(pubBytes, Base64.NO_WRAP)
        return calculateUid(pubB64NoWrap) to pubB64NoWrap
    }

    fun loadPrivateKey(): PrivateKey {
        val pem = File(context.filesDir, PRIV_FILE).readText()
            .replace("-----BEGIN PRIVATE KEY-----\n", "")
            .replace("-----END PRIVATE KEY-----\n", "").replace("\n", "")
        return KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(pem, Base64.DEFAULT)))
    }

    fun loadMyPublicKey(): PublicKey {
        val pem = File(context.filesDir, PUB_FILE).readText()
            .replace("-----BEGIN PUBLIC KEY-----\n", "")
            .replace("-----END PUBLIC KEY-----\n", "").replace("\n", "")
        return KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.decode(pem, Base64.DEFAULT)))
    }

    fun getMyPublicKeyBase64() = Base64.encodeToString(loadMyPublicKey().encoded, Base64.NO_WRAP)

    fun getMyUid() = calculateUid(getMyPublicKeyBase64())

    fun loadPublicKeyFromBase64(b64: String): PublicKey = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(Base64.decode(b64, Base64.NO_WRAP)))

    fun calculateUid(pubBase64: String) = MessageDigest.getInstance("SHA-256")
        .digest(pubBase64.toByteArray(Charsets.US_ASCII))
        .joinToString("") { "%02x".format(it) }
}
