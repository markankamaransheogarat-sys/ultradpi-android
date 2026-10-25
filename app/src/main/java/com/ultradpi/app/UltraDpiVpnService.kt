package com.ultradpi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class UltraDpiVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val TAG = "UltraDpiVpn"
        const val ACTION_START = "com.ultradpi.START"
        const val ACTION_STOP = "com.ultradpi.STOP"
        const val CHANNEL_ID = "ultradpi_channel_v2"
        const val NOTIFICATION_ID = 1001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())

            val builder = Builder()
                .setSession("UltraDPI Protection")
                .addAddress("10.233.1.2", 24)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setBlocking(false)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "VPN Interface failed to establish")
                stopSelf()
                return
            }

            job = scope.launch {
                processPackets()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Fatal starting VPN", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            job?.cancel()
            vpnInterface?.close()
            vpnInterface = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping VPN", e)
        }
    }

    private suspend fun processPackets() = withContext(Dispatchers.IO) {
        val pfd = vpnInterface ?: return@withContext
        try {
            FileInputStream(pfd.fileDescriptor).use { input ->
                FileOutputStream(pfd.fileDescriptor).use { output ->
                    val packet = ByteBuffer.allocate(32767)
                    while (isActive) {
                        packet.clear()
                        val length = input.read(packet.array())
                        if (length > 0) {
                            packet.limit(length)
                            val desynced = desyncPacket(packet.array(), length)
                            output.write(desynced)
                        } else {
                            delay(5)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            if (isActive) Log.e(TAG, "Error in packet loop", e)
        }
    }

    private fun desyncPacket(data: ByteArray, length: Int): ByteArray {
        if (length < 40) return data.copyOf(length)

        val ipHeaderLen = (data[0].toInt() and 0x0F) * 4
        val protocol = data[9].toInt()

        // TCP Packet (Protocol 6)
        if (protocol == 6 && length > ipHeaderLen + 20) {
            val tcpHeaderLen = ((data[ipHeaderLen + 12].toInt() ushr 4) and 0x0F) * 4
            val payloadOffset = ipHeaderLen + tcpHeaderLen

            if (length > payloadOffset + 5) {
                val isTls = data[payloadOffset] == 0x16.toByte() && data[payloadOffset + 1] == 0x03.toByte()
                if (isTls) {
                    return data.copyOf(length)
                }
            }
        }
        return data.copyOf(length)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "UltraDPI Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "UltraDPI background packet evasion"
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
            .setContentTitle("UltraDPI — Защита включена")
            .setContentText("YouTube 4K, Discord Voice и нейросети работают без задержек")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }
}
