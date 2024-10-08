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

    private val resourceSimulator by lazy {
        ResourceSimulator(this) { detailedUsage ->
            broadcastUpdate(detailedUsage)
        }
    }

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
        const val EXTRA_IS_LOADING = "extra_is_loading"
        const val EXTRA_DETAILED_USAGE = "extra_detailed_usage"

    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SimulationService", "onStartCommand called")
        when (intent?.action) {
            ACTION_START_SIMULATION -> {
                broadcastUpdate(isLoading = true)
                val cpuIntensity = intent.getIntExtra("cpuIntensity", 50)
                val ramIntensity = intent.getIntExtra("ramIntensity", 50)
                resourceSimulator.setCpuIntensity(cpuIntensity)
                resourceSimulator.setRamIntensity(ramIntensity)
                resourceSimulator.startSimulation()
                isSimulating = true
                Log.d("SimulationService", "Starting simulation with CPU intensity: $cpuIntensity, RAM intensity: $ramIntensity")
                broadcastUpdate(isLoading = false)
            }
            ACTION_STOP_SIMULATION -> {
                broadcastUpdate(isLoading = true)
                resourceSimulator.stopSimulation()
                isSimulating = false
                val currentUsage = resourceSimulator.getCurrentUsage()
                broadcastUpdate(currentUsage, isLoading = false)
                stopForeground(true)
                stopSelf()
                Log.d("SimulationService", "Stopping simulation")
            }
            ACTION_UPDATE_INTENSITY -> {
                broadcastUpdate(isLoading = true)
                val cpuIntensity = intent.getIntExtra("cpuIntensity", 50)
                val ramIntensity = intent.getIntExtra("ramIntensity", 50)
                resourceSimulator.setCpuIntensity(cpuIntensity)
                resourceSimulator.setRamIntensity(ramIntensity)
                Log.d("SimulationService", "Updating intensity to: CPU $cpuIntensity, RAM $ramIntensity")
                broadcastUpdate(isLoading = false)
            }
        }
        startForeground(notificationId, createNotification(0f, 0f))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        resourceSimulator.stopSimulation()
        resourceSimulator.cancelAllCoroutines()
        Log.d("SimulationService", "onDestroy called")
    }

    private fun createNotification(cpuUsage: Float, ramUsage: Float): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SimulationService::class.java).apply {
            action = ACTION_STOP_SIMULATION
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
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
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
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

    private fun broadcastUpdate(detailedUsage: DetailedUsage? = null, isLoading: Boolean = false) {
        val intent = Intent(ACTION_USAGE_UPDATE).apply {
            putExtra(EXTRA_DETAILED_USAGE, detailedUsage)
            putExtra(EXTRA_IS_LOADING, isLoading)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("SimulationService", "Broadcast sent: $detailedUsage, Loading: $isLoading")

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNotificationUpdate >= 5000) {  // Update every 5 seconds
            detailedUsage?.let { updateNotification(it.cpuUsage, it.ramUsage) }
            lastNotificationUpdate = currentTime
        }
    }

    private fun updateNotification(cpuUsage: Float, ramUsage: Float) {
        val notification = createNotification(cpuUsage, ramUsage)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
}