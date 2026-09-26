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
    private var lastSpamTime = 0L
    private var burstCount = 0

    // Rate limit: max spam dalam 10 detik
    private val maxBurstPerWindow = 15
    private val windowMs = 10_000L

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

        // Thread baca paket dari tun0 — DROP 95% kalau spam ON
        executor.execute {
            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val outBuffer = ByteArray(32767)
            while (running) {
                try {
                    val length = input.read(outBuffer)
                    if (length <= 0) continue

                    if (spamActive.get()) {
                        // Drop 95% — cuma 5% lolos (ghost mode)
                        if (Random.nextInt(100) < 95) continue
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
                    val now = System.currentTimeMillis()

                    // Rate limit — reset window tiap 10 detik
                    if (now - lastSpamTime > windowMs) {
                        lastSpamTime = now
                        burstCount = 0
                    }

                    // Kalau udah lewat batas, jeda dulu
                    if (burstCount >= maxBurstPerWindow) {
                        Thread.sleep(1000)
                        continue
                    }

                    // Random burst count 5-15
                    val burst = Random.nextInt(5, 15)
                    for (i in 0 until burst) {
                        spamServer()
                    }
                    burstCount++

                    // Random interval 50-150ms
                    val baseInterval = intervalMs.get()
                    val jitter = Random.nextInt(-30, 30)
                    val actualInterval = (baseInterval + jitter).coerceAtLeast(30)
                    Thread.sleep(actualInterval.toLong())

                } catch (_: Exception) {}
            }
        }
    }

    private fun spamServer() {
        try {
            val socket = DatagramSocket()
            socket.broadcast = true
            // Random junk size 512-2048 bytes
            val size = Random.nextInt(512, 2048)
            val junk = ByteArray(size) { (it % 256).toByte() }

            // Random target IP dari pool (biar susah di-detect)
            val targets = listOf(
                "8.8.8.8", "1.1.1.1", "8.8.4.4", "1.0.0.1",
                "203.0.113.1", "198.51.100.1", "192.0.2.1",
                "10.8.0.1"
            ).shuffled().take(Random.nextInt(3, 7))

            for (ip in targets) {
                try {
                    val addr = InetAddress.getByName(ip)
                    // Random port
                    val ports = intArrayOf(80, 443, 8080, 53, 123, 9000).shuffled()
                        .take(Random.nextInt(2, 5))
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
