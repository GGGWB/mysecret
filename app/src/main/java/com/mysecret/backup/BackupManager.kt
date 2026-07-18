package com.mysecret.backup

import android.content.Context
import android.net.Uri
import com.mysecret.data.BackupConfigManager
import com.mysecret.data.SessionManager
import com.mysecret.data.VaultRepository
import com.mysecret.data.model.BackupConfig
import com.mysecret.data.model.BackupType
import com.mysecret.data.model.Vault
import com.mysecret.security.CryptoVault
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份管理器：负责加密备份的导出、上传和恢复。
 */
class BackupManager(private val context: Context) {

    private val repo = VaultRepository.get(context)
    private val gson = Gson()

    val backupExt = ".mysecret"

    /** 导出加密备份到本地文件。 */
    fun exportLocalBackup(masterPassword: CharArray?): Result<File> {
        return try {
            val pwd = masterPassword ?: return Result.failure(Exception("未解锁，无法导出"))
            val vault = repo.load(pwd)
            val json = gson.toJson(vault)
            val encrypted = CryptoVault.encrypt(json.toByteArray(Charsets.UTF_8), pwd)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(context.filesDir, "backup/mysecret_$timestamp$backupExt")
            backupFile.parentFile?.mkdirs()
            FileOutputStream(backupFile).use { it.write(encrypted) }
            Result.success(backupFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 导出加密备份并上传到远程服务器。 */
    fun backupToRemote(masterPassword: CharArray?): Result<Unit> {
        return try {
            val pwd = masterPassword ?: return Result.failure(Exception("未解锁，无法备份"))
            val config = BackupConfigManager.load(context)
            if (!config.isConfigured()) {
                return Result.failure(Exception(context.getString(com.mysecret.R.string.backup_no_config)))
            }

            val vault = repo.load(pwd)
            val json = gson.toJson(vault)
            val encrypted = CryptoVault.encrypt(json.toByteArray(Charsets.UTF_8), pwd)

            when (config.type) {
                BackupType.WEBDAV -> {
                    val client = WebDavClient(config.server, config.username, config.password)
                    client.upload(encrypted, config.remotePath)
                        .onFailure { throw it }
                }
                BackupType.SMB -> {
                    val client = SmbClient(config.server, config.username, config.password)
                    client.upload(encrypted, config.remotePath)
                        .onFailure { throw it }
                }
                BackupType.NONE -> {
                    throw Exception(context.getString(com.mysecret.R.string.backup_no_config))
                }
            }

            BackupConfigManager.updateLastBackupTime(context, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 测试远程连接。 */
    fun testConnection(config: BackupConfig): Result<Unit> = when (config.type) {
        BackupType.WEBDAV -> WebDavClient(config.server, config.username, config.password).testConnection()
        BackupType.SMB -> SmbClient(config.server, config.username, config.password).testConnection()
        BackupType.NONE -> Result.failure(Exception("未选择远程类型"))
    }

    /** 从备份文件恢复密码库。 */
    fun restoreFromBackup(backupUri: Uri, backupPassword: CharArray, currentPassword: CharArray?): Result<Int> {
        return try {
            val currentPwd = currentPassword ?: return Result.failure(Exception("未解锁，无法恢复"))
            val encrypted = context.contentResolver.openInputStream(backupUri)?.use { it.readBytes() }
                ?: throw Exception("无法读取备份文件")
            val json = CryptoVault.decrypt(encrypted, backupPassword).toString(Charsets.UTF_8)
            val restoredVault = gson.fromJson(json, Vault::class.java)
                ?: throw Exception("备份文件格式错误")

            repo.save(restoredVault, currentPwd)
            val updatedVault = repo.load(currentPwd)
            SessionManager.updateVault(updatedVault)

            Result.success(restoredVault.entries.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
