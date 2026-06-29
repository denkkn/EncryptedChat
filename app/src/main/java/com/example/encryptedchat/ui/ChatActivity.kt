package com.example.encryptedchat.ui

import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.encryptedchat.databinding.ActivityChatBinding
import com.example.encryptedchat.model.*
import com.example.encryptedchat.ui.adapters.ChatAdapter
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream

class ChatActivity : AppCompatActivity() {
    private lateinit var b: ActivityChatBinding
    private lateinit var app: ChatApp
    private lateinit var adapter: ChatAdapter
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var sseJob: Job? = null
    private var friendUid = ""
    private var friendName = ""
    private var lastTs = 0L
    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChatBinding.inflate(layoutInflater)
        setContentView(b.root)
        app = application as ChatApp

        friendUid = intent.getStringExtra("uid") ?: return finish()
        friendName = intent.getStringExtra("name") ?: ""

        b.toolbar.title = friendName
        b.toolbar.setNavigationOnClickListener { finish() }

        adapter = ChatAdapter(
            onRetry = { retrySend(it) },
            onDownload = { download(it) },
            onPlay = { playAudio(it) }
        )
        b.recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        b.recyclerChat.adapter = adapter

        b.btnSend.setOnClickListener { sendText() }
        b.btnAttach.setOnClickListener { pickFile() }
        b.btnRecord.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> startRecord()
                android.view.MotionEvent.ACTION_UP -> stopRecord()
            }
            true
        }

        pullThenSse()
    }

    // ===== 加载消息 + SSE =====

    private fun pullThenSse() {
        scope.launch {
            try {
                val msgs = app.apiClient.getMessages(friendUid, lastTs)
                for (m in msgs) { adapter.add(m); if (m.time > lastTs) lastTs = m.time }
                app.convManager.markRead(friendUid)
            } catch (_: Exception) {}
            startSse()
        }
    }

    private fun startSse() {
        sseJob = scope.launch {
            while (isActive) {
                try {
                    app.apiClient.sseListen(friendUid, lastTs,
                        onMsg = { msg -> scope.launch {
                            val m = msg.copy(dir = MsgDir.RECEIVED)
                            if (adapter.add(m)) { lastTs = m.time; app.convManager.upsert(friendUid, friendName, preview(m), m.time, 0) }
                        }},
                        onHb = {}, onError = {})
                } catch (_: Exception) {}
                delay(1000)
            }
        }
    }

    // ===== 发送 =====

    private fun sendText() {
        val txt = b.inputText.text.toString().trim()
        if (txt.isEmpty()) return
        b.inputText.text.clear()
        val localId = System.currentTimeMillis()
        val msg = Message(localId = localId, time = System.currentTimeMillis() / 1000, type = "msg", content = txt, dir = MsgDir.SENT, status = MsgStatus.SENDING)
        adapter.add(msg)
        app.convManager.upsert(friendUid, friendName, txt, msg.time, 0)

        scope.launch {
            try {
                val r = app.apiClient.sendTextMsg(friendUid, txt)
                if (r.optInt("code") == 0) adapter.updateStatus(localId, MsgStatus.SENT)
                else adapter.updateStatus(localId, MsgStatus.FAILED)
            } catch (_: Exception) { adapter.updateStatus(localId, MsgStatus.FAILED) }
        }
    }

    private fun retrySend(msg: Message) {
        adapter.updateStatus(msg.localId, MsgStatus.SENDING)
        scope.launch {
            try {
                val r = if (msg.type == "audio") {
                    app.apiClient.sendFileMsg(friendUid, File(msg.content ?: return@launch).readBytes(), msg.filename ?: "voice.aac")
                } else if (!msg.filename.isNullOrEmpty()) {
                    // file retry needs original data - this is approximate
                    app.apiClient.sendFileMsg(friendUid, ByteArray(0), msg.filename)
                } else {
                    app.apiClient.sendTextMsg(friendUid, msg.content ?: "")
                }
                if (r.optInt("code") == 0) adapter.updateStatus(msg.localId, MsgStatus.SENT)
                else adapter.updateStatus(msg.localId, MsgStatus.FAILED)
            } catch (_: Exception) { adapter.updateStatus(msg.localId, MsgStatus.FAILED) }
        }
    }

    private fun pickFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, 100)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 100 && res == RESULT_OK) data?.data?.let { sendFile(it) }
    }

    private fun sendFile(uri: Uri) {
        scope.launch {
            val name = contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) it.getString(it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)) else null
            } ?: "unknown"
            val data = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val localId = System.currentTimeMillis()
            val isImg = name.lowercase().matches(Regex(".*\\.(jpg|jpeg|png|gif|bmp|webp)$"))
            val isAudio = name.lowercase().matches(Regex(".*\\.(aac|mp3|wav|ogg|m4a)$"))
            val type = if (isAudio) "audio" else "file"
            val msg = Message(localId = localId, time = System.currentTimeMillis() / 1000, type = type, filename = name, size = data.size.toLong(), isImage = isImg, isAudio = isAudio, dir = MsgDir.SENT, status = MsgStatus.SENDING)
            adapter.add(msg)
            app.convManager.upsert(friendUid, friendName, "[${if (isAudio) "语音" else if (isImg) "图片" else "文件"}] $name", msg.time, 0)

            try {
                val r = withContext(Dispatchers.IO) { app.apiClient.sendFileMsg(friendUid, data, name) }
                if (r.optInt("code") == 0) adapter.updateStatus(localId, MsgStatus.SENT)
                else adapter.updateStatus(localId, MsgStatus.FAILED)
            } catch (_: Exception) { adapter.updateStatus(localId, MsgStatus.FAILED) }
        }
    }

    // ===== 录音 =====

    private fun startRecord() {
        recordFile = File(externalCacheDir, "voice_${System.currentTimeMillis()}.aac")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setOutputFile(recordFile!!.absolutePath)
            prepare(); start()
        }
        Toast.makeText(this, "录音中...", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecord() {
        try {
            recorder?.apply { stop(); release() }; recorder = null
            recordFile?.let { f ->
                val localId = System.currentTimeMillis()
                val duration = (f.length() / 16000).toInt() // approximate for AAC
                val msg = Message(localId = localId, time = System.currentTimeMillis() / 1000, type = "audio", filename = "voice.aac", size = f.length(), isAudio = true, duration = duration, content = f.absolutePath, dir = MsgDir.SENT, status = MsgStatus.SENDING)
                adapter.add(msg)
                app.convManager.upsert(friendUid, friendName, "[语音 ${duration}\"]", msg.time, 0)

                scope.launch {
                    try {
                        val r = withContext(Dispatchers.IO) { app.apiClient.sendFileMsg(friendUid, f.readBytes(), "voice.aac") }
                        if (r.optInt("code") == 0) adapter.updateStatus(localId, MsgStatus.SENT)
                        else adapter.updateStatus(localId, MsgStatus.FAILED)
                    } catch (_: Exception) { adapter.updateStatus(localId, MsgStatus.FAILED) }
                }
            }
        } catch (_: Exception) {}
        b.btnRecord.isPressed = false
    }

    // ===== 下载 =====

    private fun download(msg: Message) {
        scope.launch {
            try {
                val hash = msg.fileHash ?: return@launch
                val enc = withContext(Dispatchers.IO) { app.apiClient.downloadFile(hash) } ?: return@launch toast("下载失败")
                val aes = android.util.Base64.decode(msg.aesKeyBase64, android.util.Base64.NO_WRAP)
                val dec = app.crypto.decryptFileData(enc, aes)
                val dir = getExternalFilesDir("downloads")!!; dir.mkdirs()
                val f = File(dir, hash)
                f.writeBytes(dec)
                // 通知adapter刷新显示
                adapter.updateStatus(msg.localId, MsgStatus.SENT)
                adapter.notifyDataSetChanged()
                if (msg.isImage || msg.isAudio) toast("下载完成")
                else toast("已保存: ${msg.filename}")
            } catch (e: Exception) { toast("下载失败: ${e.message}") }
        }
    }

    // ===== 播放音频 =====

    private fun playAudio(msg: Message) {
        val hash = msg.fileHash ?: return
        val f = File(getExternalFilesDir("downloads"), hash)
        if (!f.exists()) {
            // 先下载再播放
            scope.launch {
                download(msg)
                delay(500)
                playFile(f)
            }
        } else {
            playFile(f)
        }
    }

    private fun playFile(f: File) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(FileInputStream(f).fd)
            prepare(); start()
            setOnCompletionListener { it.release(); player = null }
        }
    }

    // ===== 工具 =====

    private fun preview(m: Message) = when {
        m.type == "audio" -> "[语音]"
        m.isImage -> "[图片]"
        !m.filename.isNullOrEmpty() -> "[文件] ${m.filename}"
        else -> m.content ?: ""
    }

    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        sseJob?.cancel(); player?.release(); recorder?.release(); super.onDestroy()
    }
}
