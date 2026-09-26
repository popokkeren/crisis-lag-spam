package com.butter.crisislagspam

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LagSpamVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val spamExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    private val spamActive = AtomicBoolean(false)
    private val intervalMs = AtomicInteger(200)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_TOGGLE_SPAM -> {
                val newState = !spamActive.get()
                spamActive.set(newState)
                if (newState) startSpamLoop()
            }
            ACTION_SET_INTERVAL -> {
                val ms = intent.getIntExtra(EXTRA_INTERVAL, 200)
                intervalMs.set(ms)
            }
            ACTION_STOP -> stopVpn()
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return

        val channelId = "lag_spam_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                channelId,
                "Lag Spam",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Crisis Lag Spam")
            .setContentText("VPN active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }

        val builder = Builder()
            .setSession("CrisisLagSpam")
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("10.8.0.1")
            .setBlocking(true)

        vpnInterface = builder.establish()
        running = true

        executor.execute {
            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val outBuffer = ByteArray(32767)
            while (running) {
                try {
                    val length = input.read(outBuffer)
                    if (length <= 0) continue
                } catch (e: Exception) {
                    break
                }
            }
            try { input.close() } catch (_: Exception) {}
        }
    }

    private fun startSpamLoop() {
        spamExecutor.execute {
            while (spamActive.get() && running) {
                try {
                    spamServer()
                    Thread.sleep(intervalMs.get().toLong())
                } catch (_: Exception) {}
            }
        }
    }

    private fun spamServer() {
        try {
            val socket = DatagramSocket()
            val junk = ByteArray(1024) { (it % 256).toByte() }
            val targets = listOf(
                "8.8.8.8", "1.1.1.1",
                "203.0.113.1", "198.51.100.1"
            )
            for (ip in targets) {
                try {
                    val addr = InetAddress.getByName(ip)
                    for (port in intArrayOf(80, 443, 8080, 9000, 10000)) {
                        val packet = DatagramPacket(junk, junk.size, addr, port)
                        socket.send(packet)
                    }
                } catch (_: Exception) {}
            }
            socket.close()
        } catch (_: Exception) {}
    }

    private fun stopVpn() {
        running = false
        spamActive.set(false)
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    companion object {
        const val ACTION_START = "com.butter.crisislagspam.START"
        const val ACTION_TOGGLE_SPAM = "com.butter.crisislagspam.TOGGLE_SPAM"
        const val ACTION_SET_INTERVAL = "com.butter.crisislagspam.SET_INTERVAL"
        const val ACTION_STOP = "com.butter.crisislagspam.STOP"
        const val EXTRA_INTERVAL = "interval"
        const val NOTIF_ID = 1001
    }
}
