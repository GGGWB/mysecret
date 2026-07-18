package com.mysecret.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 封装 SharedPreferences，仅存储非敏感的配置元数据。
 * 验证令牌（验证主密码是否正确）也存储于此，它本身是加密的。
 */
object PrefsManager {

    private const val PREFS_NAME = "mysecret_prefs"
    private const val KEY_VERIFICATION_TOKEN = "verification_token_b64"
    private const val KEY_SORT_MODE = "sort_mode"
    private const val KEY_VAULT_EXISTS = "vault_exists"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 是否已设置过主密码（即密码库已初始化）。 */
    fun isVaultInitialized(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VAULT_EXISTS, false)

    fun setVaultInitialized(context: Context) {
        prefs(context).edit().putBoolean(KEY_VAULT_EXISTS, true).apply()
    }

    fun saveVerificationToken(context: Context, token: ByteArray) {
        prefs(context).edit()
            .putString(KEY_VERIFICATION_TOKEN, android.util.Base64.encodeToString(token, android.util.Base64.NO_WRAP))
            .apply()
    }

    fun getVerificationToken(context: Context): ByteArray? =
        prefs(context).getString(KEY_VERIFICATION_TOKEN, null)
            ?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }

    fun saveSortMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_SORT_MODE, mode).apply()
    }

    fun getSortMode(context: Context): Int =
        prefs(context).getInt(KEY_SORT_MODE, 0) // 0=name asc, 1=name desc, 2=updated desc
}
