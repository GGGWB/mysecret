package com.mysecret.backup

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.util.Properties

/**
 * SMB / Samba 客户端：支持上传文件和测试连接。
 * 使用 jcifs-ng 库实现 SMB 协议通信。
 *
 * 服务器地址格式：smb://host/share/ 或 smb://host/share/path/
 */
class SmbClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    /** 创建带认证的 CIFS 上下文。 */
    private fun createContext(): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.soTimeout", "30000")
        }
        val bc = BaseContext(PropertyConfiguration(props))
        val auth = NtlmPasswordAuthenticator(username, password)
        return bc.withCredentials(auth)
    }

    /** 规范化 SMB URL。 */
    private fun normalizeUrl(): String {
        var url = serverUrl.trim()
        if (!url.endsWith("/")) url += "/"
        // 如果 URL 中已包含用户名（smb://user:pass@host），移除掉，改用 authenticator
        if (url.contains("@")) {
            val scheme = url.substring(0, url.indexOf("//") + 2)
            val hostPart = url.substring(url.indexOf("@") + 1)
            url = scheme + hostPart
        }
        return url
    }

    /** 测试连接：尝试列出根目录。 */
    fun testConnection(): Result<Unit> = try {
        val ctx = createContext()
        val root = SmbFile(normalizeUrl(), ctx)
        root.exists()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * 上传文件到 SMB 共享。
     * @param data 加密后的字节数据
     * @param remotePath 远程相对路径，如 backup/mysecret.mysecret
     */
    fun upload(data: ByteArray, remotePath: String): Result<Unit> = try {
        val ctx = createContext()
        val baseUrl = normalizeUrl()
        val path = remotePath.trimStart('/')
        val fullUrl = baseUrl + path

        // 确保父目录存在
        ensureParentDirs(baseUrl, path, ctx)

        val remoteFile = SmbFile(fullUrl, ctx)
        remoteFile.getOutputStream().use { output ->
            output.write(data)
            output.flush()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** 递归创建父目录。 */
    private fun ensureParentDirs(baseUrl: String, remotePath: String, ctx: CIFSContext) {
        val parts = remotePath.trim('/').split("/")
        if (parts.size <= 1) return
        var current = baseUrl
        for (i in 0 until parts.size - 1) {
            current += parts[i] + "/"
            try {
                val dir = SmbFile(current, ctx)
                if (!dir.exists()) dir.mkdirs()
            } catch (_: Exception) { /* ignore */ }
        }
    }
}
