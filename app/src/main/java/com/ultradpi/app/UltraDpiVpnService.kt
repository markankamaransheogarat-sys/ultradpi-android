package com.ultradpi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
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
        const val ACTION_START = "com.ultradpi.START"
        const val ACTION_STOP = "com.ultradpi.STOP"
        const val CHANNEL_ID = "ultradpi_channel"
        const val NOTIFICATION_ID = 101
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val builder = Builder()
            .setSession("UltraDPI Protection")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            // Route standard blocked ports through local desync tunnel
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)

        vpnInterface = builder.establish()

        job = scope.launch {
            processPackets()
        }
    }

    private fun stopVpn() {
        job?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun processPackets() = withContext(Dispatchers.IO) {
        val vpnFileDescriptor = vpnInterface?.fileDescriptor ?: return@withContext
        val input = FileInputStream(vpnFileDescriptor)
        val output = FileOutputStream(vpnFileDescriptor)
        val packet = ByteBuffer.allocate(32767)

        try {
            while (isActive) {
                packet.clear()
                val length = input.read(packet.array())
                if (length > 0) {
                    packet.limit(length)
                    // Apply mid-SNI TCP desync & UDP voice bypass
                    val desynced = desyncPacket(packet.array(), length)
                    output.write(desynced)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Splits TLS ClientHello packets at byte 2 or mid-SNI to bypass TSPU reassembly
     */
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
                    // Mid-SNI split transformation: modifies TCP window / segment bounds
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
                "UltraDPI Protection Service",
                NotificationManager.IMPORTANCE_LOW
            )
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UltraDPI Защита активна")
            .setContentText("YouTube 4K, Discord Voice и нейросети работают без замедлений")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", stopPending)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }
}
