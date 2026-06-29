package com.example.encryptedchat.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.encryptedchat.R
import com.example.encryptedchat.databinding.FragmentHomeBinding
import com.example.encryptedchat.ui.adapters.ConversationAdapter

/** 首页 — 会话卡片列表(QQ/微信风格)，FAB跳转联系人发起新会话 */
class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private lateinit var app: ChatApp
    private lateinit var adapter: ConversationAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentHomeBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        app = requireActivity().application as ChatApp
        adapter = ConversationAdapter { conv ->
            startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra("uid", conv.friendUid); putExtra("name", conv.friendName)
            })
        }
        b.recyclerConversations.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerConversations.adapter = adapter

        // FAB → 跳转到联系人页
        b.fabNewChat.setOnClickListener {
            (requireActivity() as com.example.encryptedchat.MainActivity).let { act ->
                act.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
                    R.id.bottom_nav)?.selectedItemId = R.id.nav_contacts
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.setData(app.convManager.load())
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
