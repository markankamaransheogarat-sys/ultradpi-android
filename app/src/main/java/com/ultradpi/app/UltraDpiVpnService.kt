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
    private var tunnelThread: Thread? = null

    companion object {
        const val TAG = "UltraDpiVpn"
        const val ACTION_START = "com.ultradpi.START"
        const val ACTION_STOP = "com.ultradpi.STOP"
        const val CHANNEL_ID = "ultradpi_channel_v4"
        const val NOTIFICATION_ID = 1003
        const val SOCKS_PORT = 10808
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startEngine()
            ACTION_STOP -> stopEngine()
        }
        return START_NOT_STICKY
    }

    private fun startEngine() {
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

            // 1. Запуск нативного ByeDPI прокси
            val args = arrayOf(
                "ciadpi",
                "-i", "127.0.0.1",
                "-p", "$SOCKS_PORT",
                "-s", "1",
                "-q", "1",
                "-e", "2",
                "-o", "1+s",
                "--fake", "-1",
                "--ttl", "3"
            )

            proxyThread = Thread {
                try {
                    val code = byeDpiProxy.startProxyWithArgs(args)
                    Log.i(TAG, "ByeDPI stopped with code $code")
                } catch (e: Throwable) {
                    Log.e(TAG, "Error in ByeDPI thread", e)
                }
            }.apply {
                name = "ByeDpiWorker"
                start()
            }

            // Небольшая пауза для готовности сокета 127.0.0.1:10808
            Thread.sleep(150)

            // 2. Создание VPN сетевого интерфейса
            val builder = Builder()
                .setSession("UltraDPI Engine")
                .addAddress("10.0.2.15", 24)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setBlocking(false)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "VPN builder returned null")
                stopEngine()
                return
            }

            // 3. Конфиг маршрутизации TUN -> SOCKS5
            val configFile = File(cacheDir, "tunnel.yaml")
            configFile.writeText(
                """
                tunnel:
                  mtu: 1500
                socks5:
                  port: $SOCKS_PORT
                  address: 127.0.0.1
                misc:
                  log-level: warn
                """.trimIndent()
            )

            // 4. Запуск перенаправителя пакетов hev-socks5-tunnel
            tunnelThread = Thread {
                try {
                    TProxyService.TProxyStartService(configFile.absolutePath, vpnInterface!!.fd)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error in TProxy thread", e)
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
            .setContentTitle("UltraDPI — Обход активен")
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
