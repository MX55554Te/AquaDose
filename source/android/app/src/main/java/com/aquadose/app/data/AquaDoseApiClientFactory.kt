package com.aquadose.app.data

import com.aquadose.api.AquaDoseApiClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface AquaDoseApiClientFactory {
    fun create(controllerAddress: String): AquaDoseApiClient
}

class DefaultAquaDoseApiClientFactory : AquaDoseApiClientFactory {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun create(controllerAddress: String): AquaDoseApiClient =
        AquaDoseApiClient.create(
            baseUrl = normalizeControllerAddress(controllerAddress),
            okHttpClient = okHttpClient,
        )

    private fun normalizeControllerAddress(controllerAddress: String): String {
        val trimmed = controllerAddress.trim().trimEnd('/')
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return "http://$trimmed"
    }
}
