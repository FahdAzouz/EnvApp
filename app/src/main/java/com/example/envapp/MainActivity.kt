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
import android.view.View
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
    private lateinit var simulationSwitch: SwitchCompat
    private lateinit var updateButton: Button
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var cpuIntensitySlider: SeekBar
    private lateinit var cpuIntensityPercentage: TextView
    private lateinit var ramIntensitySlider: SeekBar
    private lateinit var ramIntensityPercentage: TextView

    companion object {
        private const val CHANNEL_ID = "permission_channel"
        private const val NOTIFICATION_ID = 1
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
        simulationSwitch = findViewById(R.id.simulationSwitch)
        updateButton = findViewById(R.id.updateButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        statusText = findViewById(R.id.statusText)
        cpuIntensitySlider = findViewById(R.id.cpuIntensitySlider)
        cpuIntensityPercentage = findViewById(R.id.cpuIntensityPercentage)
        ramIntensitySlider = findViewById(R.id.ramIntensitySlider)
        ramIntensityPercentage = findViewById(R.id.ramIntensityPercentage)

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
        cpuIntensitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                cpuIntensityPercentage.text = getString(R.string.percentage_format, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        ramIntensitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                ramIntensityPercentage.text = getString(R.string.percentage_format, progress)
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
        showLoading("Adjusting CPU and RAM load...")
        val intent = Intent(this, SimulationService::class.java).apply {
            action = SimulationService.ACTION_UPDATE_INTENSITY
            putExtra("cpuIntensity", cpuIntensitySlider.progress)
            putExtra("ramIntensity", ramIntensitySlider.progress)
        }
        startService(intent)
        Log.d("MainActivity", "Updated simulation intensity: CPU ${cpuIntensitySlider.progress}, RAM ${ramIntensitySlider.progress}")
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
        showLoading("Starting simulation...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission is granted, start the service
                    val intent = Intent(this, SimulationService::class.java).apply {
                        action = SimulationService.ACTION_START_SIMULATION
                        putExtra("cpuIntensity", cpuIntensitySlider.progress)
                        putExtra("ramIntensity", ramIntensitySlider.progress)
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
                putExtra("cpuIntensity", cpuIntensitySlider.progress)
                putExtra("ramIntensity", ramIntensitySlider.progress)
            }
            startService(intent)
            Log.d("MainActivity", "Started SimulationService")
        }
    }

    private fun stopSimulationService() {
        showLoading("Stopping simulation...")
        val intent = Intent(this, SimulationService::class.java).apply {
            action = SimulationService.ACTION_STOP_SIMULATION
        }
        startService(intent)
        Log.d("MainActivity", "Stopped SimulationService")
        // The final update will be received through the BroadcastReceiver
    }

    private fun showLoading(message: String) {
        loadingIndicator.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun hideLoading() {
        loadingIndicator.visibility = View.GONE
        statusText.text = getString(R.string.status_idle)
    }

    private val usageUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "Received broadcast: ${intent?.action}")
            intent?.let {
                val cpuUsage = it.getFloatExtra(SimulationService.EXTRA_CPU_USAGE, -1f)
                val ramUsage = it.getFloatExtra(SimulationService.EXTRA_RAM_USAGE, -1f)
                val isLoading = it.getBooleanExtra(SimulationService.EXTRA_IS_LOADING, false)
                Log.d(
                    "MainActivity",
                    "CPU Usage: $cpuUsage, RAM Usage: $ramUsage, Is Loading: $isLoading"
                )
                runOnUiThread {
                    if (cpuUsage != -1f) updateCpuUsage(cpuUsage)
                    if (ramUsage != -1f) updateRamUsage(ramUsage)
                    if (isLoading) {
                        showLoading("Adjusting RAM load...")
                    } else {
                        hideLoading()
                        if (!simulationSwitch.isChecked) {
                            // Update UI to reflect that simulation is stopped
                            statusText.text = getString(R.string.status_idle)
                        }
                    }
                }
            }
        }
    }

}