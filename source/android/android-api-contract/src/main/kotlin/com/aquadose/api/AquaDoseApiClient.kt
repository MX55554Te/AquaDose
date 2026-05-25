package com.aquadose.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.IOException
import java.util.Locale

sealed class AquaDoseResult<out T> {
    data class Success<T>(val value: T) : AquaDoseResult<T>()
    data class ValidationFailure(val errors: List<ValidationError>) : AquaDoseResult<Nothing>()
    data class HttpFailure(val statusCode: Int, val body: String?) : AquaDoseResult<Nothing>()
    data class NetworkFailure(val exception: IOException) : AquaDoseResult<Nothing>()
    data class UnexpectedFailure(val exception: Throwable) : AquaDoseResult<Nothing>()
}

class AquaDoseApiClient private constructor(
    private val api: AquaDoseHttpApi,
) {
    suspend fun getStatus(): AquaDoseResult<AquaDoseStatus> =
        request { api.getStatus() }

    suspend fun manualDose(pumpIndex: Int, ml: Double): AquaDoseResult<String> {
        val errors = listOfNotNull(
            AquaDoseValidation.validatePumpIndex(pumpIndex),
            AquaDoseValidation.validateMl(ml),
        )
        if (errors.isNotEmpty()) return AquaDoseResult.ValidationFailure(errors)
        return request { api.manualDose(pumpIndex, ml) }
    }

    suspend fun primeStart(pumpIndex: Int): AquaDoseResult<String> {
        AquaDoseValidation.validatePumpIndex(pumpIndex)?.let {
            return AquaDoseResult.ValidationFailure(listOf(it))
        }
        return request { api.primeStart(pumpIndex) }
    }

    suspend fun primeStop(): AquaDoseResult<String> =
        request { api.primeStop() }

    suspend fun calibrationRun(pumpIndex: Int): AquaDoseResult<String> {
        AquaDoseValidation.validatePumpIndex(pumpIndex)?.let {
            return AquaDoseResult.ValidationFailure(listOf(it))
        }
        return request { api.calibrationRun(pumpIndex) }
    }

    suspend fun calibrationSave(pumpIndex: Int, grams: Double): AquaDoseResult<String> {
        val errors = listOfNotNull(
            AquaDoseValidation.validatePumpIndex(pumpIndex),
            AquaDoseValidation.validateCalibrationGrams(grams),
        )
        if (errors.isNotEmpty()) return AquaDoseResult.ValidationFailure(errors)
        return request { api.calibrationSave(pumpIndex, grams) }
    }

    suspend fun savePumpConfig(update: PumpConfigUpdate): AquaDoseResult<String> {
        val errors = AquaDoseValidation.validatePumpConfig(update)
        if (errors.isNotEmpty()) return AquaDoseResult.ValidationFailure(errors)
        return request { api.savePumpConfig(update.toFirmwareQuery()) }
    }

    suspend fun saveTimezone(timezone: String): AquaDoseResult<String> {
        if (timezone.isBlank()) {
            return AquaDoseResult.ValidationFailure(
                listOf(ValidationError("tz", "Timezone must not be blank.")),
            )
        }
        return request { api.saveTimezone(timezone) }
    }

    suspend fun emergencyStop(): AquaDoseResult<String> =
        request { api.emergencyStop() }

    suspend fun resetWifi(): AquaDoseResult<String> =
        request { api.resetWifi() }

    suspend fun factoryReset(): AquaDoseResult<String> =
        request { api.factoryReset() }

    private suspend fun <T> request(call: suspend () -> Response<T>): AquaDoseResult<T> =
        try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    AquaDoseResult.Success(body)
                } else {
                    AquaDoseResult.UnexpectedFailure(IllegalStateException("HTTP response body was empty."))
                }
            } else {
                AquaDoseResult.HttpFailure(response.code(), response.errorBody()?.string())
            }
        } catch (exception: IOException) {
            AquaDoseResult.NetworkFailure(exception)
        } catch (exception: Throwable) {
            AquaDoseResult.UnexpectedFailure(exception)
        }

    private fun PumpConfigUpdate.toFirmwareQuery(): Map<String, String> =
        AquaDoseFirmwareQuery.savePumpConfig(this)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun create(
            baseUrl: String,
            okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
        ): AquaDoseApiClient {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return AquaDoseApiClient(retrofit.create(AquaDoseHttpApi::class.java))
        }
    }
}

object AquaDoseFirmwareQuery {
    fun savePumpConfig(update: PumpConfigUpdate): Map<String, String> {
        val query = linkedMapOf(
            "p" to update.pumpIndex.toString(),
            "name" to update.name,
            "enabled" to if (update.enabled) "1" else "0",
            "rate" to update.mlPerSec.toFirmwareDecimal(),
        )
        update.schedules.forEachIndexed { index, schedule ->
            query["s${index}en"] = if (schedule.enabled) "1" else "0"
            query["s${index}h"] = schedule.hour.toString()
            query["s${index}m"] = schedule.minute.toString()
            query["s${index}ml"] = schedule.ml.toFirmwareDecimal()
        }
        return query
    }

    private fun Double.toFirmwareDecimal(): String =
        String.format(Locale.US, "%.4f", this).trimTrailingZeros()

    private fun String.trimTrailingZeros(): String =
        if (contains('.')) trimEnd('0').trimEnd('.') else this
}
