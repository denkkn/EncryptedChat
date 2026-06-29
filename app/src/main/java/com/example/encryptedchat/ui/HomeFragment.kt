package com.example.encryptedchat.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.encryptedchat.R
import com.example.encryptedchat.databinding.FragmentHomeBinding
import com.example.encryptedchat.model.Friend
import com.example.encryptedchat.model.Message
import com.example.encryptedchat.ui.adapters.MessageAdapter
import kotlinx.coroutines.*
import java.io.File

/** 首页 — 消息：GetMsg拉全量 → SSE实时监听 */
class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private lateinit var app: ChatApp
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var sseJob: Job? = null
    private var currentUid = ""
    private var lastTs = 0L
    private lateinit var adapter: MessageAdapter
    private val friends = mutableListOf<Friend>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        app = requireActivity().application as ChatApp

        adapter = MessageAdapter(
            onFile = { downloadFile(it) },
            onImage = { msg, iv -> loadImage(msg, iv) }
        )
        b.recyclerMessages.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerMessages.adapter = adapter

        b.friendSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                stopSse()
                if (pos > 0 && pos <= friends.size) {
                    currentUid = friends[pos - 1].uid; lastTs = 0; adapter.clear()
                    pullThenSse() // GetMsg拉全量 → 启动SSE
                } else currentUid = ""
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        b.btnSend.setOnClickListener { sendText() }
        b.btnAttach.setOnClickListener { pickFile() }
    }

    // ========== 消息获取: GetMsg全量 + SSE实时 ==========

    /** 先GetMsg拉全部新消息，然后启动SSE监听 */
    private fun pullThenSse() {
        if (currentUid.isEmpty()) return
        scope.launch {
            try {
                val msgs = app.apiClient.getMessages(currentUid, lastTs)
                for (m in msgs) { adapter.add(m); if (m.time > lastTs) lastTs = m.time }
            } catch (_: Exception) {}
            startSse()
        }
    }

    /** SSE长连接接收实时消息 */
    private fun startSse() {
        if (currentUid.isEmpty()) return
        b.btnPolling.text = "SSE"
        sseJob = scope.launch {
            while (isActive && currentUid.isNotEmpty()) {
                try {
                    app.apiClient.sseListen(
                        fromUid = currentUid,
                        lastTs = lastTs,
                        onMsg = { msg ->
                            scope.launch {
                                if (adapter.add(msg)) lastTs = msg.time
                            }
                        },
                        onHb = { /* 心跳 */ },
                        onError = { /* 自动重连 */ }
                    )
                } catch (_: Exception) {}
                // SSE断开后等1秒重连
                delay(1000)
            }
        }
    }

    private fun stopSse() { sseJob?.cancel(); sseJob = null; b.btnPolling.text = "SSE" }

    // ========== 发送消息 ==========

    private fun sendText() {
        if (currentUid.isEmpty()) return toast("请先选择好友")
        val txt = b.inputMessage.text.toString().trim()
        if (txt.isEmpty()) return toast("请输入消息")
        scope.launch {
            b.btnSend.isEnabled = false; b.btnSend.text = "…"
            try {
                val r = app.apiClient.sendTextMsg(currentUid, txt)
                if (r.optInt("code") == 0) b.inputMessage.text.clear()
                else toast("发送失败: ${r.optString("msg")}")
            } catch (e: Exception) { toast("网络错误: ${e.message}") }
            finally { b.btnSend.isEnabled = true; b.btnSend.text = "发送" }
        }
    }

    private fun pickFile() {
        if (currentUid.isEmpty()) return toast("请先选择好友")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, REQ_FILE)
    }

    fun handleFileResult(uri: android.net.Uri) {
        scope.launch {
            try {
                val name = requireContext().contentResolver.query(uri, null, null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)) else null
                } ?: "unknown"
                val data = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val r = withContext(Dispatchers.IO) { app.apiClient.sendFileMsg(currentUid, data, name) }
                if (r.optInt("code") != 0) toast("发送失败: ${r.optString("msg")}")
            } catch (e: Exception) { toast("错误: ${e.message}") }
        }
    }

    // ========== 文件下载 ==========

    private fun downloadFile(msg: Message) {
        scope.launch {
            try {
                val enc = withContext(Dispatchers.IO) { app.apiClient.downloadFile(msg.fileHash) } ?: return@launch toast("下载失败")
                val aes = android.util.Base64.decode(msg.aesKeyBase64, android.util.Base64.NO_WRAP)
                val dec = app.cryptoManager.decryptFileData(enc, aes)
                val out = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), msg.filename ?: "download")
                withContext(Dispatchers.IO) { out.writeBytes(dec) }
                toast("已保存: ${out.name}")
            } catch (e: Exception) { toast("下载失败: ${e.message}") }
        }
    }

    private fun loadImage(msg: Message, iv: ImageView) {
        scope.launch {
            try {
                val enc = withContext(Dispatchers.IO) { app.apiClient.downloadFile(msg.fileHash) } ?: return@launch toast("加载失败")
                val aes = android.util.Base64.decode(msg.aesKeyBase64, android.util.Base64.NO_WRAP)
                val bmp = BitmapFactory.decodeByteArray(app.cryptoManager.decryptFileData(enc, aes), 0, (enc.size - 28).coerceAtLeast(0))
                if (bmp != null) { iv.setImageBitmap(bmp); iv.visibility = View.VISIBLE; iv.setOnClickListener { showFull(bmp) } }
            } catch (e: Exception) { toast("加载失败: ${e.message}") }
        }
    }

    private fun showFull(bmp: android.graphics.Bitmap) {
        val img = ImageView(requireContext()).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(0xFF000000.toInt()) }
        AlertDialog.Builder(requireContext()).setView(img).setPositiveButton("关闭") { d, _ -> d.dismiss() }.show()
    }

    // ========== 工具 ==========

    private fun loadSpinner() {
        friends.clear(); friends.addAll(app.friendsManager.loadFriends())
        val names = mutableListOf(getString(R.string.select_friend))
        names.addAll(friends.map { "${it.name} (${it.uid.take(12)}...)" })
        b.friendSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun toast(s: String) { Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show() }

    override fun onResume() { super.onResume(); loadSpinner() }
    override fun onDestroyView() { stopSse(); super.onDestroyView(); _b = null }

    companion object { const val REQ_FILE = 1001 }
}
