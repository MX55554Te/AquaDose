package com.aquadose.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface AquaDoseHttpApi {
    @GET("status")
    suspend fun getStatus(): Response<AquaDoseStatus>

    @GET("manual")
    suspend fun manualDose(
        @Query("p") pumpIndex: Int,
        @Query("ml") ml: Double,
    ): Response<String>

    @GET("primeStart")
    suspend fun primeStart(
        @Query("p") pumpIndex: Int,
    ): Response<String>

    @GET("primeStop")
    suspend fun primeStop(): Response<String>

    @GET("calRun")
    suspend fun calibrationRun(
        @Query("p") pumpIndex: Int,
    ): Response<String>

    @GET("calSave")
    suspend fun calibrationSave(
        @Query("p") pumpIndex: Int,
        @Query("grams") grams: Double,
    ): Response<String>

    @GET("savePump")
    suspend fun savePumpConfig(
        @QueryMap(encoded = false) query: Map<String, String>,
    ): Response<String>

    @GET("saveTZ")
    suspend fun saveTimezone(
        @Query("tz") timezone: String,
    ): Response<String>

    @GET("stop")
    suspend fun emergencyStop(): Response<String>

    @GET("reset")
    suspend fun resetWifi(): Response<String>

    @GET("factoryReset")
    suspend fun factoryReset(): Response<String>
}

