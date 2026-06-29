package com.example.encryptedchat.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.encryptedchat.R
import com.example.encryptedchat.crypto.KeyManager
import com.example.encryptedchat.databinding.FragmentSettingsBinding

/** 设置 — 密钥管理与分享 */
class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSettingsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        refresh()
        b.btnCopyPubKey.setOnClickListener {
            val pub = b.tvMyPubKey.text.toString()
            if (pub.isNotEmpty() && !pub.startsWith("请")) {
                (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("公钥Base64", pub))
                toast("已复制!")
            }
        }
        b.btnRegenerate.setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle("重新生成密钥对")
                .setMessage(R.string.confirm_regenerate)
                .setPositiveButton("确定") { _, _ -> regenerate() }
                .setNegativeButton("取消") { d, _ -> d.dismiss() }.show()
        }
    }

    private fun refresh() {
        try {
            val app = requireActivity().application as ChatApp
            b.tvMyUid.text = app.cryptoManager.getMyUid()
            b.tvMyPubKey.text = app.cryptoManager.getMyPubBase64()
        } catch (e: Exception) {
            b.tvMyUid.text = "加载失败: ${e.message}"
        }
    }

    private fun regenerate() {
        try {
            val (uid, pub) = KeyManager(requireContext()).generateKeyPair()
            b.tvMyUid.text = uid; b.tvMyPubKey.text = pub
            toast("密钥已重新生成!")
        } catch (e: Exception) { toast("生成失败: ${e.message}") }
    }

    private fun toast(s: String) { Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show() }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
