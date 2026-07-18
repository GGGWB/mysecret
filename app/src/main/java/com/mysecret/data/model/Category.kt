package com.mysecret.data.model

import com.google.gson.annotations.SerializedName

/**
 * 分类。
 * 每个分类有一个唯一的 ID 和名称，密码记录归属到某个分类。
 */
data class Category(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    var name: String,

    @SerializedName("color")
    val color: Int = 0xFF6750A4.toInt(),  // 默认紫色

    @SerializedName("sortOrder")
    val sortOrder: Int = 0
)
