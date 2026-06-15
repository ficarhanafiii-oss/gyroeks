package com.gyrooverlay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gyrooverlay.R
import com.gyrooverlay.input.InputInjector
import com.gyrooverlay.overlay.OverlayManager
import com.gyrooverlay.sensor.GyroProcessor

class GyroService : Service(), SensorEventListener {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "gyro_overlay_channel"
        const val NOTIF_ID = 1
    }

    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private lateinit var overlayManager: OverlayManager
    private lateinit var inputInjector: InputInjector
    private lateinit var gyroProcessor: GyroProcessor

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        inputInjector = InputInjector()
        gyroProcessor = GyroProcessor()
        overlayManager = OverlayManager(this, inputInjector, gyroProcessor)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        overlayManager.show()

        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            gyroProcessor.onGyroData(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        sensorManager.unregisterListener(this)
        overlayManager.hide()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gyro Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Gyro overlay service running"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gyro Overlay Active")
            .setContentText("Tap the floating button to configure zones")
            .setSmallIcon(R.drawable.ic_gyro)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
