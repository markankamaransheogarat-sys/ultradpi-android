package io.github.dovecoteescapee.byedpi.core

import android.util.Log

class ByeDpiProxy {
    companion object {
        init {
            System.loadLibrary("byedpi")
        }
    }

    private var fd: Int = -1

    fun startProxyWithPreset(port: Int, presetArgs: Array<String>): Int {
        if (fd >= 0) {
            return -1
        }
        val fullArgs = arrayOf("ciadpi", "-i", "127.0.0.1", "-p", "$port") + presetArgs
        val socketFd = jniCreateSocketWithCommandLine(fullArgs)
        if (socketFd < 0) {
            Log.e("ByeDpiProxy", "jniCreateSocketWithCommandLine failed with code $socketFd, falling back to clean UI socket")
            return startProxy(port)
        }
        this.fd = socketFd
        return jniStartProxy(socketFd)
    }

    fun startProxy(port: Int): Int {
        if (fd >= 0) {
            return -1
        }
        // Вызов проверенного JNI-метода с проверенными базовыми настройками:
        // desyncHttp = true, desyncHttps = true, desyncUdp = true (с 1 fake UDP для QUIC)
        // desyncMethod = 2 (DESYNC_DISORDER: split pos 1, reverse order)
        val socketFd = jniCreateSocket(
            ip = "127.0.0.1",
            port = port,
            maxConnections = 512,
            bufferSize = 16384,
            defaultTtl = 0,
            customTtl = false,
            noDomain = false,
            desyncHttp = true,
            desyncHttps = true,
            desyncUdp = true,
            desyncMethod = 2,
            splitPosition = 1,
            splitAtHost = false,
            fakeTtl = 8,
            fakeSni = "www.iana.org",
            customOobChar = 'a'.code.toByte(),
            hostMixedCase = false,
            domainMixedCase = false,
            hostRemoveSpaces = false,
            tlsRecordSplit = false,
            tlsRecordSplitPosition = 0,
            tlsRecordSplitAtSni = false,
            hostsMode = 0,
            hosts = null,
            tfo = false,
            udpFakeCount = 1,
            dropSack = false,
            fakeOffset = 0
        )
        if (socketFd < 0) {
            Log.e("ByeDpiProxy", "jniCreateSocket failed with code $socketFd")
            return -1
        }
        this.fd = socketFd
        return jniStartProxy(socketFd)
    }

    fun startProxyWithArgs(args: Array<String>): Int {
        val socketFd = jniCreateSocketWithCommandLine(args)
        if (socketFd < 0) {
            return -1
        }
        this.fd = socketFd
        return jniStartProxy(socketFd)
    }

    fun stopProxy(): Int {
        val currentFd = this.fd
        if (currentFd >= 0) {
            val res = jniStopProxy(currentFd)
            this.fd = -1
            return res
        }
        return 0
    }

    external fun jniCreateSocket(
        ip: String,
        port: Int,
        maxConnections: Int,
        bufferSize: Int,
        defaultTtl: Int,
        customTtl: Boolean,
        noDomain: Boolean,
        desyncHttp: Boolean,
        desyncHttps: Boolean,
        desyncUdp: Boolean,
        desyncMethod: Int,
        splitPosition: Int,
        splitAtHost: Boolean,
        fakeTtl: Int,
        fakeSni: String,
        customOobChar: Byte,
        hostMixedCase: Boolean,
        domainMixedCase: Boolean,
        hostRemoveSpaces: Boolean,
        tlsRecordSplit: Boolean,
        tlsRecordSplitPosition: Int,
        tlsRecordSplitAtSni: Boolean,
        hostsMode: Int,
        hosts: String?,
        tfo: Boolean,
        udpFakeCount: Int,
        dropSack: Boolean,
        fakeOffset: Int
    ): Int

    external fun jniCreateSocketWithCommandLine(args: Array<String>): Int
    external fun jniStartProxy(fd: Int): Int
    external fun jniStopProxy(fd: Int): Int
}
