package com.example.encryptedchat.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.encryptedchat.crypto.KeyManager
import com.example.encryptedchat.databinding.ActivityAccountSetupBinding

/** 首次启动 — 导入私钥或创建新账户 */
class AccountSetupActivity : AppCompatActivity() {
    private lateinit var b: ActivityAccountSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAccountSetupBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnCreate.setOnClickListener { createAccount() }
        b.btnImport.setOnClickListener {
            b.layoutImport.visibility = if (b.layoutImport.visibility == View.GONE) View.VISIBLE else View.GONE
        }
        b.btnDoImport.setOnClickListener { importAccount() }
    }

    private fun createAccount() {
        val km = KeyManager(this)
        val (uid, _) = km.generateKeyPair()
        Toast.makeText(this, "账户已创建! UID: ${uid.take(16)}...", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun importAccount() {
        val pem = b.etPrivateKey.text.toString().trim()
        if (!pem.contains("BEGIN PRIVATE KEY")) { toast("无效的私钥格式"); return }
        try {
            val km = KeyManager(this)
            val (uid, _) = km.importPrivateKey(pem)
            Toast.makeText(this, "账户已恢复! UID: ${uid.take(16)}...", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) { toast("导入失败: ${e.message}") }
    }

    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
}
