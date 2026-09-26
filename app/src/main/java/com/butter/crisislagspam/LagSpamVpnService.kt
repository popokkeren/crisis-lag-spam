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
import kotlin.random.Random

class LagSpamVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val spamExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    private val spamActive = AtomicBoolean(false)
    private val intervalMs = AtomicInteger(80)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_TOGGLE_SPAM -> {
                val newState = !spamActive.get()
                spamActive.set(newState)
                if (newState) startSpamLoop()
            }
            ACTION_SET_INTERVAL -> {
                val ms = intent.getIntExtra(EXTRA_INTERVAL, 80)
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
            .setContentText("Ghost Mode active")
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

        // Thread baca paket dari tun0
        // LAG TIPIS: delay 200ms + drop 5% — musuh susah hit, lo tetep konek
        executor.execute {
            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val outBuffer = ByteArray(32767)
            while (running) {
                try {
                    val length = input.read(outBuffer)
                    if (length <= 0) continue

                    if (spamActive.get()) {
                        // Delay 200ms — bikin posisi lo "stutter" di server
                        Thread.sleep(200)
                        // Drop 5% aja — biar server ga flag "disconnect"
                        if (Random.nextInt(100) < 5) continue
                    }
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
                    val burst = Random.nextInt(3, 8)
                    for (i in 0 until burst) {
                        spamServer()
                    }
                    val baseInterval = intervalMs.get()
                    val jitter = Random.nextInt(-20, 20)
                    val actualInterval = (baseInterval + jitter).coerceAtLeast(50)
                    Thread.sleep(actualInterval.toLong())
                } catch (_: Exception) {}
            }
        }
    }

    private fun spamServer() {
        try {
            val socket = DatagramSocket()
            socket.broadcast = true
            val size = Random.nextInt(256, 1024)
            val junk = ByteArray(size) { (it % 256).toByte() }

            val allTargets = listOf(
                "8.8.8.8", "1.1.1.1", "8.8.4.4", "1.0.0.1"
            )
            val targets = allTargets.shuffled(java.util.Random())
                .take(Random.nextInt(2, 4))

            for (ip in targets) {
                try {
                    val addr = InetAddress.getByName(ip)
                    val allPorts = listOf(80, 443, 53)
                    val ports = allPorts.shuffled(java.util.Random())
                        .take(Random.nextInt(1, 3))
                    for (port in ports) {
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
