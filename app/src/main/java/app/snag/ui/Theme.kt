package app.snag.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SnagDark = darkColorScheme(
    primary = Color(0xFFC9B8FF), onPrimary = Color(0xFF26164C),
    primaryContainer = Color(0xFF352951), onPrimaryContainer = Color(0xFFE9DFFF),
    secondary = Color(0xFFB7CEC4), onSecondary = Color(0xFF20362D),
    secondaryContainer = Color(0xFF293B33), onSecondaryContainer = Color(0xFFD6EBDF),
    tertiary = Color(0xFFE2BDD1),
    background = Color(0xFF111115), onBackground = Color(0xFFF0EDF5),
    surface = Color(0xFF111115), onSurface = Color(0xFFF0EDF5),
    surfaceContainerLowest = Color(0xFF0D0D11), surfaceContainerLow = Color(0xFF19191F),
    surfaceContainer = Color(0xFF202027), surfaceContainerHigh = Color(0xFF27272F),
    surfaceContainerHighest = Color(0xFF32323C), surfaceVariant = Color(0xFF30303A),
    onSurfaceVariant = Color(0xFFB9B5C4), outline = Color(0xFF898492),
    outlineVariant = Color(0xFF393640),
)

private val SnagLight = lightColorScheme(
    primary = Color(0xFF6546A5), onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDFFF), onPrimaryContainer = Color(0xFF29134E),
    secondary = Color(0xFF466454), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0EADB), onSecondaryContainer = Color(0xFF173629),
    tertiary = Color(0xFF80536D),
    background = Color(0xFFFAF8FC), onBackground = Color(0xFF201D26),
    surface = Color(0xFFFAF8FC), onSurface = Color(0xFF201D26),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F0F7),
    surfaceContainer = Color(0xFFEDE9F2), surfaceContainerHigh = Color(0xFFE7E2ED),
    surfaceContainerHighest = Color(0xFFE0DAE8), surfaceVariant = Color(0xFFE8E1EE),
    onSurfaceVariant = Color(0xFF615B6B), outline = Color(0xFF7C7485),
    outlineVariant = Color(0xFFD4CCDD),
)

private val SnagTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp),
)

private val PaperColors = lightColorScheme(
    primary = Color(0xFFB7351B), onPrimary = Color.White,
    primaryContainer = Color(0xFFF4DDD0), onPrimaryContainer = Color(0xFF442015),
    secondary = Color(0xFF596047), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E6D5), onSecondaryContainer = Color(0xFF252C1D),
    background = Color(0xFFF5F2E9), onBackground = Color(0xFF24251F),
    surface = Color(0xFFF5F2E9), onSurface = Color(0xFF24251F),
    surfaceContainerLowest = Color(0xFFFFFDF7), surfaceContainerLow = Color(0xFFF0EDE3),
    surfaceContainer = Color(0xFFEAE6DC), surfaceContainerHigh = Color(0xFFE2DED3),
    surfaceContainerHighest = Color(0xFFDAD5C9), surfaceVariant = Color(0xFFEAE6DC),
    onSurfaceVariant = Color(0xFF626257), outline = Color(0xFF7A7A6D), outlineVariant = Color(0xFFCCCABB),
)
private val CinemaColors = darkColorScheme(
    primary = Color(0xFFD3EF93), onPrimary = Color(0xFF23300B),
    primaryContainer = Color(0xFF354324), onPrimaryContainer = Color(0xFFE2F7BC),
    secondary = Color(0xFFB4D5C3), onSecondary = Color(0xFF18372A),
    secondaryContainer = Color(0xFF2B4037), onSecondaryContainer = Color(0xFFD7EBDF),
    background = Color(0xFF111612), onBackground = Color(0xFFEAF0E7),
    surface = Color(0xFF111612), onSurface = Color(0xFFEAF0E7),
    surfaceContainerLowest = Color(0xFF0D110D), surfaceContainerLow = Color(0xFF192019),
    surfaceContainer = Color(0xFF222B23), surfaceContainerHigh = Color(0xFF2B352D),
    surfaceContainerHighest = Color(0xFF364237), surfaceVariant = Color(0xFF303C31),
    onSurfaceVariant = Color(0xFFBDC8BC), outline = Color(0xFF8C9A8B), outlineVariant = Color(0xFF404D40),
)
private val PaperDark = CinemaColors.copy(
    primary = Color(0xFFFFA07A), onPrimary = Color(0xFF4B1D0D),
    primaryContainer = Color(0xFF603521), onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFCAD0B2), onSecondary = Color(0xFF2D321F),
    secondaryContainer = Color(0xFF3D4430), onSecondaryContainer = Color(0xFFE3E8CD),
    background = Color(0xFF1B1C18), onBackground = Color(0xFFF2F0E6),
    surface = Color(0xFF1B1C18), onSurface = Color(0xFFF2F0E6),
    surfaceContainerLowest = Color(0xFF141511), surfaceContainerLow = Color(0xFF22231D),
    surfaceContainer = Color(0xFF2A2C24), surfaceContainerHigh = Color(0xFF33352B),
    surfaceContainerHighest = Color(0xFF3E4035), surfaceVariant = Color(0xFF383A30),
    onSurfaceVariant = Color(0xFFC5C7B6), outline = Color(0xFF979A87), outlineVariant = Color(0xFF45483A),
)
private val SignalColors = PaperColors.copy(
    primary = Color(0xFF283ABD), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF), onPrimaryContainer = Color(0xFF132168),
    secondary = Color(0xFF283ABD), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE1FF), onSecondaryContainer = Color(0xFF132168),
    background = Color(0xFFF7F7F1), surface = Color(0xFFF7F7F1),
    surfaceContainerLow = Color(0xFFF0F0E7), surfaceContainer = Color(0xFFE7E8DE),
    outline = Color(0xFF54574C), outlineVariant = Color(0xFFBCBFB3),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SnagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val preference by app.snag.core.AppPreferences.theme.collectAsState()
    val useDark = when(preference) { "dark" -> true; "light" -> false; else -> darkTheme }
    MaterialExpressiveTheme(
        colorScheme = when { DesignStudy.paper -> if(useDark) PaperDark else PaperColors; DesignStudy.cinema -> CinemaColors
            DesignStudy.signal -> SignalColors; useDark -> SnagDark; else -> SnagLight },
        typography = if (!DesignStudy.active) SnagTypography else SnagTypography.copy(
            headlineLarge = SnagTypography.headlineLarge.copy(
                fontSize = if (DesignStudy.signal) 36.sp else 32.sp,
                lineHeight = if (DesignStudy.signal) 40.sp else 37.sp,
                fontWeight = if (DesignStudy.signal) FontWeight.Black else FontWeight.SemiBold),
            labelLarge = SnagTypography.labelLarge.copy(
                fontFamily = if (DesignStudy.paper) FontFamily.Monospace else FontFamily.SansSerif),
        ),
        motionScheme = MotionScheme.standard(),
        content = content,
    )
}
