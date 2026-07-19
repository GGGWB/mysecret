package com.mysecret.data.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * 整个密码库的根结构。
 */
data class Vault(
    @SerializedName("categories")
    val categories: MutableList<Category> = mutableListOf(),

    @SerializedName("entries")
    val entries: MutableList<Credential> = mutableListOf()
) {
    // ── 分类管理 ──

    fun getCategoryById(id: String): Category? =
        categories.find { it.id == id }

    fun createCategory(name: String, color: Int = 0xFF6750A4.toInt(), emoji: String = "📁"): Category {
        val cat = Category(
            id = UUID.randomUUID().toString(),
            name = name,
            color = color,
            sortOrder = categories.size,
            emoji = emoji
        )
        categories.add(cat)
        return cat
    }

    fun renameCategory(categoryId: String, newName: String) {
        val cat = categories.find { it.id == categoryId }
        cat?.let { it.name = newName }
    }

    /** 更新分类的 emoji 图标 */
    fun updateCategoryEmoji(categoryId: String, newEmoji: String) {
        val cat = categories.find { it.id == categoryId }
        cat?.let { it.emoji = newEmoji }
    }

    fun deleteCategory(categoryId: String) {
        categories.removeAll { it.id == categoryId }
        entries.forEach {
            if (it.categoryId == categoryId) it.categoryId = ""
        }
    }

    /** 获取所有分类及其对应的密码数量。 */
    fun getCategoryStats(): List<Pair<Category, Int>> =
        categories.map { cat -> cat to entries.count { it.categoryId == cat.id } }
            .sortedBy { it.first.sortOrder }

    // ── 列表排序/搜索 ──

    /** 按 name 排序（不区分大小写），返回新列表。 */
    private fun sortedByName(ascending: Boolean = true, list: List<Credential> = entries): List<Credential> {
        val sorted = list.sortedBy { it.name.lowercase() }
        return if (ascending) sorted else sorted.asReversed()
    }

    /** 按更新时间倒序。 */
    fun sortedByUpdated(): List<Credential> =
        entries.sortedByDescending { it.updatedAt }

    /** 按分类分组。 */
    fun groupedByCategory(): Map<String?, List<Credential>> {
        val grouped = entries.groupBy { it.categoryId }
        return grouped.toSortedMap { a, b ->
            when {
                a == null && b == null -> 0
                a == null -> -1
                b == null -> 1
                else -> {
                    val ca = categories.find { it.id == a }
                    val cb = categories.find { it.id == b }
                    (ca?.sortOrder ?: 0) - (cb?.sortOrder ?: 0)
                }
            }
        }
    }

    /** 搜索（名称/用户名/密码/网址/备注，不区分大小写）。 */
    fun search(query: String): List<Credential> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return sortedByName(true)
        return entries.filter {
            it.name.lowercase().contains(q) ||
                    it.username.lowercase().contains(q) ||
                    it.password.lowercase().contains(q) ||
                    it.url.lowercase().contains(q) ||
                    it.notes.lowercase().contains(q)
        }.sortedBy { it.name.lowercase() }
    }

    /** 按分类 ID 筛选。categoryId 为空字符串表示"全部"。 */
    fun filteredByCategory(filterCatId: String): List<Credential> {
        val result = if (filterCatId.isBlank()) {
            entries
        } else {
            entries.filter { it.categoryId == filterCatId }
        }
        return result.sortedBy { it.name.lowercase() }
    }

    /** 按分类 ID 筛选 + 搜索。filterCatId 为空字符串表示"全部"。 */
    fun filteredByCategoryAndSearch(filterCatId: String, query: String): List<Credential> {
        val q = query.trim().lowercase()

        // 第一步：按分类筛选
        val baseList = if (filterCatId.isBlank()) {
            entries
        } else {
            entries.filter { it.categoryId == filterCatId }
        }

        // 第二步：按搜索词过滤
        val filtered = if (q.isEmpty()) {
            baseList
        } else {
            baseList.filter {
                it.name.lowercase().contains(q) ||
                        it.username.lowercase().contains(q) ||
                        it.password.lowercase().contains(q) ||
                        it.url.lowercase().contains(q) ||
                        it.notes.lowercase().contains(q)
            }
        }

        // 第三步：排序
        return filtered.sortedBy { it.name.lowercase() }
    }
}
