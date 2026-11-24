package com.example.engine

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CryptoUtils {
    private val secureRandom = SecureRandom()
    private const val PBKDF2_ITERATIONS = 12_000
    private const val KEY_LENGTH_BITS = 256

    fun hashPassword(password: String): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        val hash = pbkdf2(password.toCharArray(), salt)
        return "${salt.toHex()}:${hash.toHex()}"
    }

    fun verifyPassword(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = parts[0].hexToBytesOrNull() ?: return false
        val expected = parts[1].hexToBytesOrNull() ?: return false
        val actual = pbkdf2(password.toCharArray(), salt)
        return constantTimeEquals(expected, actual)
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.toHex()
    }

    fun hmacSha256Hex(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).toHex()
    }

    fun signWebhookPayload(secret: String, timestampSec: Long, payloadJson: String): String {
        val signedContent = "$timestampSec.$payloadJson"
        val sig = hmacSha256Hex(secret, signedContent)
        return "t=$timestampSec,v1=$sig"
    }

    fun verifyWebhookSignature(secret: String, signatureHeader: String, payloadJson: String): Boolean {
        val parts = signatureHeader.split(",").mapNotNull { segment ->
            val idx = segment.indexOf('=')
            if (idx <= 0) null else segment.substring(0, idx).trim() to segment.substring(idx + 1).trim()
        }.toMap()
        val ts = parts["t"]?.toLongOrNull() ?: return false
        val providedSig = parts["v1"] ?: return false
        val expectedSig = hmacSha256Hex(secret, "$ts.$payloadJson")
        return constantTimeEquals(
            expectedSig.toByteArray(Charsets.UTF_8),
            providedSig.toByteArray(Charsets.UTF_8)
        )
    }

    fun generateSecureToken(byteCount: Int = 24): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return bytes.toHex()
    }

    fun generateAccountNumber(): String {
        val digits = StringBuilder("LL")
        repeat(10) {
            digits.append(secureRandom.nextInt(10))
        }
        return digits.toString()
    }

    fun generatePrefixedId(prefix: String): String {
        val bytes = ByteArray(6)
        secureRandom.nextBytes(bytes)
        return "${prefix}_${bytes.toHex()}"
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { i ->
                substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
