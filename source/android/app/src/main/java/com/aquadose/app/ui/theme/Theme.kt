package com.aquadose.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AquaDoseColors = lightColorScheme(
    primary = Color(0xFF006B75),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA9EDF7),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF4A6267),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE7ED),
    onSecondaryContainer = Color(0xFF061F24),
    tertiary = Color(0xFF5B5F7D),
    onTertiary = Color.White,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAFB),
    onBackground = Color(0xFF171D1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1F),
    surfaceVariant = Color(0xFFDCE4E7),
    onSurfaceVariant = Color(0xFF404B4F),
    outline = Color(0xFF708084),
    outlineVariant = Color(0xFFC0C8CB),
)

private val AquaDoseTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
        ),
        headlineSmall = headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 23.sp,
        ),
        bodyLarge = bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 23.sp,
        ),
        bodyMedium = bodyMedium.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelLarge = labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

@Composable
fun AquaDoseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AquaDoseColors,
        typography = AquaDoseTypography,
        content = content,
    )
}
