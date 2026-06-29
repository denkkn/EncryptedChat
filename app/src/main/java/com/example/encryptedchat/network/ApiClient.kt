package com.example.encryptedchat.network

import com.example.encryptedchat.crypto.CryptoManager
import com.example.encryptedchat.model.Friend
import com.example.encryptedchat.model.Message
import com.example.encryptedchat.storage.FriendsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * API客户端 — 与Python main..py完全一致的API格式
 * POST {API_URL}  Body: {"sig":"...", "data":{"pub":"...","ts":...,"type":"...",...}}
 */
class ApiClient(
    private val crypto: CryptoManager,
    private val friends: FriendsManager
) {
    companion object {
        const val SERVER = "http://47.113.126.123:8891"
        private const val API = "$SERVER/api/api.php"
        private const val UPLOAD = "$SERVER/api/upload.php"
        const val DOWNLOAD = "$SERVER/uploads"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // SSE长连接需要更长的超时时间（服务端35s超时）
    private val sseHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 无超时
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // ---- 请求构建 ----

    private fun buildReq(type: String, extra: Map<String, Any?>? = null) = mutableMapOf<String, Any?>(
        "pub" to crypto.getMyPubBase64(),
        "ts" to System.currentTimeMillis() / 1000,
        "type" to type
    ).also { extra?.let { e -> it.putAll(e) } }

    private suspend fun callApi(type: String, extra: Map<String, Any?>? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val data = buildReq(type, extra)
            val body = JSONObject().apply {
                put("sig", crypto.signData(data, crypto.getPrivateKey()))
                put("data", JSONObject(data))
            }
            val res = http.newCall(Request.Builder().url(API)
                .post(body.toString().toRequestBody(JSON)).build()).execute()
            try { JSONObject(res.body?.string() ?: "{}") }
            catch (e: Exception) { JSONObject("{\"code\":-1,\"msg\":\"${e.message}\"}") }
        }

    // ---- 消息 ----

    suspend fun sendTextMsg(toUid: String, text: String): JSONObject {
        val f = friends.findFriendByUid(toUid) ?: return JSONObject("{\"code\":-1,\"msg\":\"好友不存在\"}")
        val (encMsg, encKey) = crypto.encryptForFriend(JSONObject().apply {
            put("type", "msg"); put("msg", text); put("time", System.currentTimeMillis() / 1000)
        }, f.pubBase64)
        return callApi("SendMsg", mapOf("recipient" to toUid, "msg" to encMsg, "key" to encKey))
    }

    suspend fun sendFileMsg(toUid: String, fileData: ByteArray, fileName: String): JSONObject {
        val f = friends.findFriendByUid(toUid) ?: return JSONObject("{\"code\":-1,\"msg\":\"好友不存在\"}")
        val isImg = fileName.lowercase().let {
            it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") ||
                    it.endsWith(".gif") || it.endsWith(".bmp") || it.endsWith(".webp")
        }
        // 加密并上传
        val aes = crypto.generateAesKey()
        val (encData, hash) = crypto.encryptFileData(fileData, aes)
        val upRes = uploadFile(encData, hash)
        if (upRes.optInt("code") != 0) return JSONObject("{\"code\":-1,\"msg\":\"上传失败: ${upRes.optString("msg")}\"}")
        // 发送文件消息
        val (encMsg, encKey) = crypto.encryptForFriend(JSONObject().apply {
            put("type", "file"); put("filename", fileName); put("hash", hash)
            put("size", fileData.size); put("is_image", isImg)
            put("time", System.currentTimeMillis() / 1000)
        }, f.pubBase64)
        return callApi("SendMsg", mapOf("recipient" to toUid, "msg" to encMsg, "key" to encKey))
    }

    suspend fun getMessages(fromUid: String, lastTs: Long = 0): List<Message> {
        val res = callApi("GetMsg", mapOf("from" to fromUid, "last_ts" to lastTs))
        if (res.optInt("code") != 0) return emptyList()
        val raw = res.optJSONObject("data") ?: return emptyList()
        val msgs = mutableListOf<Message>()
        val sortedKeys = mutableListOf<String>().also { k -> raw.keys().forEach { k.add(it) } }.also { it.sort() }
        for (ts in sortedKeys) try {
            val m = raw.getJSONObject(ts)
            val dm = crypto.decryptReceivedMsg(m.getString("msg"), m.getString("key"))
            msgs.add(Message(dm.time, dm.type, dm.content, dm.filename, dm.fileHash, dm.size, dm.isImage, dm.aesKeyBase64))
        } catch (_: Exception) {}
        return msgs
    }

    // ---- 文件上传/下载 ----

    private suspend fun uploadFile(data: ByteArray, hash: String): JSONObject = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "encrypted_file.bin", data.toRequestBody("application/octet-stream".toMediaType()))
            .addFormDataPart("hash", hash).build()
        val res = http.newCall(Request.Builder().url(UPLOAD).post(body).build()).execute()
        val text = res.body?.string() ?: "{}"
        if ((res.header("Content-Type") ?: "").contains("json")) try { JSONObject(text) }
        catch (_: Exception) { JSONObject("{\"code\":-1,\"msg\":\"$text\"}") }
        else JSONObject("{\"code\":-1,\"msg\":\"$text\"}")
    }

    suspend fun downloadFile(hash: String): ByteArray? = withContext(Dispatchers.IO) {
        val res = http.newCall(Request.Builder().url("$DOWNLOAD/$hash").get().build()).execute()
        if (res.isSuccessful) res.body?.bytes() else null
    }

    // ---- SSE 实时监听 (对应 Python api_sse) ----

    /**
     * SSE长连接 — POST流式响应，解析 data: / hb / error 行
     * 返回时SSE已断开（需重连）
     */
    suspend fun sseListen(
        fromUid: String,
        lastTs: Long,
        onMsg: (Message) -> Unit,
        onHb: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val data = buildReq("SseMsg", mapOf("from" to fromUid, "last_ts" to lastTs))
        val body = JSONObject().apply {
            put("sig", crypto.signData(data, crypto.getPrivateKey()))
            put("data", JSONObject(data))
        }
        val req = Request.Builder().url(API)
            .post(body.toString().toRequestBody(JSON))
            .header("Accept", "text/event-stream")
            .build()

        try {
            val res = sseHttp.newCall(req).execute()
            val body = res.body ?: return@withContext
            val reader = java.io.BufferedReader(body.charStream())
            var currentTs = lastTs

            reader.use { r ->
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    when {
                        l.startsWith("data: ") -> try {
                            val evt = JSONObject(l.substring(6))
                            if (evt.optInt("code") == 0) {
                                val dm = crypto.decryptReceivedMsg(
                                    evt.getString("msg"), evt.getString("key"))
                                val msg = Message(dm.time, dm.type, dm.content,
                                    dm.filename, dm.fileHash, dm.size, dm.isImage, dm.aesKeyBase64)
                                if (msg.time > currentTs) currentTs = msg.time
                                onMsg(msg)
                            }
                        } catch (_: Exception) {}
                        l.startsWith("hb") -> onHb()
                        l.startsWith("event: error") -> onError("服务端错误")
                    }
                }
            }
        } catch (e: Exception) {
            onError(e.message ?: "SSE连接断开")
        }
    }
}
