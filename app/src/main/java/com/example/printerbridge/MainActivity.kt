package com.example.printerbridge

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var etIp: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnTestLan: Button

    private val PREFS = "bridge_prefs"

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("STATUS_MSG") ?: return
            val color = intent.getIntExtra("STATUS_COLOR", 0xFF000000.toInt())
            tvStatus.text = status
            tvStatus.setTextColor(color)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etIp = findViewById(R.id.etPrinterIp)
        etPort = findViewById(R.id.etPrinterPort)
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnTestLan = findViewById(R.id.btnTestLan)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        etIp.setText(prefs.getString("ip", "192.168.1.200"))
        etPort.setText(prefs.getInt("port", 9100).toString())

        checkPermissions()

        btnTestLan.setOnClickListener {
            val ip = etIp.text.toString().trim()
            val port = etPort.text.toString().trim().toIntOrNull() ?: 9100
            thread {
                val ok = try {
                    Socket().use { it.connect(InetSocketAddress(ip, port), 2000); true }
                } catch (_: Exception) { false }
                runOnUiThread {
                    Toast.makeText(this, if (ok) "ต่อ LAN สำเร็จ!" else "ต่อ LAN ไม่ติด", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnStart.setOnClickListener {
            val ip = etIp.text.toString().trim()
            val port = etPort.text.toString().trim().toIntOrNull() ?: 9100
            prefs.edit().putString("ip", ip).putInt("port", port).apply()

            val intent = Intent(this, PrinterBridgeService::class.java).apply {
                putExtra("IP", ip)
                putExtra("PORT", port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            btnStart.isEnabled = false
            btnStop.isEnabled = true
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, PrinterBridgeService::class.java))
            btnStart.isEnabled = true
            btnStop.isEnabled = false
            tvStatus.text = "หยุดการทำงาน (Stopped)"
            tvStatus.setTextColor(0xFFD32F2F.toInt())
        }
    }

    private fun checkPermissions() {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            list.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = list.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("PRINTER_BRIDGE_STATUS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }
}
