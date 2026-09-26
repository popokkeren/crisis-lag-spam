package com.butter.crisislagspam

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private val OVERLAY_REQ = 101
    private val VPN_REQ = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.btnVpnPerm).setOnClickListener { requestVpn() }
        findViewById<Button>(R.id.btnShowOverlay).setOnClickListener { startOverlay() }
        findViewById<Button>(R.id.btnLaunchGame).setOnClickListener { launchGame() }

        refreshStatus()
    }

    // FIX: refresh status setiap balik ke activity (dari Settings, VPN dialog, dll)
    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val i = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(i, OVERLAY_REQ)
        } else {
            Toast.makeText(this, "Overlay already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQ)
        } else {
            Toast.makeText(this, "VPN already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant overlay first", Toast.LENGTH_SHORT).show()
            return
        }
        val vpnPrep = VpnService.prepare(this)
        if (vpnPrep != null) {
            Toast.makeText(this, "Grant VPN first", Toast.LENGTH_SHORT).show()
            return
        }
        // FIX: overlay service TIDAK start VPN. VPN cuma disiapkan (prepare),
        // di-start eksplisit dari tombol ON di overlay, SETELAH game di lobby.
        val i = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, i)
        } else {
            startService(i)
        }
        Toast.makeText(this, "Overlay shown", Toast.LENGTH_SHORT).show()
    }

    private fun launchGame() {
        val pkg = "com.herogames.gplay.crisisactionsa"
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            startActivity(i)
        } else {
            Toast.makeText(this, "Crisis Action SEA not installed", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
            } catch (_: Exception) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                    )
                )
            }
        }
    }

    private fun refreshStatus() {
        val overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(this)
        val vpnOk = VpnService.prepare(this) == null
        statusText.text = "Overlay: ${if (overlayOk) "OK" else "NO"} | VPN: ${if (vpnOk) "OK" else "NO"}"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        refreshStatus()
    }
}
