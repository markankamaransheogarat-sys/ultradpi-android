package io.github.dovecoteescapee.byedpi.core

object ByeDpiProxy {
    init {
        System.loadLibrary("byedpi")
    }

    @JvmStatic
    external fun jniCreateSocketWithCommandLine(args: Array<String>): Int

    @JvmStatic
    external fun jniStart(fd: Int)

    @JvmStatic
    external fun jniStop()
}
