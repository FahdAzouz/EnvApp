package com.example.envapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter


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
    private lateinit var infoIcon: ImageView
    private lateinit var runningProcessesText: TextView
    private lateinit var availableRamText: TextView
    private lateinit var totalRamText: TextView
    private lateinit var usageChart: LineChart
    private val CPU_MAX_INTENSITY = 90
    private val RAM_MAX_INTENSITY = 80


    // In the MainActivity class, add this companion object
    companion object {
        private const val CHANNEL_ID = "permission_channel"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_DETAILED_USAGE = "extra_detailed_usage"
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
        infoIcon = findViewById(R.id.infoIcon)
        runningProcessesText = findViewById(R.id.runningProcessesText)
        availableRamText = findViewById(R.id.availableRamText)
        totalRamText = findViewById(R.id.totalRamText)
        usageChart = findViewById(R.id.usageChart)
        setupChart()
        setupInfoIcon()

        setupListeners()
    }

    private fun setupChart() {
        usageChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                labelCount = 5
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}s"
                    }
                }
            }

            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                axisMaximum = 100f
                labelCount = 5
            }

            axisRight.isEnabled = false

            legend.apply {
                form = com.github.mikephil.charting.components.Legend.LegendForm.LINE
                textSize = 11f
                textColor = Color.BLACK
                verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.LEFT
                orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
            }
        }
    }

    private fun updateChart(detailedUsage: DetailedUsage) {
        val data = usageChart.data ?: LineData().also { usageChart.data = it }

        if (data.dataSetCount == 0) {
            val cpuDataSet = LineDataSet(null, "CPU Usage").apply {
                color = ContextCompat.getColor(this@MainActivity, R.color.cpu_color)
                setCircleColor(ContextCompat.getColor(this@MainActivity, R.color.cpu_color))
                lineWidth = 2f
                circleRadius = 3f
                setDrawCircleHole(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            val ramDataSet = LineDataSet(null, "RAM Usage").apply {
                color = ContextCompat.getColor(this@MainActivity, R.color.ram_color)
                setCircleColor(ContextCompat.getColor(this@MainActivity, R.color.ram_color))
                lineWidth = 2f
                circleRadius = 3f
                setDrawCircleHole(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            data.addDataSet(cpuDataSet)
            data.addDataSet(ramDataSet)
        }

        data.addEntry(Entry(data.entryCount.toFloat(), detailedUsage.cpuUsage), 0)
        data.addEntry(Entry(data.entryCount.toFloat(), detailedUsage.ramUsage), 1)

        data.notifyDataChanged()
        usageChart.notifyDataSetChanged()
        usageChart.setVisibleXRangeMaximum(30f)
        usageChart.moveViewToX(data.entryCount.toFloat())
    }

    private fun setupInfoIcon() {
        infoIcon.setOnClickListener {
            showAppInfo()
        }
    }

    private fun showAppInfo() {
        val appInfo = """
        App Functionality:
        This app simulates CPU and RAM usage on your device. It allows you to control the intensity of the simulation and monitor the resource usage in real-time.

        Key Features:
        - Adjust CPU and RAM usage intensity
        - Start and stop simulation
        - Real-time monitoring of resource usage
        - Background service for continuous simulation

        Author: Visionary Crafter
        Version: 1.0
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("About EnvApp")
            .setMessage(appInfo)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
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
            intent?.let {
                val detailedUsage = it.getParcelableExtra<DetailedUsage>(SimulationService.EXTRA_DETAILED_USAGE)
                detailedUsage?.let { usage ->
                    updateUI(usage)
                    updateChart(usage)
                }
                val isLoading = it.getBooleanExtra(SimulationService.EXTRA_IS_LOADING, false)
                runOnUiThread {
                    if (isLoading) {
                        showLoading("Adjusting resource usage...")
                    } else {
                        hideLoading()
                        if (!simulationSwitch.isChecked) {
                            statusText.text = getString(R.string.status_idle)
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(usage: DetailedUsage) {
        cpuProgressBar.progress = usage.cpuUsage.toInt()
        cpuPercentage.text = getString(R.string.percentage_format, usage.cpuUsage.toInt())
        ramProgressBar.progress = usage.ramUsage.toInt()
        ramPercentage.text = getString(R.string.percentage_format, usage.ramUsage.toInt())
        runningProcessesText.text = getString(R.string.running_processes_format, usage.runningProcesses)
        availableRamText.text = getString(R.string.memory_format, usage.availableRam / (1024 * 1024))
        totalRamText.text = getString(R.string.memory_format, usage.totalRam / (1024 * 1024))
    }

}