package io.github.dovecoteescapee.byedpi.core

class ByeDpiProxy {
    companion object {
        init {
            System.loadLibrary("byedpi")
        }
    }

    private var fd: Int = -1

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

    external fun jniCreateSocketWithCommandLine(args: Array<String>): Int
    external fun jniStartProxy(fd: Int): Int
    external fun jniStopProxy(fd: Int): Int
}
