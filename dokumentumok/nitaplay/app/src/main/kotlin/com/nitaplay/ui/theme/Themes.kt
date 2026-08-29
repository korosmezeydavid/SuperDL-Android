package com.nitaplay.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class AppThemeId {
    PURPLE_NIGHT,
    CAT,
    DOG,
    SUNSET,
    OCEAN,
    MIDNIGHT,
    SAKURA;

    fun labelHu(): String = when (this) {
        PURPLE_NIGHT -> "Lila éjszaka"
        CAT -> "Cicás"
        DOG -> "Kutyás"
        SUNSET -> "Naplemente"
        OCEAN -> "Óceán"
        MIDNIGHT -> "Éjfél"
        SAKURA -> "Sakura"
    }
}

data class NitaThemeColors(
    val id: AppThemeId,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val card: Color,
    val accent: Color,
    val secondary: Color,
    val text: Color,
    val textMuted: Color,
    val isDark: Boolean
) {
    fun backgroundBrush(): Brush = Brush.verticalGradient(
        listOf(backgroundTop, backgroundBottom)
    )
}

fun themeOf(id: AppThemeId): NitaThemeColors = when (id) {
    AppThemeId.PURPLE_NIGHT -> NitaThemeColors(
        id = id,
        backgroundTop = Color(0xFF2A1B3D),
        backgroundBottom = Color(0xFF150E22),
        card = Color(0xFF2E2248),
        accent = Color(0xFFB388FF),
        secondary = Color(0xFF7C4DFF),
        text = Color(0xFFF3EAFF),
        textMuted = Color(0xFFA896C4),
        isDark = true
    )
    AppThemeId.CAT -> NitaThemeColors(
        id = id,
        backgroundTop = Color(0xFFFFF6EC),
        backgroundBottom = Color(0xFFFFE8D6),
        card = Color(0xFFFFFFFF),
        accent = Color(0xFFE88D67),
        secondary = Color(0xFFC97B84),
        text = Color(0xFF4A3B33),
        textMuted = Color(0xFF8A7368),
        isDark = false
    )
    AppThemeId.DOG -> NitaThemeColors(
        id = id,
        backgroundTop = Color(0xFFF2F7F0),
        backgroundBottom = Color(0xFFE4EFE0),
        card = Color(0xFFFFFFFF),
        accent = Color(0xFF6BA368),
        secondary = Color(0xFFC99A5B),
        text = Color(0xFF33402F),
        textMuted = Color(0xFF6A7A64),
        isDark = false
    )
    AppThemeId.SUNSET -> NitaThemeColors(
        id = id,
        backgroundTop = Color(0xFFFF9A6C),
        backgroundBottom = Color(0xFF6A3093),
        card = Color(0x33FFFFFF),
        accent = Color(0xFFFFD166),
        secondary = Color(0xFFFF8E53),
        text = Color(0xFFFFFFFF),
        textMuted = Color(0xFFE8D5FF),
        isDark = true
    )
    AppThemeId.OCEAN -> NitaThemeColors(
        id = id,
        backgroundTop = Color(0xFF0F2B3D),
        backgroundBottom = Color(0xFF0A1A26),
        card = Color(0xFF163A52),
        accent = Color(0xFF4ECDC4),
        secondary = Color(0xFF2E8BC0),
        text = Color(0xFFE8F6F8),
        textMuted = Color(0xFF8FB8C4),
        isDark = true
    )
    AppThemeId.MIDNIGHT -> NitaThemeColors(
        id = id,
        backgroundTop = Color(0xFF000000),
        backgroundBottom = Color(0xFF000000),
        card = Color(0xFF121212),
        accent = Color(0xFFBB86FC),
        secondary = Color(0xFF7C4DFF),
        text = Color(0xFFEDEDED),
        textMuted = Color(0xFF9E9E9E),
        isDark = true
    )
    AppThemeId.SAKURA -> NitaThemeColors(
        id = id,
        backgroundTop = Color(0xFFFFF0F5),
        backgroundBottom = Color(0xFFFFE4EC),
        card = Color(0xFFFFFFFF),
        accent = Color(0xFFE8799E),
        secondary = Color(0xFFC48CB3),
        text = Color(0xFF4A2F3A),
        textMuted = Color(0xFF8A6A76),
        isDark = false
    )
}

/** Placeholder mood art when no album cover */
fun moodEmoji(id: AppThemeId): String = when (id) {
    AppThemeId.PURPLE_NIGHT -> "🌙"
    AppThemeId.CAT -> "🐱"
    AppThemeId.DOG -> "🐶"
    AppThemeId.SUNSET -> "🌅"
    AppThemeId.OCEAN -> "🌊"
    AppThemeId.MIDNIGHT -> "✨"
    AppThemeId.SAKURA -> "🌸"
}
