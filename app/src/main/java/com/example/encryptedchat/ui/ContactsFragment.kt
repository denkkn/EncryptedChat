package com.example.encryptedchat.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.encryptedchat.databinding.DialogAddFriendBinding
import com.example.encryptedchat.databinding.FragmentContactsBinding
import com.example.encryptedchat.model.Friend
import com.example.encryptedchat.ui.adapters.FriendAdapter
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

class ContactsFragment : Fragment() {
    private var _b: FragmentContactsBinding? = null
    private val b get() = _b!!
    private lateinit var app: ChatApp
    private lateinit var adapter: FriendAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentContactsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        app = requireActivity().application as ChatApp
        adapter = FriendAdapter(onClick = { renameDialog(it) }, onDelete = { confirmDelete(it) })
        b.recyclerFriends.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerFriends.adapter = adapter
        b.fabAddFriend.setOnClickListener { addDialog() }
        load()
    }

    private fun load() { adapter.setFriends(app.friendsManager.loadFriends()) }

    private fun addDialog() {
        val db = DialogAddFriendBinding.inflate(LayoutInflater.from(requireContext()), null, false)
        AlertDialog.Builder(requireContext()).setView(db.root).create().apply {
            db.btnCancel.setOnClickListener { dismiss() }
            db.btnSave.setOnClickListener {
                val name = db.etFriendName.text.toString().trim()
                val pub = db.etFriendPubKey.text.toString().trim()
                if (name.isEmpty() || pub.isEmpty()) { toast("请填写完整"); return@setOnClickListener }
                try {
                    KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(android.util.Base64.decode(pub, android.util.Base64.NO_WRAP)))
                    app.friendsManager.addFriend(name, pub); load(); dismiss()
                    toast("添加成功")
                } catch (e: Exception) { toast("公钥格式错误") }
            }
            show()
        }
    }

    private fun renameDialog(f: Friend) {
        val input = EditText(requireContext()).apply { setText(f.name) }
        AlertDialog.Builder(requireContext()).setTitle("修改备注")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    app.friendsManager.addFriend(newName, f.pubBase64)
                    if (newName != f.name) app.friendsManager.deleteFriend(f.name)
                    load(); toast("已更新")
                }
            }
            .setNegativeButton("取消") { d, _ -> d.dismiss() }.show()
    }

    private fun confirmDelete(f: Friend) {
        AlertDialog.Builder(requireContext()).setTitle("删除好友").setMessage("确定删除「${f.name}」？")
            .setPositiveButton("删除") { _, _ -> app.friendsManager.deleteFriend(f.name); load(); toast("已删除") }
            .setNegativeButton("取消") { d, _ -> d.dismiss() }.show()
    }

    private fun toast(s: String) { Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show() }
    override fun onResume() { super.onResume(); load() }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
