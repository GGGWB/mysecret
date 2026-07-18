package com.mysecret.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.mysecret.data.model.Category
import com.mysecret.data.model.Credential
import com.mysecret.data.model.Vault
import com.mysecret.security.CryptoVault
import java.io.File

/**
 * 密码库仓储：负责加密读写本地文件。
 *
 * 文件位置：应用私有目录 files/vault.enc
 * 流程：Vault → JSON → AES-256-GCM 加密 → 写文件
 *
 * 所有方法应在 IO 协程中调用。
 */
class VaultRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val vaultFile: File = File(appContext.filesDir, "vault.enc")
    private val gson = Gson()

    /** 加载并解密密码库。 */
    fun load(masterPassword: CharArray): Vault {
        if (!vaultFile.exists()) return Vault()
        val encrypted = vaultFile.readBytes()
        if (encrypted.isEmpty()) return Vault()
        val json = CryptoVault.decrypt(encrypted, masterPassword).toString(Charsets.UTF_8)
        val vault = gson.fromJson(json, Vault::class.java) ?: Vault()

        // 清理 null 字段：Gson 可能将旧 JSON 中的 null 值直接赋给字段
        cleanVault(vault)
        return vault
    }

    /** 递归清理 Vault 中的 null 字段，防止运行时 NPE */
    private fun cleanVault(vault: Vault) {
        // 清理 entries
        val cleanedEntries = mutableListOf<Credential>()
        for (entry in vault.entries) {
            cleanedEntries.add(cleanCredential(entry))
        }
        vault.entries.clear()
        vault.entries.addAll(cleanedEntries)

        // 清理 categories
        val cleanedCategories = mutableListOf<Category>()
        for (cat in vault.categories) {
            cleanedCategories.add(cleanCategory(cat))
        }
        vault.categories.clear()
        vault.categories.addAll(cleanedCategories)
    }

    private fun cleanCredential(c: Credential): Credential {
        return c.copy(
            categoryId = c.categoryId ?: "",
            name = c.name ?: "",
            username = c.username ?: "",
            password = c.password ?: "",
            url = c.url ?: "",
            notes = c.notes ?: ""
        )
    }

    private fun cleanCategory(cat: Category): Category {
        return cat.copy(
            name = cat.name ?: "未命名"
        )
    }

    /** 加密保存整个密码库。 */
    fun save(vault: Vault, masterPassword: CharArray) {
        val json = gson.toJson(vault)
        val encrypted = CryptoVault.encrypt(json.toByteArray(Charsets.UTF_8), masterPassword)
        // 写入临时文件再重命名，保证原子性
        val tmp = File(vaultFile.parentFile, "vault.enc.tmp")
        tmp.writeBytes(encrypted)
        tmp.renameTo(vaultFile)
    }

    /** 添加或更新一条凭证。 */
    fun upsert(credential: Credential, masterPassword: CharArray) {
        val vault = load(masterPassword)
        val idx = vault.entries.indexOfFirst { it.id == credential.id }
        if (idx >= 0) {
            vault.entries[idx] = credential.copy(updatedAt = System.currentTimeMillis())
        } else {
            vault.entries.add(credential)
        }
        save(vault, masterPassword)
    }

    /** 删除一条凭证。 */
    fun delete(credentialId: String, masterPassword: CharArray) {
        val vault = load(masterPassword)
        vault.entries.removeAll { it.id == credentialId }
        save(vault, masterPassword)
    }

    fun exists(): Boolean = vaultFile.exists() && vaultFile.length() > 0

    companion object {
        @Volatile private var INSTANCE: VaultRepository? = null

        fun get(context: Context): VaultRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: VaultRepository(context).also { INSTANCE = it }
            }
    }
}
