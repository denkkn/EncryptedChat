package com.example.encryptedchat.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.encryptedchat.R
import com.example.encryptedchat.model.Message
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val onFile: (Message) -> Unit,
    private val onImage: (Message, ImageView) -> Unit
) : RecyclerView.Adapter<MessageAdapter.VH>() {

    private val msgs = mutableListOf<Message>()
    private val seen = mutableSetOf<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    fun add(msg: Message): Boolean {
        if (!seen.add(msg.displayHash)) return false
        msgs.add(0, msg); notifyItemInserted(0); return true
    }

    fun addAll(newList: List<Message>) = newList.reversed().count { add(it) }

    fun clear() { msgs.clear(); seen.clear(); notifyDataSetChanged() }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        LayoutInflater.from(p.context).inflate(R.layout.item_message, p, false))

    override fun onBindViewHolder(h: VH, i: Int) = h.bind(msgs[i])

    override fun getItemCount() = msgs.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val hdr = v.findViewById<TextView>(R.id.tv_msg_header)
        private val con = v.findViewById<TextView>(R.id.tv_msg_content)
        private val img = v.findViewById<ImageView>(R.id.img_msg)
        private val act = v.findViewById<View>(R.id.ll_file_actions)
        private val btn = v.findViewById<TextView>(R.id.btn_download_file)

        fun bind(m: Message) {
            hdr.text = "[${fmt.format(Date(m.time * 1000))}] " +
                    if (m.type == "msg") "文本:" else "文件: ${m.filename} (${m.size}B)"
            when (m.type) {
                "msg" -> {
                    con.text = m.content; con.visibility = View.VISIBLE
                    img.visibility = View.GONE; act.visibility = View.GONE
                }
                "file" -> {
                    con.visibility = View.GONE; act.visibility = View.VISIBLE
                    img.visibility = View.GONE
                    btn.text = if (m.isImage) "查看图片" else "下载文件"
                    btn.setOnClickListener {
                        if (m.isImage) onImage(m, img) else onFile(m)
                    }
                }
            }
        }
    }
}
