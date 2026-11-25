package app.waveflow.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.waveflow.ui.theme.LiquidAquaDeep
import app.waveflow.ui.theme.LiquidLavenderDeep
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.WaveFlowTheme
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * La progression du lecteur, dessinée selon la matière.
 *
 * - **Fluid** : l'onde. La partie déjà jouée ondule dans l'accent, le reste
 *   est un filet droit (`fluid-editorial.md`, « The mark »). Pendant la
 *   lecture, au plein écran, l'onde **coule** vers la tête de lecture ; elle
 *   s'immobilise en pause et quand les animations sont supprimées. Celle du
 *   mini-lecteur reste immobile : il est à l'écran pendant toute la
 *   navigation, et le document ne laisse rien tourner sans fin.
 * - **Liquid** : la ligne de lumière (`liquid-aurora-glass.md`). Une piste
 *   givrée, un remplissage aqua → lavande → accent, un bord lumineux qui dit
 *   où en est la musique, plus vif pendant qu'on cherche. Immobile : la ligne
 *   avance parce que la musique avance. Le document finit le dégradé sur la
 *   menthe ; il finit ici sur l'accent du lecteur, celui de la pochette — le
 *   document lâche justement la couleur de la pochette « au bout de la plaque
 *   du lecteur ».
 *
 * L'ondulation tourne sur l'horloge d'images, pas sur les tics de position.
 *
 * @param progress l'avancement, entre 0 et 1, lu au dessin seulement : la
 *   tête qui glisse ne recompose rien.
 * @param playing la musique joue — l'onde du plein écran coule.
 * @param loop les bornes A-B à marquer, `null` pour aucune.
 * @param scrubbing un doigt déplace la tête : elle grossit, la ligne s'avive.
 * @param compact la tranche du mini-lecteur : plus fine, sans tête ni bornes.
 */
@Composable
fun ProgressLine(
    progress: () -> Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
    loop: LoopMarks? = null,
    scrubbing: Boolean = false,
    compact: Boolean = false,
) {
    val material = WaveFlowTheme.material
    val reduced = WaveFlowTheme.motion.reduced
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val tone = MaterialTheme.colorScheme.secondaryContainer
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val mesureur = rememberTextMeasurer()
    val etiquette = MaterialTheme.typography.labelSmall

    val coule = playing && !reduced && !compact
    val phase = rememberWavePhase(flowing = material == WaveFlowMaterial.Fluid && coule)
    val allure = when {
        compact -> Allure.Compacte
        scrubbing -> Allure.Recherche
        else -> Allure.Repos
    }
    // Les bornes n'ont pas leur place sur la tranche du mini-lecteur.
    val bornes = loop.takeUnless { compact }

    Canvas(modifier = modifier) {
        val fait = progress().coerceIn(0f, 1f)
        bornes?.let { drawLoopBand(it, accent) }
        when (material) {
            WaveFlowMaterial.Fluid -> drawWave(fait, phase.floatValue, accent, ink, allure)
            WaveFlowMaterial.Liquid -> drawLightLine(fait, accent, tone, allure)
        }
        bornes?.let { drawLoopMarks(it, ink, label, mesureur, etiquette) }
    }
}

/**
 * La phase de l'onde, qui avance tant que [flowing] : un tour en deux
 * secondes, repris là où la dernière pause l'avait laissé. Lue au dessin
 * seulement — chaque image redessine la ligne, rien ne recompose.
 */
@Composable
private fun rememberWavePhase(flowing: Boolean): FloatState {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(flowing) {
        if (!flowing) return@LaunchedEffect
        val depart = phase.floatValue
        var debut = -1L
        while (true) {
            withInfiniteAnimationFrameMillis { maintenant ->
                if (debut < 0) debut = maintenant
                val tours = (maintenant - debut).toFloat() / WAVE_PERIOD_MS
                phase.floatValue = (depart + tours * 2f * PI.toFloat()) % (2f * PI.toFloat())
            }
        }
    }
    return phase
}

/**
 * L'avancement à afficher pour [positionMs] : il glisse d'un tic de position
 * au suivant au lieu de sauter, comme la ligne du bureau (une transition
 * linéaire de la durée d'un tic). Un saut — en arrière, ou plus loin que deux
 * tics, un changement de morceau — se fait d'un coup, et tout se fait d'un
 * coup en pause ou sous mouvement réduit.
 */
@Composable
fun rememberGlidingProgress(positionMs: Long, durationMs: Long, trackKey: String, playing: Boolean): State<Float> {
    val reduced = WaveFlowTheme.motion.reduced
    val cible = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val anime = remember(trackKey) { Animatable(cible) }
    LaunchedEffect(cible, playing, reduced, durationMs) {
        val ecartMs = (cible - anime.value) * durationMs
        val glisse = playing && !reduced
        val petitPas = ecartMs > 0f && ecartMs <= GLIDE_MAX_MS
        if (glisse && petitPas) {
            anime.animateTo(cible, tween(GLIDE_MS, easing = LinearEasing))
        } else {
            anime.snapTo(cible)
        }
    }
    return anime.asState()
}

/** Comment se présente la ligne : la tranche du mini-lecteur, au repos, ou sous le doigt. */
private enum class Allure { Compacte, Repos, Recherche }

// ── L'onde ──────────────────────────────────────────────────────────────────

private fun DrawScope.drawWave(
    fait: Float,
    phase: Float,
    accent: Color,
    ink: Color,
    allure: Allure,
) {
    val compact = allure == Allure.Compacte
    val scrubbing = allure == Allure.Recherche
    val milieu = size.height / 2f
    val tete = size.width * fait
    val longueur = (if (compact) WAVE_LENGTH_COMPACT else WAVE_LENGTH).dp.toPx()
    val amplitude = (if (compact) WAVE_AMPLITUDE_COMPACT else WAVE_AMPLITUDE).dp.toPx()

    // Le reste du morceau : un filet droit.
    drawLine(
        color = ink.copy(alpha = TRACK_ALPHA),
        start = Offset(tete, milieu),
        end = Offset(size.width, milieu),
        strokeWidth = TRACK_STROKE.dp.toPx(),
        cap = StrokeCap.Round,
    )

    // La partie jouée : l'onde, dont l'amplitude s'éteint aux deux bouts
    // pour qu'elle parte du filet et le rejoigne sans marche.
    if (tete > 0f) {
        val fondu = longueur / 2f
        val onde = Path()
        val pas = 2.dp.toPx()
        var x = 0f
        while (true) {
            val attenuation = min(1f, min(x, tete - x) / fondu).coerceAtLeast(0f)
            val y = milieu + amplitude * attenuation * sin(2f * PI.toFloat() * x / longueur - phase)
            if (x == 0f) onde.moveTo(x, y) else onde.lineTo(x, y)
            if (x >= tete) break
            x = min(x + pas, tete)
        }
        drawPath(
            path = onde,
            color = accent,
            style = Stroke(
                width = (if (compact) WAVE_STROKE_COMPACT else WAVE_STROKE).dp.toPx(),
                cap = StrokeCap.Round,
            ),
        )
    }

    // La tête : une ellipse d'encre, plus grande sous le doigt.
    if (!compact) {
        val demiLargeur = (if (scrubbing) HEAD_SCRUB_W else HEAD_W).dp.toPx() / 2f
        val demiHauteur = (if (scrubbing) HEAD_SCRUB_H else HEAD_H).dp.toPx() / 2f
        drawOval(
            color = ink,
            topLeft = Offset(tete - demiLargeur, milieu - demiHauteur),
            size = Size(demiLargeur * 2f, demiHauteur * 2f),
        )
    }
}

// ── La ligne de lumière ─────────────────────────────────────────────────────

private fun DrawScope.drawLightLine(
    fait: Float,
    accent: Color,
    tone: Color,
    allure: Allure,
) {
    val compact = allure == Allure.Compacte
    val scrubbing = allure == Allure.Recherche
    val milieu = size.height / 2f
    val epaisseur = when (allure) {
        Allure.Compacte -> LINE_COMPACT
        Allure.Recherche -> LINE_SCRUB
        Allure.Repos -> LINE
    }.dp.toPx()
    val rayon = CornerRadius(epaisseur / 2f)
    val haut = milieu - epaisseur / 2f
    val tete = size.width * fait

    // La piste givrée.
    drawRoundRect(color = tone, topLeft = Offset(0f, haut), size = Size(size.width, epaisseur), cornerRadius = rayon)

    if (tete > 0f) {
        // Le halo, sous le remplissage : deux voiles de lavande, du plus
        // large au plus serré, plus appuyés quand on cherche.
        val force = if (scrubbing) HALO_SCRUB else HALO
        listOf(HALO_WIDE to force * HALO_WIDE_SHARE, HALO_NEAR to force).forEach { (marge, alpha) ->
            val m = marge.dp.toPx()
            drawRoundRect(
                color = LiquidLavenderDeep.copy(alpha = alpha),
                topLeft = Offset(-m, haut - m),
                size = Size(tete + 2f * m, epaisseur + 2f * m),
                cornerRadius = CornerRadius(epaisseur / 2f + m),
            )
        }
        // Le remplissage : le dégradé court sur toute la largeur, et la part
        // jouée en découvre ce qu'elle couvre.
        drawRoundRect(
            brush = Brush.horizontalGradient(
                0f to LiquidAquaDeep,
                LIGHT_LINE_MID to LiquidLavenderDeep,
                1f to accent,
                startX = 0f,
                endX = size.width,
            ),
            topLeft = Offset(0f, haut),
            size = Size(tete, epaisseur),
            cornerRadius = rayon,
        )
    }

    // Le bord lumineux : un point blanc cerclé de lavande.
    if (!compact) {
        // Le rayon borné à la demi-largeur : sur une ligne plus étroite que la
        // tête, les bornes du centre s'inverseraient et coerceIn lèverait.
        val r = min((if (scrubbing) LIGHT_HEAD_SCRUB else LIGHT_HEAD).dp.toPx() / 2f, size.width / 2f)
        val centre = Offset(tete.coerceIn(r, size.width - r), milieu)
        drawCircle(LiquidLavenderDeep.copy(alpha = HEAD_GLOW), radius = r + HEAD_GLOW_SPREAD.dp.toPx(), center = centre)
        drawCircle(LiquidLavenderDeep.copy(alpha = HEAD_RING), radius = r + HEAD_RING_WIDTH.dp.toPx(), center = centre)
        drawCircle(Color.White, radius = r, center = centre)
    }
}

// ── La boucle A-B ───────────────────────────────────────────────────────────

/** La plage bouclée, un voile d'accent sur la hauteur des bornes. */
private fun DrawScope.drawLoopBand(loop: LoopMarks, accent: Color) {
    val fin = loop.end ?: return
    val hauteur = LOOP_MARK_HEIGHT.dp.toPx()
    drawRect(
        color = accent.copy(alpha = LOOP_BAND_ALPHA),
        topLeft = Offset(size.width * loop.start, size.height / 2f - hauteur / 2f),
        size = Size(size.width * (fin - loop.start), hauteur),
    )
}

/**
 * Les bornes : un trait d'encre en travers de la ligne, sa lettre au-dessus.
 * L'encre et non l'accent : une borne se lit sur l'onde, qui est déjà dans
 * l'accent. La lettre de A se pose à gauche de son trait, celle de B à
 * droite : deux bornes rapprochées — une phrase de quelques secondes —
 * gardent des lettres qui ne se touchent pas, même à police doublée.
 */
private fun DrawScope.drawLoopMarks(
    loop: LoopMarks,
    ink: Color,
    label: Color,
    mesureur: TextMeasurer,
    style: androidx.compose.ui.text.TextStyle,
) {
    val hauteur = LOOP_MARK_HEIGHT.dp.toPx()
    val haut = size.height / 2f - hauteur / 2f
    val bornes = listOfNotNull(loop.start, loop.end)
    bornes.forEach { part ->
        val x = size.width * part
        drawLine(
            color = ink,
            start = Offset(x, haut),
            end = Offset(x, haut + hauteur),
            strokeWidth = LOOP_MARK_STROKE.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    val a = mesureur.measure("A", style.copy(color = label))
    val b = loop.end?.let { mesureur.measure("B", style.copy(color = label)) }
    val (gaucheA, gaucheB) = loopLetterLefts(
        aX = size.width * loop.start,
        aWidth = a.size.width.toFloat(),
        bX = loop.end?.let { size.width * it },
        bWidth = b?.size?.width?.toFloat() ?: 0f,
        width = size.width,
    )
    drawText(a, topLeft = Offset(gaucheA, haut - a.size.height))
    if (b != null && gaucheB != null) drawText(b, topLeft = Offset(gaucheB, haut - b.size.height))
}

/**
 * Le bord gauche des lettres A et B : A à gauche de son trait, B à droite,
 * chacune bornée à la ligne. Près d'un bord, une boucle de quelques secondes
 * les ferait se chevaucher une fois bornées : B est alors poussée juste après
 * A, ou A juste avant B quand c'est B qui bute sur le bord droit.
 */
internal fun loopLetterLefts(aX: Float, aWidth: Float, bX: Float?, bWidth: Float, width: Float): Pair<Float, Float?> {
    var gaucheA = (aX - aWidth).coerceIn(0f, (width - aWidth).coerceAtLeast(0f))
    var gaucheB = bX?.coerceIn(0f, (width - bWidth).coerceAtLeast(0f)) ?: return gaucheA to null
    if (gaucheB < gaucheA + aWidth) {
        gaucheB = gaucheA + aWidth
        if (gaucheB + bWidth > width) {
            gaucheB = (width - bWidth).coerceAtLeast(0f)
            gaucheA = (gaucheB - aWidth).coerceAtLeast(0f)
        }
    }
    return gaucheA to gaucheB
}

/** La durée d'un tic de position (`Media3PlaybackController.POSITION_POLL_MS`). */
private const val GLIDE_MS = 500

/** Au-delà, ce n'est plus la musique qui avance : c'est un saut. */
private const val GLIDE_MAX_MS = 1_500f

/** L'onde fait un tour de phase en deux secondes : elle coule sans s'agiter. */
private const val WAVE_PERIOD_MS = 2_000f
private const val WAVE_LENGTH = 24f
private const val WAVE_LENGTH_COMPACT = 18f
private const val WAVE_AMPLITUDE = 3f
private const val WAVE_AMPLITUDE_COMPACT = 1.5f
private const val WAVE_STROKE = 2.5f
private const val WAVE_STROKE_COMPACT = 2f
private const val TRACK_STROKE = 1.5f

/** Le filet du reste du morceau : l'encre à 16 %, comme le bureau. */
private const val TRACK_ALPHA = 0.16f

private const val HEAD_W = 6f
private const val HEAD_H = 12f
private const val HEAD_SCRUB_W = 9f
private const val HEAD_SCRUB_H = 18f

private const val LINE = 6f
private const val LINE_SCRUB = 8f
private const val LINE_COMPACT = 3f
private const val LIGHT_LINE_MID = 0.6f
private const val HALO = 0.22f

/** Le voile large du halo, à moitié de la force du voile serré. */
private const val HALO_WIDE_SHARE = 0.5f
private const val HALO_SCRUB = 0.36f
private const val HALO_WIDE = 5f
private const val HALO_NEAR = 2f
private const val LIGHT_HEAD = 10f
private const val LIGHT_HEAD_SCRUB = 14f
private const val HEAD_GLOW = 0.25f
private const val HEAD_GLOW_SPREAD = 5f
private const val HEAD_RING = 0.35f
private const val HEAD_RING_WIDTH = 2f

private const val LOOP_MARK_HEIGHT = 16f
private const val LOOP_MARK_STROKE = 2f
private const val LOOP_BAND_ALPHA = 0.22f
