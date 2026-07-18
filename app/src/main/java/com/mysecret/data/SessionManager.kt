package com.mysecret.data

import com.mysecret.data.model.Credential
import com.mysecret.data.model.Vault
import java.util.concurrent.atomic.AtomicReference

/**
 * 全局会话状态：解锁后持有主密码与已加载的密码库。
 * 应用退出或锁定时调用 [clear] 立即清除内存中的敏感数据。
 *
 * 注意：主密码以 CharArray 持有，使用后立即清零。
 */
object SessionManager {

    @Volatile private var masterPasswordRef: CharArray? = null
    private val vaultRef = AtomicReference<Vault?>(null)
    @Volatile var locked: Boolean = true
        private set

    fun unlock(masterPassword: CharArray, vault: Vault) {
        // 清零旧密码
        masterPasswordRef?.let { it.fill(0.toChar()) }
        // 复制一份持有，调用方可随时清零自己的数组
        masterPasswordRef = masterPassword.copyOf()
        vaultRef.set(vault)
        locked = false
    }

    fun getMasterPassword(): CharArray? = masterPasswordRef

    fun getVault(): Vault? = vaultRef.get()

    fun updateVault(vault: Vault) {
        vaultRef.set(vault)
    }

    fun lock() {
        masterPasswordRef?.let { it.fill(0.toChar()) }
        masterPasswordRef = null
        vaultRef.set(null)
        locked = true
    }

    /** 搜索/排序辅助。 */
    fun currentEntries(): List<Credential> = vaultRef.get()?.entries?.toList() ?: emptyList()
}
