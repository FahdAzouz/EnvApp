package com.example.envapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private lateinit var cpuProgressBar: ProgressBar
    private lateinit var ramProgressBar: ProgressBar
    private lateinit var cpuPercentage: TextView
    private lateinit var ramPercentage: TextView
    private lateinit var intensitySlider: SeekBar
    private lateinit var intensityPercentage: TextView
    private lateinit var simulationSwitch: SwitchCompat
    private lateinit var updateButton: Button

    companion object {
        private const val CHANNEL_ID = "permission_channel"
        private const val NOTIFICATION_ID = 1
    }

    private val usageUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "Received broadcast: ${intent?.action}")
            intent?.let {
                val cpuUsage = it.getFloatExtra(SimulationService.EXTRA_CPU_USAGE, -1f)
                val ramUsage = it.getFloatExtra(SimulationService.EXTRA_RAM_USAGE, -1f)
                Log.d("MainActivity", "CPU Usage: $cpuUsage, RAM Usage: $ramUsage")
                runOnUiThread {
                    if (cpuUsage != -1f) updateCpuUsage(cpuUsage)
                    if (ramUsage != -1f) updateRamUsage(ramUsage)
                }
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startSimulationService()
            } else {
                Log.d("MainActivity", "Notification permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("MainActivity", "onCreate called")

        cpuProgressBar = findViewById(R.id.cpuProgressBar)
        ramProgressBar = findViewById(R.id.ramProgressBar)
        cpuPercentage = findViewById(R.id.cpuPercentage)
        ramPercentage = findViewById(R.id.ramPercentage)
        intensitySlider = findViewById(R.id.intensitySlider)
        intensityPercentage = findViewById(R.id.intensityPercentage)
        simulationSwitch = findViewById(R.id.simulationSwitch)
        updateButton = findViewById(R.id.updateButton)

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            usageUpdateReceiver,
            IntentFilter(SimulationService.ACTION_USAGE_UPDATE)
        )
        Log.d("MainActivity", "Registered BroadcastReceiver")
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(usageUpdateReceiver)
        Log.d("MainActivity", "Unregistered BroadcastReceiver")
    }

    private fun setupListeners() {
        intensitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                intensityPercentage.text = getString(R.string.percentage_format, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        simulationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startSimulationService()
            } else {
                stopSimulationService()
            }
        }

        updateButton.setOnClickListener {
            updateSimulationIntensity()
        }
    }

    private fun updateSimulationIntensity() {
        val intent = Intent(this, SimulationService::class.java).apply {
            action = SimulationService.ACTION_UPDATE_INTENSITY
            putExtra("intensity", intensitySlider.progress)
        }
        startService(intent)
        Log.d("MainActivity", "Updated simulation intensity: ${intensitySlider.progress}")
    }

    private fun updateCpuUsage(usage: Float) {
        cpuProgressBar.progress = usage.toInt()
        cpuPercentage.text = getString(R.string.percentage_format, usage.toInt())
        Log.d("MainActivity", "Updated CPU Usage UI: ${usage.toInt()}%")
    }

    private fun updateRamUsage(usage: Float) {
        val usagePercentage = usage.toInt()
        ramProgressBar.progress = usagePercentage
        ramPercentage.text = getString(R.string.percentage_format, usagePercentage)
        Log.d("MainActivity", "Updated RAM Usage UI: $usagePercentage%")
    }

    private fun startSimulationService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission is granted, start the service
                    val intent = Intent(this, SimulationService::class.java).apply {
                        action = SimulationService.ACTION_START_SIMULATION
                        putExtra("intensity", intensitySlider.progress)
                    }
                    startService(intent)
                    Log.d("MainActivity", "Started SimulationService")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.d("MainActivity", "Should show permission rationale")
                    // Explain to the user why we need the permission
                    // You can show a dialog here
                }
                else -> {
                    // Request the permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For versions below Android 13, just start the service
            val intent = Intent(this, SimulationService::class.java).apply {
                action = SimulationService.ACTION_START_SIMULATION
                putExtra("intensity", intensitySlider.progress)
            }
            startService(intent)
            Log.d("MainActivity", "Started SimulationService")
        }
    }

    private fun stopSimulationService() {
        val intent = Intent(this, SimulationService::class.java).apply {
            action = SimulationService.ACTION_STOP_SIMULATION
        }
        startService(intent)
        Log.d("MainActivity", "Stopped SimulationService")
    }

    private fun updateSimulationIntensity(intensity: Int) {
        val intent = Intent(this, SimulationService::class.java).apply {
            action = SimulationService.ACTION_UPDATE_INTENSITY
            putExtra("intensity", intensity)
        }
        startService(intent)
        Log.d("MainActivity", "Updated simulation intensity: $intensity")
    }

}