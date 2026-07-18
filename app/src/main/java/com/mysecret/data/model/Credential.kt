package com.mysecret.data.model

import com.google.gson.annotations.SerializedName

/**
 * 单条凭证记录。所有字符串字段均为非空，Gson 反序列化时用默认值兜底。
 */
data class Credential(
    @SerializedName("id")
    val id: String,

    @SerializedName("categoryId")
    var categoryId: String = "",

    @SerializedName("name")
    val name: String,

    @SerializedName("username")
    val username: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("url")
    val url: String,

    @SerializedName("notes")
    val notes: String,

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Gson 反序列化兜底：将 null 字段替换为空字符串，避免 NPE */
        fun safeFrom(src: Credential?): Credential {
            if (src != null) return src
            return Credential(
                id = "",
                categoryId = "",
                name = "",
                username = "",
                password = "",
                url = "",
                notes = ""
            )
        }
    }
}
