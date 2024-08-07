package com.example.envapp

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.Build
import kotlinx.coroutines.*
import java.io.RandomAccessFile
import kotlin.math.max
import android.util.Log
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
    private var lastCpuUsage = 0f
    private var lastIdleTime = 0L
    private var lastTotalTime = 0L

    companion object {
        init {
            System.loadLibrary("memory_allocator")
        }
    }

    private external fun allocateMemory(intensity: Int)
    private external fun freeAllocatedMemory()

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
        val threadCount = (intensity * maxThreads / 100).coerceIn(1, maxThreads)
        cpuThreads = List(threadCount) {
            Thread {
                while (!Thread.currentThread().isInterrupted) {
                    // Busy-wait to simulate CPU load
                    val start = System.nanoTime()
                    while (System.nanoTime() - start < 1000000) { // 1 millisecond
                        // Simulating CPU work
                    }
                }
            }.apply { start() }
        }

        // Consume RAM
        try {
            val maxMemoryToAllocate = (Runtime.getRuntime().maxMemory() * 0.25).toLong() // Max 25% of available memory
            val memoryToAllocate = (intensity * maxMemoryToAllocate / 100).coerceIn(0, maxMemoryToAllocate).toInt()
            ramAllocation = ByteArray(memoryToAllocate)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        }
    }


    private fun measureCpuUsage(): Float {
        try {
            // We can log or measure the CPU usage via the ActivityManager's getProcessMemoryInfo method
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid()))

            val totalPss = memoryInfo[0].totalPss.toFloat()
            val totalMemory = Runtime.getRuntime().totalMemory().toFloat()
            val usedMemory = totalMemory - Runtime.getRuntime().freeMemory().toFloat()

            val cpuUsage = (usedMemory / totalMemory * 100).coerceIn(0f, 100f)

            // Add some randomness to simulate fluctuations
            val randomFactor = (Math.random() * 10 - 5).toFloat()
            lastCpuUsage = max(0f, cpuUsage)

            return lastCpuUsage
        } catch (e: Exception) {
            e.printStackTrace()
            return lastCpuUsage
        }
    }

    private fun measureRamUsage(): Float {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemory = memoryInfo.totalMem.toFloat()
        val availableMemory = memoryInfo.availMem.toFloat()
        val usedMemory = totalMemory - availableMemory

        val ramUsage = (usedMemory / totalMemory * 100).coerceIn(0f, 100f)
        return ramUsage
    }
}