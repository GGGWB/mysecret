package com.mysecret.data.model

import com.google.gson.annotations.SerializedName

/**
 * 远程备份类型。
 */
enum class BackupType {
    @SerializedName("none") NONE,
    @SerializedName("webdav") WEBDAV,
    @SerializedName("smb") SMB
}

/**
 * 远程备份配置。
 * 服务器地址、用户名、密码使用 EncryptedSharedPreferences 加密存储。
 */
data class BackupConfig(
    @SerializedName("type")
    val type: BackupType = BackupType.NONE,

    @SerializedName("server")
    val server: String = "",

    @SerializedName("username")
    val username: String = "",

    @SerializedName("password")
    val password: String = "",

    @SerializedName("remotePath")
    val remotePath: String = "mysecret_backup.mysecret",

    @SerializedName("lastBackupTime")
    val lastBackupTime: Long = 0L
) {
    fun isConfigured(): Boolean = type != BackupType.NONE && server.isNotBlank()
}
