package com.hybridremote.app.adb

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Cliente ADB minimalista sobre TCP (puerto 5555) para controlar un
 * Android TV / TV Box por Wi-Fi sin depender del binario adb ni de Termux.
 *
 * IMPORTANTE: la primera vez, el TV mostrará un diálogo pidiendo autorizar
 * esta conexión ("¿Permitir depuración USB desde esta computadora?").
 * Esto es una medida de seguridad de Android y ninguna app puede evitarlo:
 * el usuario debe aceptarlo una sola vez en su TV.
 *
 * Requisito en el TV: Ajustes > Preferencias del dispositivo > Información
 * > compilación (pulsar 7 veces) para activar Opciones de desarrollador,
 * luego activar "Depuración de red/ADB por Wi-Fi".
 */
class AdbClient(private val host: String, private val port: Int = 5555) {

    companion object {
        private const val A_CNXN = 0x4e584e43
        private const val A_AUTH = 0x48545541
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_CLSE = 0x45534c43
        private const val A_WRTE = 0x45545257
        private const val ADB_VERSION = 0x01000000
        private const val MAX_PAYLOAD = 256 * 1024
        private const val AUTH_TYPE_TOKEN = 1
        private const val AUTH_TYPE_SIGNATURE = 2
        private const val AUTH_TYPE_RSA_PUBLIC = 3
    }

    private var socket: Socket? = null
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private var localId = 1

    /** Conecta y autentica contra el dispositivo. Lanza excepción si falla. */
    fun connect(keyPair: KeyPair) {
        socket = Socket(host, port).apply { soTimeout = 8000 }
        input = DataInputStream(socket!!.getInputStream())
        output = DataOutputStream(socket!!.getOutputStream())

        sendMessage(A_CNXN, ADB_VERSION, MAX_PAYLOAD, "host::hybridremote\u0000".toByteArray())

        var authAttempted = false
        while (true) {
            val msg = readMessage() ?: throw java.io.IOException("Conexión cerrada por el dispositivo")
            when (msg.command) {
                A_CNXN -> return // conectado y autenticado
                A_AUTH -> {
                    if (msg.arg0 == AUTH_TYPE_TOKEN) {
                        if (!authAttempted) {
                            // 1er intento: firmamos el token con la clave privada
                            val signature = AdbCrypto.signWithToken(
                                keyPair.private as RSAPrivateKey, msg.payload
                            )
                            sendMessage(A_AUTH, AUTH_TYPE_SIGNATURE, 0, signature)
                            authAttempted = true
                        } else {
                            // 2do intento: mandamos la clave pública (dispara el diálogo en el TV)
                            val pubKeyStr = AdbCrypto.encodePublicKeyAdb(keyPair.public as RSAPublicKey)
                            sendMessage(A_AUTH, AUTH_TYPE_RSA_PUBLIC, 0, pubKeyStr.toByteArray() + byteArrayOf(0))
                        }
                    }
                }
                else -> { /* ignorar */ }
            }
        }
    }

    /** Ejecuta un comando shell en el TV, ej: "input keyevent 20" */
    fun shell(command: String) {
        val id = localId++
        sendMessage(A_OPEN, id, 0, "shell:$command\u0000".toByteArray())
        // Esperamos OKAY/CLSE de confirmación, sin bloquear demasiado
        try {
            while (true) {
                val msg = readMessage() ?: break
                if (msg.command == A_CLSE || msg.command == A_OKAY) break
            }
        } catch (_: Exception) { /* timeout aceptable, el comando ya se envió */ }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
    }

    // ---- Bajo nivel: framing de mensajes ADB ----

    private data class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

    private fun sendMessage(command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        var checksum = 0
        for (b in payload) checksum += (b.toInt() and 0xFF)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(payload.size)
        header.putInt(checksum)
        header.putInt(command.inv())
        output.write(header.array())
        if (payload.isNotEmpty()) output.write(payload)
        output.flush()
    }

    private fun readMessage(): AdbMessage? {
        val headerBytes = ByteArray(24)
        input.readFully(headerBytes)
        val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val length = buf.int
        buf.int // checksum, ignorado
        buf.int // magic, ignorado
        val payload = if (length > 0) {
            val p = ByteArray(length)
            input.readFully(p)
            p
        } else ByteArray(0)
        return AdbMessage(command, arg0, arg1, payload)
    }

    /** Códigos de tecla Android más usados para el mando */
    object KeyEvent {
        const val UP = 19
        const val DOWN = 20
        const val LEFT = 21
        const val RIGHT = 22
        const val OK = 23        // DPAD_CENTER
        const val HOME = 3
        const val BACK = 4
        const val POWER = 26
        const val VOLUME_UP = 24
        const val VOLUME_DOWN = 25
        const val MUTE = 164
    }
}
