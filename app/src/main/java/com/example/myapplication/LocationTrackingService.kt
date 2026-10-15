package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class LocationTrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var socket: Socket? = null
    private var currentOrderId: String? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        try {
            // Conectando ao backend via WebSocket (Socket.io) no servidor físico (Porta 3333)
            socket = IO.socket("http://192.168.15.52:3333")
            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    currentOrderId?.let { orderId ->
                        try {
                            val data = JSONObject()
                            data.put("orderId", orderId)
                            data.put("lat", location.latitude)
                            data.put("lng", location.longitude)
                            // Emitindo evento de GPS em tempo real pro servidor Fastify
                            socket?.emit("driver:location_update", data)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentOrderId = intent?.getStringExtra("ORDER_ID")
        startForegroundService()
        requestLocationUpdates()
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "location_tracking_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Rastreio de Entrega", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ponto Doce em Rota")
            .setContentText("Transmitindo localização para o cliente...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
            .setMinUpdateDistanceMeters(6f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        socket?.disconnect()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
