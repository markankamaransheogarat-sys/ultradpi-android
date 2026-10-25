package com.ultradpi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.dovecoteescapee.byedpi.core.ByeDpiProxy
import io.github.dovecoteescapee.byedpi.core.TProxyService
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class UltraDpiVpnService : VpnService() {

    private val byeDpiProxy = ByeDpiProxy()
    private var vpnInterface: ParcelFileDescriptor? = null
    private var proxyThread: Thread? = null
    private var isRunning = false
    private var configFile: File? = null

    companion object {
        const val TAG = "UltraDpiVpn"
        const val ACTION_START = "com.ultradpi.START"
        const val ACTION_STOP = "com.ultradpi.STOP"
        const val EXTRA_PRESET = "com.ultradpi.PRESET_INDEX"
        const val CHANNEL_ID = "ultradpi_channel_v8"
        const val NOTIFICATION_ID = 1008
        const val SOCKS_PORT = 10808
    }

    private var currentPreset = BypassPreset.UNIVERSAL

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val presetIndex = intent.getIntExtra(EXTRA_PRESET, 0)
                startEngine(presetIndex)
            }
            ACTION_STOP -> stopEngine()
        }
        return START_NOT_STICKY
    }

    private fun startEngine(presetIndex: Int) {
        if (isRunning) return
        isRunning = true
        currentPreset = BypassPreset.fromIndex(presetIndex)

        try {
            createNotificationChannel()
            val notification = buildNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.i(TAG, "Starting ByeDPI proxy with preset: ${currentPreset.title}")

            // 1. Запуск нативного ByeDPI Proxy
            proxyThread = Thread {
                try {
                    val code = byeDpiProxy.startProxyWithPreset(SOCKS_PORT, currentPreset.args)
                    Log.i(TAG, "ByeDPI proxy exited with code $code")
                } catch (e: Throwable) {
                    Log.e(TAG, "Error in ByeDPI thread", e)
                }
            }.apply {
                name = "ByeDpiWorker"
                start()
            }

            // 2. Ожидание и проверка доступности SOCKS5 прокси (SOCKS5 handshake)
            var ready = awaitProxy(SOCKS_PORT, 2500)
            if (!ready) {
                Log.w(TAG, "Proxy did not respond to handshake with preset args. Falling back to native socket...")
                byeDpiProxy.stopProxy()
                proxyThread?.interrupt()
                proxyThread = Thread {
                    byeDpiProxy.startProxy(SOCKS_PORT)
                }.apply {
                    name = "ByeDpiFallbackWorker"
                    start()
                }
                ready = awaitProxy(SOCKS_PORT, 2000)
            }

            if (!ready) {
                Log.e(TAG, "Failed to start ByeDPI proxy on port $SOCKS_PORT")
                stopEngine()
                return
            }

            Log.i(TAG, "SOCKS5 proxy verified and listening on port $SOCKS_PORT")

            // 3. Создание системного VPN-интерфейса
            val builder = Builder()
                .setSession("UltraDPI Engine")
                .setConfigureIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .addAddress("10.10.10.10", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("77.88.8.8")
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addDisallowedApplication(applicationContext.packageName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val pfd = builder.establish()
            if (pfd == null) {
                Log.e(TAG, "VPN builder returned null")
                stopEngine()
                return
            }
            this.vpnInterface = pfd

            // 4. Валидная конфигурация tun2socks (hev-socks5-tunnel)
            // Внимание: секция 'tunnel:' в этой версии libhev-socks5-tunnel приводит к ошибке 'Parse failed'.
            // Конфиг должен строго содержать только 'misc' и 'socks5':
            val tun2socksConfig = """
                misc:
                  task-stack-size: 81920
                socks5:
                  mtu: 8500
                  address: 127.0.0.1
                  port: $SOCKS_PORT
                  udp: udp
            """.trimIndent()

            val tempFile = try {
                File.createTempFile("config", "tmp", cacheDir).apply {
                    writeText(tun2socksConfig)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create tun2socks config file", e)
                stopEngine()
                return
            }
            this.configFile = tempFile

            // 5. Запуск туннелирования tun2socks
            // TProxyStartService внутри нативно создает свой рабочий поток
            TProxyService.TProxyStartService(tempFile.absolutePath, pfd.fd)

            Log.i(TAG, "UltraDPI connected and routing all traffic!")
        } catch (e: Throwable) {
            Log.e(TAG, "Fatal starting UltraDPI", e)
            stopEngine()
        }
    }

    private fun awaitProxy(port: Int, timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 200)
                    socket.soTimeout = 200
                    socket.getOutputStream().write(byteArrayOf(5, 1, 0))
                    val input = socket.getInputStream()
                    val b1 = input.read()
                    val b2 = input.read()
                    if (b1 == 5 && b2 == 0) {
                        return true
                    }
                }
            } catch (_: Exception) {
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        return false
    }

    private fun stopEngine() {
        isRunning = false
        try {
            TProxyService.TProxyStopService()
            byeDpiProxy.stopProxy()
            proxyThread?.interrupt()
            proxyThread = null
            vpnInterface?.close()
            vpnInterface = null
            configFile?.delete()
            configFile = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.i(TAG, "UltraDPI stopped cleanly")
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping UltraDPI", e)
        }
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN onRevoke called")
        stopEngine()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "UltraDPI Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "UltraDPI bypass active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, UltraDpiVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPending = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UltraDPI — ${currentPreset.title}")
            .setContentText("YouTube 4K, Discord и сервисы работают на максимальной скорости")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopEngine()
        super.onDestroy()
    }
}
