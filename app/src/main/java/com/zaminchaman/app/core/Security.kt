package com.zaminchaman.app.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Passwords are never stored: only a salted PBKDF2-HMAC-SHA256 hash. */
object PasswordHasher {
    const val DEFAULT_ITERATIONS = 120_000

    data class Hashed(val hash: String, val salt: String, val iterations: Int)

    fun hash(password: String, iterations: Int = DEFAULT_ITERATIONS): Hashed {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = derive(password, salt, iterations)
        return Hashed(
            Base64.getEncoder().encodeToString(key),
            Base64.getEncoder().encodeToString(salt),
            iterations
        )
    }

    fun verify(password: String, hash: String, salt: String, iterations: Int): Boolean {
        val expected = runCatching { Base64.getDecoder().decode(hash) }.getOrNull() ?: return false
        val saltBytes = runCatching { Base64.getDecoder().decode(salt) }.getOrNull() ?: return false
        val actual = derive(password, saltBytes, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
