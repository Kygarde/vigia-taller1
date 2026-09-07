package com.vigia

import android.app.*
import android.content.Intent
import android.os.IBinder

class MonitoringService : Service() {

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            "vigia", "Monitoreo VIGÍA", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = Notification.Builder(this, "vigia")
            .setContentTitle("VIGÍA activo")
            .setContentText("Monitoreando contexto del dispositivo")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
