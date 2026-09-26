package com.butter.crisislagspam

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var statusView: TextView
    private lateinit var tvInterval: TextView
    private var spamOn = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        showOverlay()
        return START_STICKY
    }

    private fun startForegroundNotification() {
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
            .setContentText("Overlay active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1002,
                notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1002, notif)
        }
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_menu, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 30
        params.y = 150

        statusView = overlayView.findViewById(R.id.tvLagStatus)
        tvInterval = overlayView.findViewById(R.id.tvInterval)
        val seek = overlayView.findViewById<SeekBar>(R.id.seekInterval)
        val minimize = overlayView.findViewById<TextView>(R.id.tvMinimize)
val close = overlayView.findViewById<TextView>(R.id.tvClose)

        statusView.setOnClickListener {
            spamOn = !spamOn
            updateStatus()
            val i = Intent(this, LagSpamVpnService::class.java).apply {
                action = LagSpamVpnService.ACTION_TOGGLE_SPAM
            }
            startService(i)
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val ms = progress + 50
                tvInterval.text = "Interval (ms): $ms"
                val i = Intent(this@OverlayService, LagSpamVpnService::class.java).apply {
                    action = LagSpamVpnService.ACTION_SET_INTERVAL
                    putExtra(LagSpamVpnService.EXTRA_INTERVAL, ms)
                }
                startService(i)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false

        val header = overlayView.findViewById<TextView>(R.id.tvLagStatus)
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) dragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        spamOn = !spamOn
                        updateStatus()
                        val i = Intent(this, LagSpamVpnService::class.java).apply {
                            action = LagSpamVpnService.ACTION_TOGGLE_SPAM
                        }
                        startService(i)
                    }
                    true
                }
                else -> false
            }
        }

        minimize.setOnClickListener {
            seek.visibility = if (seek.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            tvInterval.visibility = if (tvInterval.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        close.setOnClickListener { stopSelf() }

        windowManager.addView(overlayView, params)
    }

    private fun updateStatus() {
        if (spamOn) {
            statusView.text = "ON"
            statusView.setBackgroundColor(0xFFB71C1C.toInt())
        } else {
            statusView.text = "OFF"
            statusView.setBackgroundColor(0xFF2E7D32.toInt())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            try { windowManager.removeView(overlayView) } catch (_: Exception) {}
        }
        val i = Intent(this, LagSpamVpnService::class.java).apply {
            action = LagSpamVpnService.ACTION_STOP
        }
        startService(i)
    }
}
