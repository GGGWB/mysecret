package com.mysecret.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密核心：使用 PBKDF2-HMAC-SHA256 从主密码派生密钥，AES-256-GCM 加解密。
 *
 * 文件格式（字节布局）：
 * ┌──────────┬───────────┬────────────┬──────────────────────────┐
 * │ salt(16) │ iv(12)    │ iterations │ ciphertext + GCM tag(16) │
 * │          │           │  (4 bytes) │                          │
 * └──────────┴───────────┴────────────┴──────────────────────────┘
 *
 * 安全要点：
 * 1. 每次加密使用全新随机 salt 和 iv，永不复用。
 * 2. PBKDF2 迭代次数 210_000，抵御暴力破解。
 * 3. GCM 提供机密性 + 完整性认证，篡改即解密失败。
 */
object CryptoVault {

    private const val KEY_LENGTH_BITS = 256
    private const val KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8          // 32
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val ITERATIONS = 210_000
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val SECRET_KEY_ALGORITHM = "PBKDF2WithHmacSHA256"

    private val secureRandom = SecureRandom()

    /**
     * 加密一段明文 [plaintext]，返回可直接写入文件的字节数组。
     */
    fun encrypt(plaintext: ByteArray, masterPassword: CharArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }
        val key = deriveKey(masterPassword, salt, ITERATIONS)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        // 组装：salt | iv | iterations | ciphertext
        return ByteArray(SALT_LENGTH + IV_LENGTH + 4 + ciphertext.size).also { out ->
            var off = 0
            System.arraycopy(salt, 0, out, off, SALT_LENGTH); off += SALT_LENGTH
            System.arraycopy(iv, 0, out, off, IV_LENGTH); off += IV_LENGTH
            writeInt(out, off, ITERATIONS); off += 4
            System.arraycopy(ciphertext, 0, out, off, ciphertext.size)
        }
    }

    /**
     * 解密由 [encrypt] 产生的 [encrypted] 字节数组。
     * 主密码错误或数据被篡改时抛出 [SecurityException]。
     */
    fun decrypt(encrypted: ByteArray, masterPassword: CharArray): ByteArray {
        require(encrypted.size > SALT_LENGTH + IV_LENGTH + 4) {
            "加密文件已损坏"
        }
        var off = 0
        val salt = encrypted.copyOfRange(off, off + SALT_LENGTH); off += SALT_LENGTH
        val iv = encrypted.copyOfRange(off, off + IV_LENGTH); off += IV_LENGTH
        val iterations = readInt(encrypted, off); off += 4
        val ciphertext = encrypted.copyOfRange(off, encrypted.size)

        val key = deriveKey(masterPassword, salt, iterations)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * 从主密码派生 AES 密钥。使用 PBKDF2-HMAC-SHA256。
     */
    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(SECRET_KEY_ALGORITHM)
        val rawKey = factory.generateSecret(spec).encoded
        return SecretKeySpec(rawKey, KEY_ALGORITHM)
    }

    /** 生成随机验证令牌（用于首次设置主密码时写入校验值）。 */
    fun generateVerificationToken(masterPassword: CharArray): ByteArray =
        encrypt(VERIFICATION_PLAINTEXT.toByteArray(Charsets.UTF_8), masterPassword)

    /** 用 [masterPassword] 尝试解密验证令牌，成功则说明密码正确。 */
    fun verify(masterPassword: CharArray, token: ByteArray): Boolean = try {
        val decrypted = decrypt(token, masterPassword)
        decrypted.contentEquals(VERIFICATION_PLAINTEXT.toByteArray(Charsets.UTF_8))
    } catch (e: Exception) {
        false
    }

    private const val VERIFICATION_PLAINTEXT = "MYSECRET_VERIFY_OK"

    private fun writeInt(buf: ByteArray, off: Int, value: Int) {
        buf[off] = (value ushr 24).toByte()
        buf[off + 1] = (value ushr 16).toByte()
        buf[off + 2] = (value ushr 8).toByte()
        buf[off + 3] = value.toByte()
    }

    private fun readInt(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 24) or
                ((buf[off + 1].toInt() and 0xFF) shl 16) or
                ((buf[off + 2].toInt() and 0xFF) shl 8) or
                (buf[off + 3].toInt() and 0xFF)
}
