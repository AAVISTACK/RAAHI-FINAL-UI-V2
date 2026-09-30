package `in`.raahi.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import `in`.raahi.app.R

// ---------------------------------------------------------------------------------------
// "Concept Redesign" design tokens — ported 1:1 from the approved HTML mockup (glass/
// gradient dark UI, Space Grotesk + Plus Jakarta Sans, orange→pink / cyan→violet accents).
// This is the single source of truth for color; every screen should reference these tokens,
// never a hardcoded hex, so the whole app restyles from one place.
// ---------------------------------------------------------------------------------------

// Backgrounds
val RaahiBg = Color(0xFF07090F)          // --bg   (page/root)
val RaahiBg2 = Color(0xFF0C0F18)         // --bg2  (screen surface)
val RaahiGlass = Color(0x0BFFFFFF)       // --glass        ~4.5% white
val RaahiGlassStrong = Color(0x14FFFFFF) // --glass-strong ~8% white
val RaahiBorder = Color(0x17FFFFFF)      // --border      ~9% white
val RaahiBorderSoft = Color(0x0FFFFFFF)  // --border-soft ~6% white

// Text
val RaahiText = Color(0xFFF4F5F7)
val RaahiTextDim = Color(0xFF8D93A3)
val RaahiTextFaint = Color(0xFF5C6274)

// Accents
val RaahiOrange = Color(0xFFFF6A2C)
val RaahiAmber = Color(0xFFFFB020)
val RaahiPink = Color(0xFFFF3D7F)
val RaahiGreen = Color(0xFF3DDC84)
val RaahiCyan = Color(0xFF31C5FF)
val RaahiVioletAccent = Color(0xFF7C5CFF)
val RaahiRed = Color(0xFFFF4D6D)

// Backward-compat aliases so any screen not yet migrated off the old token names (pre-
// concept-redesign) still compiles and picks up a close equivalent from this palette.
val RaahiNavyBackground = RaahiBg
val RaahiCardBg = RaahiBg2
val RaahiSurfaceHigh = RaahiGlassStrong
val RaahiSecondaryCard = RaahiGlassStrong
val RaahiCardBorder = RaahiBorder
val RaahiOrangeAccent = RaahiOrange
val RaahiOrangeDark = Color(0xFFCC4E1E)
val RaahiTextPrimary = RaahiText
val RaahiTextSecondary = RaahiTextDim
val RaahiTextMuted = RaahiTextFaint
val RaahiYellow = RaahiAmber
val RaahiWarningOrange = RaahiAmber

// Signature gradients — used across logo mark, CTA buttons, avatar, AI orb, nav center pill.
val RaahiBrandGradient = Brush.linearGradient(listOf(RaahiOrange, RaahiPink))
val RaahiAiGradient = Brush.linearGradient(listOf(RaahiCyan, RaahiVioletAccent))
fun raahiHeroGradient() = Brush.linearGradient(
    listOf(RaahiOrange.copy(alpha = 0.14f), RaahiPink.copy(alpha = 0.05f), RaahiGlassStrong)
)

// Shape system: rounded corners 14–22dp, pill for chips/badges.
val RaahiRadiusSmall = 12.dp
val RaahiRadiusMedium = 15.dp
val RaahiRadiusLarge = 20.dp
val RaahiShapeSmall = RoundedCornerShape(RaahiRadiusSmall)
val RaahiShapeMedium = RoundedCornerShape(RaahiRadiusMedium)
val RaahiShapeLarge = RoundedCornerShape(RaahiRadiusLarge)
val RaahiShapePill = RoundedCornerShape(50)

// --- Typography -----------------------------------------------------------------------
// Downloaded at runtime via Play Services' Fonts provider — see res/values/font_certs.xml
// for the one-time setup this needs. GoogleFont lookups fail gracefully (falls back to
// FontFamily.Default) if that file hasn't been populated yet, so this is safe either way.
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun googleFontFamily(name: String): FontFamily {
    val font = GoogleFont(name)
    return FontFamily(
        Font(googleFont = font, fontProvider = fontProvider, weight = FontWeight.Medium),
        Font(googleFont = font, fontProvider = fontProvider, weight = FontWeight.SemiBold),
        Font(googleFont = font, fontProvider = fontProvider, weight = FontWeight.Bold),
    )
}

/** Headlines, numbers (scores/prices/odometer), step badges — the mockup's "Space Grotesk". */
val RaahiDisplayFont: FontFamily = runCatching { googleFontFamily("Space Grotesk") }
    .getOrDefault(FontFamily.Default)

/** Body text everywhere else — the mockup's "Plus Jakarta Sans". */
val RaahiBodyFont: FontFamily = runCatching { googleFontFamily("Plus Jakarta Sans") }
    .getOrDefault(FontFamily.Default)

private val RaahiDarkColors = darkColorScheme(
    background = RaahiBg,
    surface = RaahiBg2,
    primary = RaahiOrange,
    onPrimary = Color.White,
    onBackground = RaahiText,
    onSurface = RaahiText,
    outline = RaahiBorder,
    error = RaahiRed,
)

@Composable
fun RaahiTheme(content: @Composable () -> Unit) {
    // Raahi is dark-first by design (matches the reference spec), not device-theme-driven.
    MaterialTheme(
        colorScheme = RaahiDarkColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
