# 加密聊天应用 ProGuard 规则
# 保留OkHttp相关类
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# 保留加密相关类
-keep class com.example.encryptedchat.crypto.** { *; }

# 保留JSON解析
-keep class org.json.** { *; }
