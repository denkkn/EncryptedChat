package com.example.encryptedchat.crypto

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class KeyManager(private val context: Context) {
    companion object {
        private const val PRIV = "key_私钥"; private const val PUB = "key_公钥"
    }

    private val dir get() = context.filesDir
    fun hasKeyPair() = File(dir, PRIV).exists()

    fun generateKeyPair(): Pair<String, String> {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        saveKeys(kp.private, kp.public)
        val pubB64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        return calculateUid(pubB64) to pubB64
    }

    /** 从PEM导入私钥恢复账户 */
    fun importPrivateKey(pemText: String): Pair<String, String> {
        val b64 = pemText.replace("-----BEGIN PRIVATE KEY-----\n", "")
            .replace("-----END PRIVATE KEY-----\n", "").replace("\n", "").replace("\r", "").trim()
        val privKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(b64, Base64.DEFAULT)))
        // 从私钥推导公钥（RSA私钥包含公钥信息）
        val privSpec = KeyFactory.getInstance("RSA").getKeySpec(privKey, java.security.spec.RSAPrivateCrtKeySpec::class.java)
        val pubSpec = java.security.spec.RSAPublicKeySpec(privSpec.modulus, privSpec.publicExponent)
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(pubSpec)
        saveKeys(privKey, pubKey)
        val pubB64 = Base64.encodeToString(pubKey.encoded, Base64.NO_WRAP)
        return calculateUid(pubB64) to pubB64
    }

    /** 导出私钥PEM（用于备份） */
    fun exportPrivateKeyPem(): String {
        val b64 = Base64.encodeToString(loadPrivateKey().encoded, Base64.DEFAULT)
        return "-----BEGIN PRIVATE KEY-----\n$b64-----END PRIVATE KEY-----\n"
    }

    private fun saveKeys(priv: PrivateKey, pub: PublicKey) {
        val privB64 = Base64.encodeToString(priv.encoded, Base64.DEFAULT)
        File(dir, PRIV).writeText("-----BEGIN PRIVATE KEY-----\n$privB64-----END PRIVATE KEY-----\n")
        val pubB64 = Base64.encodeToString(pub.encoded, Base64.DEFAULT)
        File(dir, PUB).writeText("-----BEGIN PUBLIC KEY-----\n$pubB64-----END PUBLIC KEY-----\n")
    }

    fun loadPrivateKey(): PrivateKey {
        val pem = File(dir, PRIV).readText()
            .replace("-----BEGIN PRIVATE KEY-----\n", "").replace("-----END PRIVATE KEY-----\n", "").replace("\n", "")
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(pem, Base64.DEFAULT)))
    }

    fun loadMyPublicKey(): PublicKey {
        val pem = File(dir, PUB).readText()
            .replace("-----BEGIN PUBLIC KEY-----\n", "").replace("-----END PUBLIC KEY-----\n", "").replace("\n", "")
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(pem, Base64.DEFAULT)))
    }

    fun getMyPublicKeyBase64() = Base64.encodeToString(loadMyPublicKey().encoded, Base64.NO_WRAP)
    fun getMyUid() = calculateUid(getMyPublicKeyBase64())

    fun loadPublicKeyFromBase64(b64: String) = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(Base64.decode(b64, Base64.NO_WRAP)))

    fun calculateUid(pubBase64: String) = MessageDigest.getInstance("SHA-256")
        .digest(pubBase64.toByteArray(Charsets.US_ASCII)).joinToString("") { "%02x".format(it) }
}
