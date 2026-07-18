package com.mysecret.backup

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * WebDAV 客户端：支持上传文件和测试连接。
 * 使用 HTTP PUT 上传，OPTIONS 测试连接。
 */
class WebDavClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val authHeader: String by lazy {
        Credentials.basic(username, password)
    }

    /** 规范化 URL：确保以 / 结尾的 base + remotePath 拼接正确。 */
    private fun buildUrl(remotePath: String): String {
        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val path = remotePath.trimStart('/')
        return URL(base + path).toString()
    }

    /** 测试连接：尝试在服务器根目录执行 PROPFIND。 */
    fun testConnection(): Result<Unit> = try {
        val request = Request.Builder()
            .url(buildUrl(""))
            .header("Authorization", authHeader)
            .header("Depth", "0")
            .method("PROPFIND", "".toRequestBody())
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful || response.code in setOf(207, 401)) {
                if (response.code == 401) {
                    Result.failure(Exception("认证失败（401），请检查用户名和密码"))
                } else {
                    Result.success(Unit)
                }
            } else {
                Result.failure(Exception("服务器返回 ${response.code}"))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * 上传文件到 WebDAV 服务器。
     * @param data 加密后的字节数据
     * @param remotePath 远程相对路径，如 backup/mysecret.mysecret
     */
    fun upload(data: ByteArray, remotePath: String): Result<Unit> = try {
        // 先尝试创建父目录（MKCOL），忽略失败（可能已存在）
        ensureParentDirs(remotePath)

        val url = buildUrl(remotePath)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .header("Content-Type", "application/octet-stream")
            .put(data.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful || response.code == 201 || response.code == 204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("上传失败，HTTP ${response.code}"))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** 尝试为远程路径创建父目录（MKCOL），忽略错误。 */
    private fun ensureParentDirs(remotePath: String) {
        val parts = remotePath.trim('/').split("/")
        if (parts.size <= 1) return
        var current = ""
        for (i in 0 until parts.size - 1) {
            current = if (current.isEmpty()) parts[i] else "$current/${parts[i]}"
            try {
                val request = Request.Builder()
                    .url(buildUrl(current + "/"))
                    .header("Authorization", authHeader)
                    .method("MKCOL", null)
                    .build()
                client.newCall(request).execute().use { /* ignore result */ }
            } catch (_: Exception) { /* ignore */ }
        }
    }
}
