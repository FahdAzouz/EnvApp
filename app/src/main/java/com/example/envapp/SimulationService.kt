package com.example.envapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class SimulationService : Service() {

    private lateinit var resourceSimulator: ResourceSimulator
    private val channelId = "SimulationServiceChannel"
    private val notificationId = 1
    private var lastNotificationUpdate = 0L
    private var isSimulating = false

    companion object {
        const val ACTION_USAGE_UPDATE = "com.example.envapp.ACTION_USAGE_UPDATE"
        const val EXTRA_CPU_USAGE = "extra_cpu_usage"
        const val EXTRA_RAM_USAGE = "extra_ram_usage"
        const val ACTION_START_SIMULATION = "com.example.envapp.ACTION_START_SIMULATION"
        const val ACTION_STOP_SIMULATION = "com.example.envapp.ACTION_STOP_SIMULATION"
        const val ACTION_UPDATE_INTENSITY = "com.example.envapp.ACTION_UPDATE_INTENSITY"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        resourceSimulator = ResourceSimulator(this) { cpu, ram ->
            broadcastUpdate(cpu, ram)
        }
        resourceSimulator.startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SimulationService", "onStartCommand called")
        when (intent?.action) {
            ACTION_START_SIMULATION -> {
                val intensity = intent.getIntExtra("intensity", 50)
                resourceSimulator.setIntensity(intensity)
                resourceSimulator.startSimulation()
                Log.d("SimulationService", "Starting simulation with intensity: $intensity")
            }
            ACTION_STOP_SIMULATION -> {
                resourceSimulator.stopSimulation()
                Log.d("SimulationService", "Stopping simulation")
            }
            ACTION_UPDATE_INTENSITY -> {
                val intensity = intent.getIntExtra("intensity", 50)
                resourceSimulator.setIntensity(intensity)
                Log.d("SimulationService", "Updating intensity to: $intensity")
            }
        }
        startForeground(notificationId, createNotification(0f, 0f))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        resourceSimulator.stopSimulation()
        Log.d("SimulationService", "onDestroy called")
    }

    private fun createNotification(cpuUsage: Float, ramUsage: Float): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val content = if (isSimulating) {
            "Simulating - CPU: ${cpuUsage.toInt()}%, RAM: ${ramUsage.toInt()}%"
        } else {
            "Monitoring - CPU: ${cpuUsage.toInt()}%, RAM: ${ramUsage.toInt()}%"
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Resource Usage")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Simulation Service Channel"
            val descriptionText = "Channel for Simulation Service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun broadcastUpdate(cpuUsage: Float, ramUsage: Float) {
        val intent = Intent(ACTION_USAGE_UPDATE).apply {
            putExtra(EXTRA_CPU_USAGE, cpuUsage)
            putExtra(EXTRA_RAM_USAGE, ramUsage)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("SimulationService", "Broadcast sent: CPU: $cpuUsage, RAM: $ramUsage")

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNotificationUpdate >= 5000) {  // Update every 5 seconds
            updateNotification(cpuUsage, ramUsage)
            lastNotificationUpdate = currentTime
        }
    }

    private fun updateNotification(cpuUsage: Float, ramUsage: Float) {
        val notification = createNotification(cpuUsage, ramUsage)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
}