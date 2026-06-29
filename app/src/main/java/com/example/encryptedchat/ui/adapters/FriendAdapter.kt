package com.example.encryptedchat.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.encryptedchat.R
import com.example.encryptedchat.model.Friend

class FriendAdapter(
    private val onClick: (Friend) -> Unit,
    private val onDelete: (Friend) -> Unit
) : RecyclerView.Adapter<FriendAdapter.VH>() {

    private val list = mutableListOf<Friend>()

    fun setFriends(fs: List<Friend>) { list.clear(); list.addAll(fs); notifyDataSetChanged() }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_friend, p, false))

    override fun onBindViewHolder(h: VH, i: Int) = h.bind(list[i])
    override fun getItemCount() = list.size

    inner class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        fun bind(f: Friend) {
            itemView.findViewById<TextView>(R.id.tv_friend_name).text = f.name
            itemView.findViewById<TextView>(R.id.tv_friend_uid).text = f.uid.take(32) + "..."
            itemView.setOnClickListener { onClick(f) }
            itemView.findViewById<android.widget.Button>(R.id.btn_delete_friend).setOnClickListener { onDelete(f) }
        }
    }
}
