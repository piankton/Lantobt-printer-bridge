package com.example.printerbridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.concurrent.thread

class PrinterBridgeService : Service() {

    private var isRunning = false
    private var serverSocket: BluetoothServerSocket? = null
    private var targetIp = "192.168.1.200"
    private var targetPort = 9100

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetIp = intent?.getStringExtra("IP") ?: targetIp
        targetPort = intent?.getIntExtra("PORT", 9100) ?: targetPort

        createNotification()
        if (!isRunning) {
            isRunning = true
            thread { listenBluetooth() }
        }
        return START_STICKY
    }

    private fun listenBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord("POS-80", SPP_UUID)
            sendState("พร้อมรับงานจาก Grab...", 0xFF1976D2.toInt())

            while (isRunning) {
                val client = try { serverSocket?.accept() ?: break } catch (_: Exception) { if (!isRunning) break else continue }
                sendState("กำลังพิมพ์ใบเสร็จ...", 0xFF388E3C.toInt())
                relayData(client)
                sendState("พร้อมรับงานจาก Grab...", 0xFF1976D2.toInt())
            }
        } catch (_: Exception) {
            sendState("บลูทูธเกิดข้อผิดพลาด", 0xFFD32F2F.toInt())
        }
    }

    private fun relayData(btSocket: BluetoothSocket) {
        var btIn: InputStream? = null
        var tcpSocket: Socket? = null
        var tcpOut: OutputStream? = null
        try {
            btIn = btSocket.inputStream
            tcpSocket = Socket()
            tcpSocket.connect(InetSocketAddress(targetIp, targetPort), 3000)
            tcpOut = tcpSocket.getOutputStream()

            val buf = ByteArray(2048)
            var n: Int
            while (btIn.read(buf).also { n = it } != -1) {
                tcpOut.write(buf, 0, n)
                tcpOut.flush()
            }
        } catch (_: Exception) {
            sendState("ส่งข้อมูลเข้า LAN ผิดพลาด", 0xFFD32F2F.toInt())
        } finally {
            try { tcpOut?.close() } catch (_: Exception) {}
            try { tcpSocket?.close() } catch (_: Exception) {}
            try { btIn?.close() } catch (_: Exception) {}
            try { btSocket.close() } catch (_: Exception) {}
        }
    }

    private fun sendState(msg: String, color: Int) {
        val intent = Intent("PRINTER_BRIDGE_STATUS").apply {
            putExtra("STATUS_MSG", msg)
            putExtra("STATUS_COLOR", color)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotification() {
        val channelId = "bridge_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Bridge Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BT to LAN Bridge กำลังทำงาน")
            .setContentText("ส่งข้อมูลไปยัง $targetIp:$targetPort")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        startForeground(1, notif)
    }

    override fun onDestroy() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
