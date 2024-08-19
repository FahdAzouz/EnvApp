package com.example.envapp

import android.app.Application

class MyApplication : Application() {
    lateinit var resourceSimulator: ResourceSimulator

    override fun onCreate() {
        super.onCreate()
        resourceSimulator = ResourceSimulator(this) { detailedUsage ->
            // Handle usage updates here if needed
        }
    }
}