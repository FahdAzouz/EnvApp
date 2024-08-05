package com.example.envapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*

class SimulationService : Service() {

    private lateinit var resourceSimulator: ResourceSimulator
    private val channelId = "SimulationServiceChannel"
    private val notificationId = 1
    private var lastNotificationUpdate = 0L

    companion object {
        const val ACTION_USAGE_UPDATE = "com.example.envapp.ACTION_USAGE_UPDATE"
        const val EXTRA_CPU_USAGE = "extra_cpu_usage"
        const val EXTRA_RAM_USAGE = "extra_ram_usage"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        resourceSimulator = ResourceSimulator(
            onCpuUsageChanged = { cpu -> broadcastUpdate(cpu, null) },
            onRamUsageChanged = { ram -> broadcastUpdate(null, ram) }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SimulationService", "onStartCommand called")
        val intensity = intent?.getIntExtra("intensity", 50) ?: 50
        resourceSimulator.setIntensity(intensity)
        resourceSimulator.startSimulation()

        startForeground(notificationId, createNotification(0f, 0f))

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        resourceSimulator.stopSimulation()
        Log.d("SimulationService", "onDestroy called")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Simulation Service Channel"
            val descriptionText = "Channel for Simulation Service"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(cpuUsage: Float, ramUsage: Float): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Resource Simulation")
            .setContentText("CPU: ${cpuUsage.toInt()}%, RAM: ${(ramUsage / 4 * 100).toInt()}%")
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun broadcastUpdate(cpuUsage: Float?, ramUsage: Float?) {
        val intent = Intent(ACTION_USAGE_UPDATE).apply {
            cpuUsage?.let { putExtra(EXTRA_CPU_USAGE, it) }
            ramUsage?.let { putExtra(EXTRA_RAM_USAGE, it) }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("SimulationService", "Broadcast sent: CPU: $cpuUsage, RAM: $ramUsage")

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNotificationUpdate >= 5000) {  // Update every 5 seconds
            updateNotification(cpuUsage ?: resourceSimulator.lastCpuUsage, ramUsage ?: resourceSimulator.lastRamUsage)
            lastNotificationUpdate = currentTime
        }
    }

    private fun updateNotification(cpuUsage: Float, ramUsage: Float) {
        val notification = createNotification(cpuUsage, ramUsage)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
}