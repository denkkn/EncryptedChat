package com.example.encryptedchat.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.encryptedchat.databinding.DialogAddFriendBinding
import com.example.encryptedchat.databinding.FragmentContactsBinding
import com.example.encryptedchat.model.Friend
import com.example.encryptedchat.ui.adapters.FriendAdapter
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

/** 联系人 — 好友列表与添加 */
class ContactsFragment : Fragment() {

    private var _b: FragmentContactsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: FriendAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentContactsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        val app = requireActivity().application as ChatApp
        adapter = FriendAdapter(
            onClick = { showDetail(it) },
            onDelete = { confirmDelete(it) }
        )
        b.recyclerFriends.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerFriends.adapter = adapter
        b.fabAddFriend.setOnClickListener { showAddDialog() }
        load()
    }

    private fun load() { adapter.setFriends((requireActivity().application as ChatApp).friendsManager.loadFriends()) }

    private fun showAddDialog() {
        val db = DialogAddFriendBinding.inflate(LayoutInflater.from(requireContext()), null, false)
        AlertDialog.Builder(requireContext()).setView(db.root).create().apply {
            db.btnCancel.setOnClickListener { dismiss() }
            db.btnSave.setOnClickListener {
                val name = db.etFriendName.text.toString().trim()
                val pub = db.etFriendPubKey.text.toString().trim()
                if (name.isEmpty()) { toast("请输入好友备注"); return@setOnClickListener }
                if (pub.isEmpty()) { toast("请粘贴好友公钥"); return@setOnClickListener }
                try {
                    KeyFactory.getInstance("RSA").generatePublic(
                        X509EncodedKeySpec(android.util.Base64.decode(pub, android.util.Base64.NO_WRAP)))
                    val uid = (requireActivity().application as ChatApp).friendsManager.addFriend(name, pub)
                    toast("添加成功! UID: ${uid.take(16)}..."); load(); dismiss()
                } catch (e: Exception) { toast("公钥格式错误") }
            }
            show()
        }
    }

    private fun showDetail(f: Friend) {
        AlertDialog.Builder(requireContext()).setTitle("好友详情")
            .setMessage("备注: ${f.name}\nUID: ${f.uid}\n\n公钥(Base64):\n${f.pubBase64}")
            .setPositiveButton("关闭") { d, _ -> d.dismiss() }.show()
    }

    private fun confirmDelete(f: Friend) {
        AlertDialog.Builder(requireContext()).setTitle("删除好友").setMessage("确定删除「${f.name}」？")
            .setPositiveButton("删除") { _, _ ->
                (requireActivity().application as ChatApp).friendsManager.deleteFriend(f.name)
                load(); toast("已删除")
            }.setNegativeButton("取消") { d, _ -> d.dismiss() }.show()
    }

    private fun toast(s: String) { Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show() }

    override fun onResume() { super.onResume(); load() }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
