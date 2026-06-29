package com.example.encryptedchat.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.encryptedchat.R
import com.example.encryptedchat.model.Conversation
import java.text.SimpleDateFormat
import java.util.*

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.VH>() {

    private val list = mutableListOf<Conversation>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private val todayFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun setData(data: List<Conversation>) { list.clear(); list.addAll(data); notifyDataSetChanged() }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_conversation, p, false))

    override fun onBindViewHolder(h: VH, i: Int) = h.bind(list[i])
    override fun getItemCount() = list.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val name = v.findViewById<TextView>(R.id.tv_name)
        private val time = v.findViewById<TextView>(R.id.tv_time)
        private val msg = v.findViewById<TextView>(R.id.tv_last_msg)
        private val unread = v.findViewById<TextView>(R.id.tv_unread)

        fun bind(c: Conversation) {
            name.text = c.friendName
            val d = Date(c.lastTime * 1000)
            time.text = if (isToday(d)) todayFmt.format(d) else fmt.format(d)
            msg.text = c.lastMsg
            if (c.unread > 0) { unread.visibility = View.VISIBLE; unread.text = "${c.unread}" }
            else unread.visibility = View.GONE
            itemView.setOnClickListener { onClick(c) }
        }

        private fun isToday(d: Date): Boolean {
            val cal = Calendar.getInstance()
            val today = Calendar.getInstance().apply { set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DATE)) }
            return d.time >= today.timeInMillis
        }
    }
}
