package app.waveflow.prototype

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import app.waveflow.ui.theme.WaveFlowTheme
import app.waveflow.ui.theme.maxChroma
import app.waveflow.ui.theme.oklchOf
import app.waveflow.ui.theme.oklchToColor
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/*
 * L'atmosphère de la pochette, telle que Pocket la propose : **une seule nappe**
 * de lumière, là où la pochette est montrée, et jamais l'accent.
 *
 * La nappe prend le ton d'ensemble de la pochette (la nuance dominante de
 * Palette) ramené à une clarté fixe — sombre en sombre, pâle en clair — pour
 * que deux pochettes ne changent pas l'équilibre de l'écran. Ces clartés et
 * ces vivacités sont réglées pour que la seconde encre garde 4,5:1 au cœur de
 * la nappe, à pleine opacité, quelle que soit la teinte (`AtmosphereTest`) :
 * la lisibilité tient à la recette, sans borne à calculer au dessin.
 */

/** La clarté OKLCH de la nappe en sombre : un ton profond, jamais une lueur. */
private const val DARK_POOL_L = 0.34f

/** La clarté OKLCH de la nappe en clair : un lavis. */
private const val LIGHT_POOL_L = 0.90f

/** La vivacité au plus : une pochette saturée ne fait pas une nappe criarde. */
private const val DARK_POOL_MAX_C = 0.10f
private const val LIGHT_POOL_MAX_C = 0.05f

/** L'opacité de la nappe en son cœur. */
internal const val POOL_ALPHA = 0.9f

/**
 * La nappe tirée de [dominant] (ARGB) pour l'apparence [dark], ou [fallback]
 * quand la pochette n'a pas de couleur à donner.
 */
internal fun poolColor(dominant: Int?, dark: Boolean, fallback: Color): Color {
    if (dominant == null) return fallback
    val couleur = oklchOf(dominant)
    val l = if (dark) DARK_POOL_L else LIGHT_POOL_L
    val plafond = if (dark) DARK_POOL_MAX_C else LIGHT_POOL_MAX_C
    val c = min(min(couleur.c, plafond), maxChroma(l, couleur.h))
    return oklchToColor(l, c, couleur.h)
}

/**
 * La nappe pour la pochette [artworkUri], fondue en 900 ms à chaque
 * changement — sur l'horloge d'images, donc aussi en pause.
 */
@Composable
fun rememberAtmosphere(artworkUri: Uri?): Color {
    val context = LocalContext.current
    val colors = Pocket.colors
    var dominant by remember { mutableStateOf(artworkUri?.let { dominantCache[it] }) }

    LaunchedEffect(artworkUri) {
        dominant = if (artworkUri == null) {
            null
        } else {
            dominantCache[artworkUri] ?: sampleDominant(context, artworkUri)?.also { dominantCache[artworkUri] = it }
        }
    }

    val cible = remember(dominant, colors) { poolColor(dominant, colors.dark, colors.atmosphere) }
    val nappe by animateColorAsState(cible, WaveFlowTheme.motion.light(), label = "nappe")
    return nappe
}

/**
 * Peint la page : le canevas, puis la nappe — une ellipse au centre
 * ([centerY] en part de la hauteur), qui déborde vers le haut et s'éteint avant
 * le bas de l'écran. Préparée une fois par couleur et par taille.
 */
fun Modifier.atmosphere(pool: Color, canvas: Color, centerY: Float = 0.34f): Modifier =
    drawWithCache {
        val alpha = POOL_ALPHA
        val centre = Offset(size.width / 2f, size.height * centerY)
        val rayon = size.width * POOL_RADIUS
        val aplatissement = size.height * POOL_HEIGHT / rayon
        val brosse = Brush.radialGradient(
            0f to pool.copy(alpha = alpha),
            POOL_CORE to pool.copy(alpha = alpha * POOL_CORE_ALPHA),
            1f to Color.Transparent,
            center = centre,
            radius = rayon,
        )
        onDrawBehind {
            drawRect(canvas)
            scale(scaleX = 1f, scaleY = aplatissement, pivot = centre) {
                drawCircle(brush = brosse, radius = rayon, center = centre)
            }
        }
    }

/** La demi-largeur de la nappe, en part de la largeur : elle déborde des bords. */
private const val POOL_RADIUS = 0.95f

/** La demi-hauteur de la nappe, en part de la hauteur. */
private const val POOL_HEIGHT = 0.42f

/** Où le cœur plein de la nappe cède au dégradé. */
private const val POOL_CORE = 0.35f
private const val POOL_CORE_ALPHA = 0.7f

private suspend fun sampleDominant(context: Context, artworkUri: Uri): Int? {
    val request = ImageRequest.Builder(context)
        .data(artworkUri)
        // Palette lit les pixels : un bitmap matériel n'est pas accessible en CPU.
        .allowHardware(false)
        .size(SAMPLE_SIZE)
        .build()
    // `execute` rend une erreur plutôt que de lever ; seule l'annulation lève, et
    // elle doit remonter : la convertir en « pas de couleur » laissait un
    // chargement abandonné écraser la couleur de la pochette suivante.
    val result = context.imageLoader.execute(request)
    val bitmap = (result as? SuccessResult)?.let { (it.drawable as? BitmapDrawable)?.bitmap } ?: return null
    val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
    return palette.dominantSwatch?.rgb
}

/**
 * Pochettes déjà lues, partagées entre le mini-lecteur et le plein écran. Lu
 * en composition et écrit depuis [LaunchedEffect], tous deux sur le fil
 * principal.
 */
private val dominantCache = object : LinkedHashMap<Uri, Int>(SAMPLE_CACHE_SIZE, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Uri, Int>): Boolean = size > SAMPLE_CACHE_SIZE
}

private const val SAMPLE_CACHE_SIZE = 32
private const val SAMPLE_SIZE = 128
