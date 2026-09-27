package com.butter.crisislagspam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.random.Random

class LagVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false
    @Volatile private var enabled = true

    // PACKET LOSS CONFIG
    @Volatile var lossPercent: Int = 20

    // Threshold: paket 250-2000 byte dianggap paket posisi
    // Heartbeat <= 200 byte SELALU lolos
    private val HEARTBEAT_MAX = 200
    private val POSITION_MIN = 250
    private val POSITION_MAX = 2000

    companion object {
        const val ACTION_START = "com.butter.crisislagspam.START"
        const val ACTION_STOP = "com.butter.crisislagspam.STOP"
        const val ACTION_SET_DELAY = "com.butter.crisislagspam.SET_DELAY"
        const val EXTRA_DELAY_MS = "delay_ms"
        const val CHANNEL_ID = "crisis_lag_spam"
        const val NOTIF_ID = 1

        @Volatile var instance: LagVpnService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_SET_DELAY -> {
                val d = intent.getLongExtra(EXTRA_DELAY_MS, 20L)
                lossPercent = d.toInt().coerceIn(1, 50)
                return START_STICKY
            }
            else -> {
                if (!running) {
                    startForegroundNotification()
                    startVpn()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Crisis Lag Spam",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Crisis Lag Spam")
            .setContentText("Packet loss mode active")
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
    }

    private fun startVpn() {
        val builder = Builder()
        builder.setSession("CrisisLagSpam")
        builder.addAddress("10.8.0.2", 32)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("1.1.1.1")
        builder.setBlocking(true)

        vpnInterface = builder.establish() ?: run {
            running = false
            return
        }
        running = true

        thread(name = "loss-loop") {
            val fd = vpnInterface?.fileDescriptor ?: return@thread
            val input = FileInputStream(fd)
            val output = FileOutputStream(fd)
            val buffer = ByteArray(32767)

            while (running) {
                val len = try {
                    input.read(buffer)
                } catch (_: Exception) {
                    -1
                }
                if (len <= 0) continue

                if (enabled && len in POSITION_MIN..POSITION_MAX) {
                    if (Random.nextInt(100) < lossPercent) {
                        continue
                    }
                }

                try {
                    output.write(buffer, 0, len)
                } catch (_: Exception) { }
            }
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
    }

    private fun stopVpn() {
        running = false
        enabled = false
        try { vpnInterface?.close() } catch (_: Exception) { }
        vpnInterface = null
        instance = null
        stopForeground(true)
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
