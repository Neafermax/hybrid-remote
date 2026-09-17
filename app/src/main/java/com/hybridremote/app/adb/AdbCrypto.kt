package com.hybridremote.app.adb

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * Generación de claves RSA-2048 y codificación de la clave pública en el
 * formato binario propio que usa el protocolo ADB (no es un PEM estándar).
 * Este formato está documentado públicamente en el código fuente de AOSP
 * (system/core/adb/adb_auth.c) y es el mismo que usan apps de "ADB
 * inalámbrico" ya publicadas.
 */
object AdbCrypto {

    private const val KEY_LENGTH_BITS = 2048
    private const val KEY_LENGTH_WORDS = KEY_LENGTH_BITS / 32

    fun generateKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(KEY_LENGTH_BITS)
        return gen.generateKeyPair()
    }

    fun signWithToken(privateKey: RSAPrivateKey, token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        val padded = padPKCS1(token)
        return cipher.doFinal(padded)
    }

    // Padding PKCS#1 v1.5 tipo "01" que exige el protocolo ADB para firmar el token
    private fun padPKCS1(input: ByteArray): ByteArray {
        val sha1Prefix = byteArrayOf(
            0x00, 0x30.toByte(), 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        )
        // El token que envía el ADB del TV ya es el hash SHA-1 de 20 bytes de un desafío
        val block = ByteArray(256)
        block[0] = 0x00; block[1] = 0x01
        var i = 2
        while (i < 256 - sha1Prefix.size - input.size - 1) {
            block[i] = 0xFF.toByte(); i++
        }
        block[i++] = 0x00
        sha1Prefix.copyInto(block, i); i += sha1Prefix.size
        input.copyInto(block, i)
        return block
    }

    /** Convierte la clave pública RSA al formato binario ADB (RSAPublicKey struct) + base64 + " user@hybridremote" */
    fun encodePublicKeyAdb(publicKey: RSAPublicKey): String {
        val n = publicKey.modulus
        val e = publicKey.publicExponent

        val r32 = BigInteger.ONE.shiftLeft(32)
        var n0inv = n.modInverse(r32)
        n0inv = r32.subtract(n0inv)

        val r = BigInteger.ONE.shiftLeft(KEY_LENGTH_BITS)
        val rr = r.multiply(r).mod(n)

        val buffer = java.nio.ByteBuffer.allocate(4 + 4 + KEY_LENGTH_WORDS * 4 * 2 + 4)
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(KEY_LENGTH_WORDS)
        buffer.putInt(n0inv.toInt())
        putBigIntegerLE(buffer, n, KEY_LENGTH_WORDS)
        putBigIntegerLE(buffer, rr, KEY_LENGTH_WORDS)
        buffer.putInt(e.toInt())

        val base64Key = Base64.getEncoder().encodeToString(buffer.array())
        return "$base64Key hybridremote@app"
    }

    private fun putBigIntegerLE(buffer: java.nio.ByteBuffer, value: BigInteger, words: Int) {
        var v = value
        val mask = BigInteger.valueOf(0xFFFFFFFFL)
        for (i in 0 until words) {
            val word = v.and(mask).toLong().toInt()
            buffer.putInt(word)
            v = v.shiftRight(32)
        }
    }
}
