package com.aquadose.app.ui

import com.aquadose.api.AquaDoseFirmwareLimits
import java.util.Locale

internal fun doseMinLabel(): String =
    AquaDoseFirmwareLimits.MIN_ML.toDoseLimitLabel()

internal fun doseMaxLabel(): String =
    AquaDoseFirmwareLimits.MAX_ML.toDoseLimitLabel()

private fun Double.toDoseLimitLabel(): String =
    String.format(Locale.US, "%.1f", this).trimTrailingZeros()

private fun String.trimTrailingZeros(): String =
    if (contains('.')) trimEnd('0').trimEnd('.') else this
