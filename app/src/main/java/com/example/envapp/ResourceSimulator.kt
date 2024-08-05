package com.example.envapp

import kotlinx.coroutines.*
import kotlin.math.min

class ResourceSimulator(
    private val onCpuUsageChanged: (Float) -> Unit,
    private val onRamUsageChanged: (Float) -> Unit
) {
    private var simulationJob: Job? = null
    private var intensity: Int = 50
    private val maxRamUsage = 3.5f  // 3.5 GB out of 4 GB total

    var lastCpuUsage: Float = 0f
        private set
    var lastRamUsage: Float = 0f
        private set

    fun startSimulation() {
        simulationJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val cpuUsage = calculateCpuUsage()
                val ramUsage = calculateRamUsage()

                withContext(Dispatchers.Main) {
                    lastCpuUsage = cpuUsage
                    lastRamUsage = ramUsage
                    onCpuUsageChanged(cpuUsage)
                    onRamUsageChanged(ramUsage)
                }

                delay(1000)  // Update every second
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    fun setIntensity(newIntensity: Int) {
        intensity = newIntensity
    }

    private fun calculateCpuUsage(): Float {
        val baseUsage = 10f
        val variableUsage = intensity.toFloat() / 100f * 90f
        return min(baseUsage + variableUsage + (Math.random() * 10 - 5).toFloat(), 100f)
    }

    private fun calculateRamUsage(): Float {
        val baseUsage = 0.5f
        val variableUsage = intensity.toFloat() / 100f * (maxRamUsage - baseUsage)
        return min(baseUsage + variableUsage + (Math.random() * 0.2 - 0.1).toFloat(), maxRamUsage)
    }
}