package app.waveflow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Emerald,
    onPrimary = Color.Black,
    secondary = EmeraldDark,
    tertiary = EmeraldLight,
    background = SurfaceDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceDarkElevated,
    onBackground = OnDark,
    onSurface = OnDark,
    onSurfaceVariant = OnDarkVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = Color.White,
    secondary = Emerald,
    tertiary = EmeraldLight,
    background = SurfaceLight,
    surface = SurfaceLight,
    onBackground = OnLight,
    onSurface = OnLight,
    onSurfaceVariant = OnLightVariant,
)

@Composable
fun WaveFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Désactivé par défaut : on garde l'identité émeraude WaveFlow plutôt que
    // les couleurs Material You du système. Activable si on veut le suivi thème.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
