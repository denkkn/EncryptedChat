package com.example.encryptedchat.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.encryptedchat.crypto.KeyManager
import com.example.encryptedchat.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSettingsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        val app = requireActivity().application as ChatApp
        refresh(app)

        b.btnCopyPubKey.setOnClickListener {
            (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("公钥", b.tvMyPubKey.text.toString()))
            toast("已复制公钥")
        }
        b.btnExportPriv.setOnClickListener {
            val pem = app.keyManager.exportPrivateKeyPem()
            (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("私钥", pem))
            toast("私钥已复制到剪贴板！请妥善保管")
        }
        b.btnImportAccount.setOnClickListener {
            startActivity(Intent(requireContext(), AccountSetupActivity::class.java))
        }
        b.switchDark.setOnCheckedChangeListener { _, isChecked ->
            val ctx = requireContext().applicationContext
            ThemeManager.setDark(ctx, isChecked)
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun refresh(app: ChatApp) {
        b.tvMyUid.text = app.keyManager.getMyUid()
        b.tvMyPubKey.text = app.keyManager.getMyPublicKeyBase64()
        b.switchDark.isChecked = ThemeManager.isDark(requireContext())
    }

    private fun toast(s: String) { Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show() }
    override fun onResume() { super.onResume(); refresh(requireActivity().application as ChatApp) }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
