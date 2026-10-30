package com.weenas.castbay.service

import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * A tiny ADB client that talks to this TV's own debugging service (127.0.0.1:5555), so the Health screen can
 * tidy the system without a computer. Same protocol and key layout as TV Doctor's pyadb. The key is made once
 * and kept in the app's private folder; the TV asks "Allow debugging?" the first time, and only the person at
 * the TV can say yes.
 */
class SelfAdb private constructor(private val socket: Socket) : AutoCloseable {
    private var nextId = 1

    private fun send(cmd: Int, a0: Int, a1: Int, data: ByteArray = ByteArray(0)) {
        var sum = 0L
        for (b in data) sum += (b.toInt() and 0xff)
        val head = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(cmd).putInt(a0).putInt(a1).putInt(data.size).putInt((sum and 0xffffffffL).toInt()).putInt(cmd.inv())
        val out = socket.getOutputStream()
        out.write(head.array()); if (data.isNotEmpty()) out.write(data); out.flush()
    }

    private class Msg(val cmd: Int, val a0: Int, val a1: Int, val data: ByteArray)

    private fun readFully(n: Int): ByteArray {
        val buf = ByteArray(n); var got = 0
        val input = socket.getInputStream()
        while (got < n) {
            val r = input.read(buf, got, n - got)
            if (r < 0) throw java.io.IOException("closed")
            got += r
        }
        return buf
    }

    private fun recv(): Msg {
        val h = ByteBuffer.wrap(readFully(24)).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = h.int; val a0 = h.int; val a1 = h.int; val len = h.int; h.int; val magic = h.int
        if (magic != cmd.inv()) throw java.io.IOException("bad message")
        return Msg(cmd, a0, a1, if (len > 0) readFully(len) else ByteArray(0))
    }

    /** Runs one shell command and returns what it printed. */
    fun shell(command: String): String {
        val id = nextId++
        send(A_OPEN, id, 0, ("shell:$command\u0000").toByteArray())
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val m = recv()
            when (m.cmd) {
                A_WRTE -> { out.write(m.data); send(A_OKAY, id, m.a0) }
                A_CLSE -> { return out.toString("UTF-8").replace("\r\n", "\n").trim() }
            }
        }
    }

    override fun close() { runCatching { socket.close() } }

    companion object {
        private const val A_CNXN = 0x4e584e43
        private const val A_AUTH = 0x48545541
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_WRTE = 0x45545257
        private const val A_CLSE = 0x45534c43
        private val SHA1_PREFIX = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)

        /**
         * Connects and authenticates. [waitSeconds] is how long to wait for the person at the TV to press
         * Allow the first time. Throws ConnectException when ADB debugging is off.
         */
        fun open(keyFile: File, waitSeconds: Int = 60): SelfAdb {
            val key = loadKey(keyFile)
            val socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", 5555), 3000)
            socket.soTimeout = waitSeconds * 1000
            val adb = SelfAdb(socket)
            try {
                adb.send(A_CNXN, 0x01000000, 4096, "host::\u0000".toByteArray())
                var signed = false
                while (true) {
                    val m = adb.recv()
                    if (m.cmd == A_CNXN) { socket.soTimeout = 20000; return adb }
                    if (m.cmd != A_AUTH || m.a0 != 1) throw java.io.IOException("unexpected reply")
                    if (!signed) {
                        val sig = Signature.getInstance("NONEwithRSA")
                        sig.initSign(key); sig.update(SHA1_PREFIX + m.data)
                        adb.send(A_AUTH, 2, 0, sig.sign()); signed = true
                    } else {
                        adb.send(A_AUTH, 3, 0, publicKey((key as RSAPrivateKey).modulus))
                    }
                }
            } catch (e: Exception) { adb.close(); throw e }
        }

        private fun loadKey(file: File): PrivateKey {
            val kf = KeyFactory.getInstance("RSA")
            if (file.exists()) return kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(file.readText().trim(), Base64.NO_WRAP)))
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            file.writeText(Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP))
            return pair.private
        }

        private fun le(v: BigInteger, size: Int): ByteArray {
            val be = v.toByteArray()
            val out = ByteArray(size)
            for (i in 0 until size) { val j = be.size - 1 - i; out[i] = if (j >= 0) be[j] else 0 }
            return out
        }

        /** The key in Android's own layout: length, -1/n[0], modulus, R^2 mod n, exponent, little endian. */
        private fun publicKey(n: BigInteger): ByteArray {
            val two32 = BigInteger.ONE.shiftLeft(32)
            val n0inv = n.modInverse(two32).negate().mod(two32).toLong()
            val rr = BigInteger.ONE.shiftLeft(4096).mod(n)
            val head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(64).putInt(n0inv.toInt()).array()
            val exp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(65537).array()
            val raw = head + le(n, 256) + le(rr, 256) + exp
            return Base64.encode(raw, Base64.NO_WRAP) + " ambientcast@tv\u0000".toByteArray()
        }
    }
}
