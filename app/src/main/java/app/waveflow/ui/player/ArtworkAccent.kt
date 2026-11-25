package app.waveflow.ui.player

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.WaveFlowTheme
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La lumière du lecteur pour la pochette courante : son accent et ses deux
 * tons de lumière, fondus en 900 ms à chaque changement de pochette.
 *
 * Elle ne dépend que de la pochette et de la matière, jamais de l'état de
 * lecture : en pause, quand [PlayerUiState] ne se reconstruit plus, un
 * changement de morceau rallume tout de même sa lumière — le fondu tourne sur
 * l'horloge d'images, pas sur les tics de position.
 *
 * L'accent fondu est repassé par [garantirContraste] à chaque image : deux
 * accents lisibles ne garantissent pas que leur mélange le soit.
 */
@Composable
fun rememberPlayerLight(artworkUri: Uri?): PlayerLight {
    val context = LocalContext.current
    val material = WaveFlowTheme.material

    // Un échantillon déjà lu sert de valeur initiale : en passant du
    // mini-lecteur au plein écran, la lumière est bonne dès la première image
    // au lieu de repartir de celle du thème.
    var sample by remember { mutableStateOf(artworkUri?.let { sampleCache[it] }) }

    LaunchedEffect(artworkUri) {
        sample = if (artworkUri == null) {
            null
        } else {
            sampleCache[artworkUri]
                ?: sampleCover(context, artworkUri)?.also { sampleCache[artworkUri] = it }
        }
    }

    // La cible ne change qu'avec la pochette ou la matière : pas à chaque
    // image du fondu, qui recompose cette fonction.
    val cible = remember(sample, material) { playerLightFor(sample, material) }
    val accent by animateColorAsState(cible.accent, WaveFlowTheme.motion.light(), label = "accentPochette")
    val glow by animateColorAsState(cible.glow, WaveFlowTheme.motion.light(), label = "lumierePochette")
    val glowDeep by animateColorAsState(cible.glowDeep, WaveFlowTheme.motion.light(), label = "lumiereProfonde")

    return PlayerLight(
        accent = garantirContraste(accent, material),
        onAccent = cible.onAccent,
        glow = glow,
        glowDeep = glowDeep,
    )
}

/**
 * Pose l'accent de la pochette sur [content], et sur lui seul.
 *
 * Tout ce que le lecteur colore passe par les rôles de Material — le bouton
 * plein, les bascules allumées, l'indicateur d'attente, le W, la progression :
 * les remplacer ici suffit, sans qu'aucun composant ait à connaître la
 * pochette. `secondary` et `tertiary` suivent, comme dans le thème, pour
 * qu'aucun composant ne retrouve l'accent fixe par un autre rôle.
 */
@Composable
fun PlayerAccent(light: PlayerLight, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = scheme.copy(
            primary = light.accent,
            onPrimary = light.onAccent,
            secondary = light.accent,
            onSecondary = light.onAccent,
            tertiary = light.accent,
            onTertiary = light.onAccent,
        ),
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

/**
 * Peint la page du lecteur : le fond du thème, et par-dessus les nappes de
 * lumière de la pochette ([lightPools]), bornées pour que le texte et
 * l'accent restent lisibles. Les nappes sont des ellipses floues : un disque
 * à dégradé radial, étiré à la mesure de l'écran.
 */
fun Modifier.playerLight(light: PlayerLight, material: WaveFlowMaterial, ground: Color): Modifier =
    drawBehind {
        drawRect(ground)
        for (nappe in lightPools(light, material, ground)) {
            val centre = Offset(size.width * nappe.x, size.height * nappe.y)
            val rx = size.width * nappe.rx
            val ry = size.height * nappe.ry
            // Un disque de rayon rx, aplati verticalement par l'échelle.
            scale(scaleX = 1f, scaleY = ry / rx, pivot = centre) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to nappe.color.copy(alpha = nappe.alpha),
                        POOL_FADE to Color.Transparent,
                        center = centre,
                        radius = rx,
                    ),
                    radius = rx,
                    center = centre,
                )
            }
        }
    }

private suspend fun sampleCover(context: Context, artworkUri: Uri): CoverSample? {
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

    // La teinte de l'accent : la plus caractéristique d'abord, puis des
    // replis de plus en plus neutres — une teinte trop grise sera écartée par
    // la règle du palier, qui rendra l'accent du thème.
    val vive = palette.vibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch

    // Le ton d'ensemble, celui que le bureau éclaire : la couleur qui couvre
    // le plus de pochette.
    val ensemble = palette.dominantSwatch

    if (vive == null && ensemble == null) return null
    return CoverSample(vivid = vive?.rgb, average = ensemble?.rgb)
}

/**
 * Pochettes déjà lues, partagées entre le mini-lecteur et le plein écran.
 *
 * L'échantillon est gardé brut, avant toute décision de matière : passer du
 * clair au sombre ne relit pas la pochette.
 *
 * Lu pendant la composition et écrit depuis [LaunchedEffect], tous deux sur le
 * thread principal : aucune synchronisation nécessaire. Borné en taille (ordre
 * d'accès) pour ne pas retenir les URI d'une bibliothèque entière.
 */
private val sampleCache = object : LinkedHashMap<Uri, CoverSample>(SAMPLE_CACHE_SIZE, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Uri, CoverSample>): Boolean =
        size > SAMPLE_CACHE_SIZE
}

private const val SAMPLE_CACHE_SIZE = 32

/** Taille d'échantillonnage : inutile de décoder la pochette en pleine résolution. */
private const val SAMPLE_SIZE = 128
