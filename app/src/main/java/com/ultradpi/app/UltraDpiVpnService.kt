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

class UltraDpiVpnService : VpnService() {

    private val byeDpiProxy = ByeDpiProxy()
    private var vpnInterface: ParcelFileDescriptor? = null
    private var proxyThread: Thread? = null
    private var isRunning = false

    companion object {
        const val TAG = "UltraDpiVpn"
        const val ACTION_START = "com.ultradpi.START"
        const val ACTION_STOP = "com.ultradpi.STOP"
        const val EXTRA_PRESET = "com.ultradpi.PRESET_INDEX"
        const val CHANNEL_ID = "ultradpi_channel_v7"
        const val NOTIFICATION_ID = 1007
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

            Log.i(TAG, "Starting ByeDPI with preset: ${currentPreset.title}")

            // 1. Запуск нативного ByeDPI Proxy с выбранным пресетом обхода
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

            Thread.sleep(250)

            // 2. Создание системного VPN-туннеля
            // Включаем Yandex DNS (77.88.8.8) как основной быстрый и никогда не блокируемый в РФ DNS,
            // а также Cloudflare (1.1.1.1) и Google (8.8.8.8) в качестве резерва.
            val builder = Builder()
                .setSession("UltraDPI Engine")
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

            // 3. Конфигурация tun2socks (hev-socks5-tunnel)
            val tun2socksConfig = """
                tunnel:
                  mtu: 8500
                misc:
                  task-stack-size: 81920
                socks5:
                  mtu: 8500
                  address: 127.0.0.1
                  port: $SOCKS_PORT
                  udp: udp
            """.trimIndent()

            val configFile = File(cacheDir, "tunnel.yaml")
            configFile.writeText(tun2socksConfig)

            // 4. Запуск hev-socks5-tunnel
            Thread {
                try {
                    TProxyService.TProxyStartService(configFile.absolutePath, pfd.fd)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error in TProxyService", e)
                }
            }.apply {
                name = "HevTunnelWorker"
                start()
            }

            Log.i(TAG, "UltraDPI connected successfully!")
        } catch (e: Throwable) {
            Log.e(TAG, "Fatal starting UltraDPI", e)
            stopEngine()
        }
    }

    private fun stopEngine() {
        isRunning = false
        try {
            TProxyService.TProxyStopService()
            byeDpiProxy.stopProxy()
            vpnInterface?.close()
            vpnInterface = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping UltraDPI", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "UltraDPI Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "UltraDPI 1-Click bypass active"
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
            .setContentText("YouTube 4K, Discord и нейросети работают на полной скорости")
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
