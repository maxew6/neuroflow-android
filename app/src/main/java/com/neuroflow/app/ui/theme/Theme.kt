package com.neuroflow.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "Bioluminescent Midnight & Aurora Glass" colour tokens. */
object NF {
    // Canvas + glass
    val Midnight = Color(0xFF090B10)
    val GlassSurface = Color(0xFF131722)
    val GlassOverlay = Color(0xFF1A2030).copy(alpha = 0.78f)
    val OledBlack = Color(0xFF000000)

    // Healthy brain / thriving
    val Sakura = Color(0xFFFF6B9D)
    val Peach = Color(0xFFFF8E53)
    val Mint = Color(0xFF00F5A0)

    // Dopamine daze / warning
    val Amber = Color(0xFFFFB800)
    val Tangerine = Color(0xFFFF6200)

    // Brainrot / critical
    val Crimson = Color(0xFFFF2A5F)
    val Ash = Color(0xFF646A7D)

    // Actions
    val Coral = Color(0xFFFF4B72)
    val Sunset = Color(0xFFFF7A45)

    // Typography colours
    val Ice = Color(0xFFF8FAFC)
    val Slate = Color(0xFF94A3B8)

    // Specular glass rim: top-left highlight -> bottom-right shadow
    private val RimStart = Color(0x40FFFFFF)
    private val RimEnd = Color(0x08FFFFFF)

    fun specularRim(): Brush = Brush.linearGradient(listOf(RimStart, RimEnd))

    fun ctaBrush(): Brush = Brush.horizontalGradient(listOf(Coral, Sunset))

    fun vitalityBrush(): Brush = Brush.horizontalGradient(listOf(Sakura, Peach, Mint))
}

/** Smoothly interpolated neon aura colour for the Dynamic Island, driven by the Reel count. */
fun auraColorFor(reels: Int): Color {
    val r = reels.coerceAtLeast(0).toFloat()
    return when {
        r <= 15f -> NF.Mint
        r <= 30f -> lerp(NF.Mint, NF.Amber, (r - 15f) / 15f)
        r <= 45f -> lerp(NF.Amber, NF.Tangerine, (r - 30f) / 15f)
        r <= 75f -> lerp(NF.Tangerine, NF.Crimson, (r - 45f) / 30f)
        else -> NF.Crimson
    }
}

/** Tabular numerals so counters never jitter horizontally. */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum")

private val NFTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp
    )
)

@Composable
fun NeuroFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NF.Coral,
            onPrimary = Color.White,
            secondary = NF.Mint,
            onSecondary = Color.Black,
            tertiary = NF.Amber,
            background = NF.Midnight,
            onBackground = NF.Ice,
            surface = NF.GlassSurface,
            onSurface = NF.Ice,
            surfaceVariant = Color(0xFF1A2030),
            onSurfaceVariant = NF.Slate,
            error = NF.Crimson
        ),
        typography = NFTypography,
        content = content
    )
}

// ---------------------------------------------------------------------------------------------
// Glassmorphic modifier extensions
// ---------------------------------------------------------------------------------------------

/** Elevated translucent glass card with a 1.dp specular rim. */
fun Modifier.glassCard(shape: Shape = RoundedCornerShape(24.dp)): Modifier =
    this
        .clip(shape)
        .background(NF.GlassOverlay)
        .border(1.dp, NF.specularRim(), shape)

/** Same rim without the fill (for items that sit on an existing glass surface). */
fun Modifier.specularBorder(shape: Shape): Modifier =
    this.border(1.dp, NF.specularRim(), shape)

/** Full-screen midnight canvas with two soft aurora glows. */
fun Modifier.auroraBackdrop(): Modifier = this.drawBehind {
    drawRect(NF.Midnight)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(NF.Mint.copy(alpha = 0.14f), Color.Transparent),
            center = Offset(size.width * 0.92f, size.height * 0.05f),
            radius = size.width * 0.85f
        )
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(NF.Sakura.copy(alpha = 0.12f), Color.Transparent),
            center = Offset(size.width * 0.05f, size.height * 0.38f),
            radius = size.width * 0.9f
        )
    )
}
