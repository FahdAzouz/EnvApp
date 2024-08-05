package com.example.envapp

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.min

class ResourceSimulator(
    private val context: Context,
    private val onUsageChanged: (cpuUsage: Float, ramUsage: Float) -> Unit
) {
    private var simulationJob: Job? = null
    private var intensity: Int = 50
    private val maxThreads = Runtime.getRuntime().availableProcessors()
    private var cpuThreads: List<Thread> = emptyList()
    private var ramAllocation: ByteArray? = null
    private var isSimulating = false
    private var lastCpuTime = 0L
    private var lastRealTime = 0L

    fun startSimulation() {
        isSimulating = true
        updateResourceConsumption()
        startMonitoring()
    }

    fun stopSimulation() {
        isSimulating = false
        simulationJob?.cancel()
        simulationJob = null
        cpuThreads.forEach { it.interrupt() }
        cpuThreads = emptyList()
        ramAllocation = null
        startMonitoring()
    }

    fun startMonitoring() {
        simulationJob?.cancel()
        simulationJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val cpuUsage = measureCpuUsage()
                val ramUsage = measureRamUsage()

                withContext(Dispatchers.Main) {
                    onUsageChanged(cpuUsage, ramUsage)
                }

                delay(1000)  // Update every second
            }
        }
    }

    fun setIntensity(newIntensity: Int) {
        intensity = newIntensity
        if (isSimulating) {
            updateResourceConsumption()
        }
    }

    private fun updateResourceConsumption() {
        cpuThreads.forEach { it.interrupt() }
        ramAllocation = null

        // Start CPU consumption
        val threadCount = (intensity * maxThreads / 100).coerceAtLeast(1)
        cpuThreads = List(threadCount) {
            Thread {
                while (!Thread.currentThread().isInterrupted) {
                    // CPU-intensive operation
                    var result = 0.0
                    repeat(1000000) { i ->
                        result += kotlin.math.sin(i.toDouble())
                    }
                }
            }.apply { start() }
        }

        // Consume RAM
        try {
            val maxMemoryToAllocate = (Runtime.getRuntime().maxMemory() * 0.25).toLong() // Max 25% of available memory
            val memoryToAllocate = min((intensity * 10L * 1024 * 1024), maxMemoryToAllocate)
            ramAllocation = ByteArray(memoryToAllocate.toInt())
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        }
    }

    private fun measureCpuUsage(): Float {
        val currentCpuTime = Debug.threadCpuTimeNanos()
        val currentRealTime = System.nanoTime()

        val cpuUsage = if (lastRealTime > 0) {
            val cpuTimeDiff = currentCpuTime - lastCpuTime
            val realTimeDiff = currentRealTime - lastRealTime
            (cpuTimeDiff * 100.0 / realTimeDiff).toFloat().coerceIn(0f, 100f)
        } else {
            0f
        }

        lastCpuTime = currentCpuTime
        lastRealTime = currentRealTime

        // Add some randomness to simulate fluctuations
        val randomFactor = (Math.random() * 10 - 5).toFloat()
        return max(0f, min(100f, cpuUsage + randomFactor + (if (isSimulating) intensity.toFloat() else 0f)))
    }

    private fun measureRamUsage(): Float {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemory = memoryInfo.totalMem.toFloat()
        val availableMemory = memoryInfo.availMem.toFloat()
        val usedMemory = totalMemory - availableMemory

        // Add some randomness to simulate fluctuations
        val randomFactor = (Math.random() * 5 - 2.5).toFloat()
        return ((usedMemory / totalMemory * 100) + randomFactor + (if (isSimulating) intensity.toFloat() / 2 else 0f)).coerceIn(0f, 100f)
    }
}