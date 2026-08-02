package app.waveflow.ui.player

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Couleur d'accent extraite de la pochette courante, animée à chaque
 * changement de morceau.
 *
 * C'est ce qui donne au lecteur son fond qui « prend la couleur » de l'album.
 * En l'absence de pochette exploitable, on retombe sur la teinte du thème.
 */
@Composable
fun rememberArtworkAccent(artworkUri: Uri?): Color {
    val context = LocalContext.current
    val fallback = MaterialTheme.colorScheme.surfaceVariant

    var target by remember { mutableStateOf(fallback) }

    LaunchedEffect(artworkUri, fallback) {
        target = artworkUri?.let { extractAccent(context, it) } ?: fallback
    }

    val accent by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = ACCENT_FADE_MS),
        label = "artworkAccent",
    )
    return accent
}

/**
 * Dégradé de fond du lecteur : l'accent en haut, fondu vers le fond du thème
 * à mi-hauteur pour que le texte du bas reste lisible.
 */
@Composable
fun artworkGradient(accent: Color): Brush {
    val background = MaterialTheme.colorScheme.background
    return Brush.verticalGradient(
        0f to lerp(accent, background, ACCENT_BLEND),
        GRADIENT_END_STOP to background,
    )
}

private suspend fun extractAccent(context: Context, artworkUri: Uri): Color? {
    val request = ImageRequest.Builder(context)
        .data(artworkUri)
        // Palette lit les pixels : un bitmap matériel n'est pas accessible en CPU.
        .allowHardware(false)
        .size(SAMPLE_SIZE)
        .build()

    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
    val bitmap = (result as? SuccessResult)?.let { (it.drawable as? BitmapDrawable)?.bitmap }
        ?: return null

    val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }

    // Vibrant d'abord (le plus caractéristique de la pochette), puis des
    // solutions de repli de plus en plus neutres.
    val swatch = palette.vibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
        ?: return null

    return Color(swatch.rgb)
}

/** Taille d'échantillonnage : inutile de décoder la pochette en pleine résolution. */
private const val SAMPLE_SIZE = 128

/** Part de fond mélangée à l'accent : plus c'est haut, plus le fond est sobre. */
private const val ACCENT_BLEND = 0.55f

private const val GRADIENT_END_STOP = 0.6f

private const val ACCENT_FADE_MS = 600
