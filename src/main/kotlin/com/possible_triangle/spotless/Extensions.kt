package com.possible_triangle.spotless

import io.ktor.utils.io.core.toByteArray
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun String.sha256(secret: String): String {
    val algorithm = "HmacSHA256"
    val mac = Mac.getInstance(algorithm)
    val secretKeySpec = SecretKeySpec(secret.toByteArray(), algorithm)
    mac.init(secretKeySpec)
    val hash = mac.doFinal(toByteArray())
    return hash.joinToString("") { String.format("%02x", it) }
}