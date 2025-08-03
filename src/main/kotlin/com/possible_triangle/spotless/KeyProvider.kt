package com.possible_triangle.spotless

import com.auth0.jwt.interfaces.RSAKeyProvider
import io.ktor.server.application.*
import io.ktor.util.*
import java.io.File
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec

fun Application.createKeyProvider(): RSAKeyProvider {
    val privateKeyFile = File("private-key.pem")
    if (!privateKeyFile.exists()) error("${privateKeyFile.absolutePath} missing")

    val base64 = privateKeyFile
        .readText()
        .replace("-----.+-----".toRegex(), "")
        .lines()
        .joinToString("")

    val keyFactory = KeyFactory.getInstance("RSA")
    val keySpec = PKCS8EncodedKeySpec(base64.decodeBase64Bytes())

    val privateKey = keyFactory.generatePrivate(keySpec) as RSAPrivateKey

    return object : RSAKeyProvider {
        override fun getPublicKeyById(id: String) = null
        override fun getPrivateKeyId(): String? = null
        override fun getPrivateKey() = privateKey
    }
}