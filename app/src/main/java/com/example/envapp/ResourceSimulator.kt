package com.example.envapp

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.*
import android.util.Log
import java.nio.ByteBuffer
import kotlin.math.max
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

class ResourceSimulator(
    private val context: Context,
    private val onUsageChanged: (cpuUsage: Float, ramUsage: Float) -> Unit
) : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    private val job = Job()
    private var simulationJob: Job? = null
    private val maxThreads = Runtime.getRuntime().availableProcessors()
    private var cpuJobs: List<Job> = emptyList()
    private var cpuThreads: List<Thread> = emptyList()
    private var isSimulating = false
    private val memoryAllocations = mutableListOf<Any>()
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val maxAllowedMemoryUsage: Long
        get() = (2000 * 0.8).toLong() // 80% of max memory class
    private var cpuIntensity: Int = 50
    private var ramIntensity: Int = 50
    private var lastCpuUsage = 0f
    private var lastMeasurementTime = System.currentTimeMillis()

    fun startSimulation() {
        isSimulating = true
        launch {
            updateResourceConsumption()
        }
        startMonitoring()
    }

    fun stopSimulation() {
        isSimulating = false
        simulationJob?.cancel()
        simulationJob = null
        cpuThreads.forEach { it.interrupt() }
        cpuThreads = emptyList()
        releaseMemory()
        System.gc() // Suggest garbage collection
        startMonitoring()
    }

    fun startMonitoring() {
        simulationJob?.cancel()
        simulationJob = launch {
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

    fun setCpuIntensity(newIntensity: Int) {
        if (cpuIntensity != newIntensity) {
            cpuIntensity = newIntensity
            if (isSimulating) {
                launch {
                    updateCpuConsumption()
                }
            }
        }
    }

    fun setRamIntensity(newIntensity: Int) {
        if (ramIntensity != newIntensity) {
            ramIntensity = newIntensity
            if (isSimulating) {
                launch {
                    updateRamConsumption()
                }
            }
        }
    }

    private suspend fun updateResourceConsumption() {
        updateCpuConsumption()
        updateRamConsumption()
    }

    private suspend fun updateCpuConsumption() {
        cpuJobs.forEach { it.cancel() }

        val threadCount = min((cpuIntensity * maxThreads / 100), maxThreads)
        cpuJobs = List(threadCount) {
            launch(Dispatchers.Default) {
                while (isActive) {
                    val workDuration = (cpuIntensity.toFloat() / 100 * 10_000_000).toLong() // nanoseconds
                    val sleepDuration = 10_000_000 - workDuration // nanoseconds

                    val startTime = System.nanoTime()
                    while (System.nanoTime() - startTime < workDuration) {
                        // Busy wait
                    }
                    delay(sleepDuration / 1_000_000) // Convert to milliseconds for delay
                }
            }
        }
    }

    private suspend fun updateRamConsumption() {
        releaseMemory()
        System.gc() // Suggest garbage collection
        Log.d("ResourceSimulator", "maxed allowed memory: ${maxAllowedMemoryUsage}")

        // Consume RAM
        val targetMemoryUsage = min((maxAllowedMemoryUsage * ramIntensity) / 100, maxAllowedMemoryUsage) * 1024 * 1024
        var allocatedMemory = 0L

        while (allocatedMemory < targetMemoryUsage && isActive) {
            try {
                val remainingToAllocate = targetMemoryUsage - allocatedMemory

                // Calculate allocationSize based on ramIntensity and remaining memory to allocate
                val allocationSize = min(
                    (remainingToAllocate * ramIntensity / 100).toLong(),
                    (maxAllowedMemoryUsage * 1024 * 1024) / 10 // Limit to 10% of max allowed memory per iteration
                ).toInt()

                // Allocate a Bitmap
                val side = max(1, sqrt((allocationSize) / 4.0).toInt()) // Ensure side is at least 1
                val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Random.nextInt())
                memoryAllocations.add(bitmap)
                allocatedMemory += side * side * 4
                Log.d("ResourceSimulator", "bitmap method was used <----------")

                Log.d("ResourceSimulator", "Allocated ${allocatedMemory / 1024 / 1024}MB total")

                yield() // Allow other coroutines to run
                delay(10) // Small delay to prevent UI freezing
            } catch (e: OutOfMemoryError) {
                Log.e("ResourceSimulator", "OOM while allocating, total: ${allocatedMemory / 1024 / 1024}MB", e)
                break
            }
        }
    }

    private fun releaseMemory() {
        memoryAllocations.forEach { allocation ->
            when (allocation) {
                is Bitmap -> allocation.recycle()
            }
        }
        memoryAllocations.clear()
    }

    private fun measureCpuUsage(): Float {
        // This is a simplified CPU usage calculation based on the intensity
        return (cpuIntensity.toFloat() * 0.9f).coerceIn(0f, 100f)
    }

    private fun measureRamUsage(): Float {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val usedMemory = memoryInfo.totalMem - memoryInfo.availMem
        return (usedMemory.toFloat() / memoryInfo.totalMem * 100).coerceIn(0f, 100f)
    }

    fun cancelAllCoroutines() {
        job.cancel()
    }

    private fun sqrt(x: Double): Double = kotlin.math.sqrt(x)
}