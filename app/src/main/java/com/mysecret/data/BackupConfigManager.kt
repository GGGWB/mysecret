package com.mysecret.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mysecret.data.model.BackupConfig
import com.mysecret.data.model.BackupType

/**
 * 备份配置存储：使用 EncryptedSharedPreferences 加密保存远程服务器凭据。
 * 即使设备被 root，配置中的密码也不会以明文出现在 shared_prefs 目录。
 */
object BackupConfigManager {

    private const val PREFS_NAME = "mysecret_backup_prefs"
    private const val KEY_TYPE = "backup_type"
    private const val KEY_SERVER = "backup_server"
    private const val KEY_USERNAME = "backup_username"
    private const val KEY_PASSWORD = "backup_password"
    private const val KEY_REMOTE_PATH = "backup_remote_path"
    private const val KEY_LAST_BACKUP = "backup_last_time"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(context: Context, config: BackupConfig) {
        getPrefs(context).edit()
            .putString(KEY_TYPE, config.type.name)
            .putString(KEY_SERVER, config.server)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_REMOTE_PATH, config.remotePath)
            .putLong(KEY_LAST_BACKUP, config.lastBackupTime)
            .apply()
    }

    fun load(context: Context): BackupConfig {
        val prefs = getPrefs(context)
        val typeName = prefs.getString(KEY_TYPE, BackupType.NONE.name) ?: BackupType.NONE.name
        return BackupConfig(
            type = runCatching { BackupType.valueOf(typeName) }.getOrDefault(BackupType.NONE),
            server = prefs.getString(KEY_SERVER, "") ?: "",
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            remotePath = prefs.getString(KEY_REMOTE_PATH, "mysecret_backup.mysecret")
                ?: "mysecret_backup.mysecret",
            lastBackupTime = prefs.getLong(KEY_LAST_BACKUP, 0L)
        )
    }

    fun updateLastBackupTime(context: Context, time: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_BACKUP, time).apply()
    }
}
