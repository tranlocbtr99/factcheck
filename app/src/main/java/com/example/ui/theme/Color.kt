package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Liquid Glass Core Palette
val GlassBackground = Color(0xFF090D1A)
val GlassSurface = Color(0x331E293B)
val GlassSurfaceElevated = Color(0x4D1E293B)
val GlassBorder = Color(0x3338BDF8)
val GlassBorderHighlight = Color(0x6638BDF8)

// Neon & Brand Accents
val CyanNeon = Color(0xFF38BDF8)
val BlueNeon = Color(0xFF0EA5E9)
val PurpleNeon = Color(0xFFA855F7)

// Text Colors
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Verdict Accents
val VerdictTrueColor = Color(0xFF10B981)
val VerdictFalseColor = Color(0xFFEF4444)
val VerdictPartialColor = Color(0xFFF59E0B)
val VerdictMisleadingColor = Color(0xFFF97316)
val VerdictInsufficientColor = Color(0xFFA855F7)
val VerdictUnverifiableColor = Color(0xFF94A3B8)

// Liquid Glass Gradients
val LiquidGlassGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0x3D1E293B),
        Color(0x1F0F172A)
    )
)

val LiquidGlowGradient = Brush.radialGradient(
    colors = listOf(
        Color(0x3338BDF8),
        Color(0x000F172A)
    )
)

val HeroHeaderGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF38BDF8),
        Color(0xFF818CF8)
    )
)
