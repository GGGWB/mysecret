package com.mysecret.security

import java.security.SecureRandom

/**
 * 随机密码生成器，支持自定义长度与字符集。
 */
object PasswordGenerator {

    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{}|;:,.<>?/~"

    data class Options(
        val length: Int = 16,
        val useUppercase: Boolean = true,
        val useLowercase: Boolean = true,
        val useDigits: Boolean = true,
        val useSymbols: Boolean = true
    )

    fun generate(options: Options = Options()): String {
        val pool = buildString {
            if (options.useUppercase) append(UPPER)
            if (options.useLowercase) append(LOWER)
            if (options.useDigits) append(DIGITS)
            if (options.useSymbols) append(SYMBOLS)
        }.toList()

        require(pool.isNotEmpty()) { "至少需要选择一种字符类型" }

        val random = SecureRandom()
        return buildString {
            repeat(options.length) {
                append(pool[random.nextInt(pool.size)])
            }
        }
    }
}
