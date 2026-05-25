package com.aquadose.app

import android.app.Application
import com.aquadose.app.data.AppContainer

class AquaDoseApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

