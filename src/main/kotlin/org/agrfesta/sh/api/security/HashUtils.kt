package org.agrfesta.sh.api.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object HashUtils {
    fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
