package com.aquadose.app.data

import android.content.Context

class AppContainer(context: Context) {
    private val settingsRepository = ControllerSettingsRepository(
        context = context.applicationContext,
    )

    val controllerRepository: ControllerRepository = ControllerRepository(
        settingsRepository = settingsRepository,
        apiClientFactory = DefaultAquaDoseApiClientFactory(),
    )
}

