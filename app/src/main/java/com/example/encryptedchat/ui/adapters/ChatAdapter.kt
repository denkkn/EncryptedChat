package com.example.encryptedchat.ui.adapters

import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.encryptedchat.R
import com.example.encryptedchat.model.Message
import com.example.encryptedchat.model.MsgDir
import com.example.encryptedchat.model.MsgStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val onRetry: (Message) -> Unit,
    private val onDownload: (Message) -> Unit,
    private val onPlay: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val msgs = mutableListOf<Message>()
    private val seen = mutableSetOf<Long>()
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object { private const val T_SENT = 0; private const val T_RECV = 1 }

    fun add(msg: Message): Boolean {
        if (!seen.add(msg.localId)) return false
        msgs.add(msg); notifyItemInserted(msgs.size - 1); return true
    }
    fun updateStatus(localId: Long, status: MsgStatus) {
        val i = msgs.indexOfFirst { it.localId == localId }
        if (i >= 0) { msgs[i].status = status; notifyItemChanged(i) }
    }
    fun clear() { msgs.clear(); seen.clear(); notifyDataSetChanged() }
    fun findPosition(localId: Long) = msgs.indexOfFirst { it.localId == localId }.takeIf { it >= 0 }

    override fun getItemViewType(i: Int) = if (msgs[i].dir == MsgDir.SENT) T_SENT else T_RECV
    override fun getItemCount() = msgs.size

    override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(p.context)
        return if (t == T_SENT) SentVH(inflater.inflate(R.layout.item_chat_sent, p, false))
        else RecvVH(inflater.inflate(R.layout.item_chat_recv, p, false))
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
        val m = msgs[i]
        if (h is SentVH) h.bind(m) else (h as RecvVH).bind(m)
    }

    // ---- Sent ViewHolder ----
    inner class SentVH(v: View) : RecyclerView.ViewHolder(v) {
        private val content = v.findViewById<TextView>(R.id.tv_content)
        private val time = v.findViewById<TextView>(R.id.tv_time)
        private val status = v.findViewById<ImageView>(R.id.iv_status)
        private val retry = v.findViewById<Button>(R.id.btn_retry)
        private val progress = v.findViewById<ProgressBar>(R.id.progress_send)
        private val imgPreview = v.findViewById<ImageView>(R.id.img_preview)
        private val fileBox = v.findViewById<LinearLayout>(R.id.ll_file)
        private val fileName = v.findViewById<TextView>(R.id.tv_filename)
        private val fileSize = v.findViewById<TextView>(R.id.tv_filesize)
        private val audioBox = v.findViewById<LinearLayout>(R.id.ll_audio)
        private val audioDur = v.findViewById<TextView>(R.id.tv_audio_dur)

        fun bind(m: Message) {
            time.text = fmt.format(Date(m.time * 1000))
            when {
                m.type == "audio" -> {
                    content.visibility = View.GONE; imgPreview.visibility = View.GONE; fileBox.visibility = View.GONE
                    audioBox.visibility = View.VISIBLE; audioDur.text = "${m.duration}\""
                    audioBox.setOnClickListener { onPlay(m) }
                }
                m.isImage -> {
                    content.visibility = View.GONE; audioBox.visibility = View.GONE; fileBox.visibility = View.GONE
                    imgPreview.visibility = View.VISIBLE
                    // 图片已发送时显示缩略图（从content解码）
                    m.content?.let { data ->
                        try {
                            val bytes = android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
                            imgPreview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                        } catch (_: Exception) {}
                    }
                }
                !m.filename.isNullOrEmpty() -> {
                    content.visibility = View.GONE; imgPreview.visibility = View.GONE; audioBox.visibility = View.GONE
                    fileBox.visibility = View.VISIBLE; fileName.text = m.filename; fileSize.text = fmtSize(m.size ?: 0)
                }
                else -> {
                    imgPreview.visibility = View.GONE; fileBox.visibility = View.GONE; audioBox.visibility = View.GONE
                    content.visibility = View.VISIBLE; content.text = m.content
                }
            }
            when (m.status) {
                MsgStatus.SENDING -> { status.visibility = View.GONE; retry.visibility = View.GONE; progress.visibility = View.VISIBLE }
                MsgStatus.FAILED -> { status.visibility = View.GONE; progress.visibility = View.GONE; retry.visibility = View.VISIBLE; retry.setOnClickListener { onRetry(m) } }
                MsgStatus.SENT -> { progress.visibility = View.GONE; retry.visibility = View.GONE; status.visibility = View.VISIBLE; status.setImageResource(android.R.drawable.presence_online) }
            }
        }
    }

    // ---- Received ViewHolder ----
    inner class RecvVH(v: View) : RecyclerView.ViewHolder(v) {
        private val content = v.findViewById<TextView>(R.id.tv_content)
        private val time = v.findViewById<TextView>(R.id.tv_time)
        private val imgPreview = v.findViewById<ImageView>(R.id.img_preview)
        private val fileBox = v.findViewById<LinearLayout>(R.id.ll_file)
        private val fileName = v.findViewById<TextView>(R.id.tv_filename)
        private val fileSize = v.findViewById<TextView>(R.id.tv_filesize)
        private val btnDown = v.findViewById<Button>(R.id.btn_download)
        private val downProgress = v.findViewById<ProgressBar>(R.id.progress_download)
        private val audioBox = v.findViewById<LinearLayout>(R.id.ll_audio)
        private val audioDur = v.findViewById<TextView>(R.id.tv_audio_dur)

        fun bind(m: Message) {
            time.text = fmt.format(Date(m.time * 1000))
            when {
                m.type == "audio" -> {
                    content.visibility = View.GONE; imgPreview.visibility = View.GONE; fileBox.visibility = View.GONE
                    audioBox.visibility = View.VISIBLE; audioDur.text = "${m.duration}\""
                    audioBox.setOnClickListener { onPlay(m) }
                }
                m.isImage -> {
                    content.visibility = View.GONE; audioBox.visibility = View.GONE; fileBox.visibility = View.GONE
                    imgPreview.visibility = View.VISIBLE; btnDown.visibility = View.VISIBLE; downProgress.visibility = View.GONE
                    val f = File(v.context.getExternalFilesDir("downloads"), m.fileHash ?: "")
                    if (f.exists()) {
                        imgPreview.setImageBitmap(BitmapFactory.decodeFile(f.absolutePath))
                        btnDown.visibility = View.GONE
                    } else {
                        btnDown.text = "下载图片 (${fmtSize(m.size ?: 0)})"
                        btnDown.setOnClickListener { btnDown.visibility = View.GONE; downProgress.visibility = View.VISIBLE; onDownload(m) }
                    }
                }
                !m.filename.isNullOrEmpty() -> {
                    content.visibility = View.GONE; imgPreview.visibility = View.GONE; audioBox.visibility = View.GONE
                    fileBox.visibility = View.VISIBLE; fileName.text = m.filename; fileSize.text = fmtSize(m.size ?: 0)
                    val f = File(v.context.getExternalFilesDir("downloads"), m.fileHash ?: "")
                    if (f.exists()) {
                        btnDown.text = "打开"; btnDown.setOnClickListener { openFile(v.context, f, m.filename ?: "") }
                    } else {
                        btnDown.text = "下载"; btnDown.setOnClickListener { btnDown.visibility = View.GONE; downProgress.visibility = View.VISIBLE; onDownload(m) }
                    }
                }
                else -> {
                    imgPreview.visibility = View.GONE; fileBox.visibility = View.GONE; audioBox.visibility = View.GONE
                    content.visibility = View.VISIBLE; content.text = m.content
                }
            }
        }
    }

    private fun fmtSize(bytes: Long) = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${bytes / (1024 * 1024)}MB"
    }

    private fun openFile(ctx: android.content.Context, f: File, name: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, ctx.contentResolver.getType(uri) ?: "*/*")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { ctx.startActivity(intent) } catch (_: Exception) {
            Toast.makeText(ctx, "无法打开文件", Toast.LENGTH_SHORT).show()
        }
    }
}
