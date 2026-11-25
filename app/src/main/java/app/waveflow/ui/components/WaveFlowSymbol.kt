package app.waveflow.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import app.waveflow.ui.theme.WaveFlowTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Le symbole de WaveFlow : le W du nom tracé comme une onde, une période et
 * demie de sinus d'un seul trait — le dessin pour petites tailles, au trait de
 * 46 sur 256 —, dans l'accent.
 *
 * Avec [playing], ses deux creux et sa crête **respirent** à tour de rôle,
 * jusqu'à 73 % de leur hauteur en 1,5 s ; une pause le ramène au W dessiné
 * plutôt que de le figer en pleine respiration. C'est le composant
 * `WaveFlowLogo` du bureau (`docs/features/ui.md`, « Brand mark »), porté tel
 * quel : mêmes hauteurs, même décalage, même retour au repos.
 *
 * La respiration tourne sur l'horloge d'images et ne dépend que de [playing],
 * jamais des tics de position : ceux-ci s'arrêtent en pause, et c'est
 * justement en pause que le W doit retomber. Immobile quand le système
 * demande de supprimer les animations.
 *
 * Décoratif : celui qui le pose écrit le nom, ou ce que le symbole marque, à
 * côté.
 */
@Composable
fun WaveFlowSymbol(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val reduced = WaveFlowTheme.motion.reduced
    var hauteurs by remember { mutableStateOf(WRest) }
    val respire = breathes(playing, reduced)

    LaunchedEffect(respire, reduced) {
        if (reduced) {
            hauteurs = WRest
            return@LaunchedEffect
        }
        var debut = -1L
        var dernier = -1L
        var auRepos = false
        while (!auRepos) {
            // Une respiration est sans fin : l'horloge des animations infinies
            // la laisse aux tests, qui l'interrompent. Le retour au repos, lui,
            // a une fin.
            val maintenant = if (respire) {
                withInfiniteAnimationFrameMillis { it }
            } else {
                withFrameMillis { it }
            }
            if (dernier >= 0 && maintenant - dernier < FRAME_MS) continue
            if (debut < 0) debut = maintenant
            // Une fenêtre cachée ne reçoit pas d'images : le pas est borné
            // pour que le W ne saute pas à son retour.
            val pas = if (dernier < 0) FRAME_MS else min(maintenant - dernier, MAX_STEP_MS)
            dernier = maintenant

            val cible = if (respire) breathTargets(maintenant - debut) else WRest
            val suivantes = settleTowards(hauteurs, cible, pas)
            auRepos = !respire && suivantes.isAtRest()
            hauteurs = if (auRepos) WRest else suivantes
        }
    }

    Canvas(modifier = modifier) {
        val echelle = size.minDimension / W_BOX
        val decalage = Offset((size.width - W_BOX * echelle) / 2f, (size.height - W_BOX * echelle) / 2f)
        withTransform({
            translate(decalage.x, decalage.y)
            scale(echelle, echelle, pivot = Offset.Zero)
        }) {
            drawPath(
                path = wPath(hauteurs),
                color = color,
                style = Stroke(width = W_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * Les hauteurs des trois extrêmes du W — premier creux, crête, second creux —
 * en unités de la boîte de 256.
 */
internal data class WHeights(val trough1: Float, val crest: Float, val trough2: Float) {

    fun isAtRest(): Boolean = listOf(trough1, crest, trough2).all { abs(it - W_AMP) < REST_EPSILON }
}

/** Le W dessiné, chaque extrême à sa pleine hauteur. */
internal val WRest = WHeights(W_AMP, W_AMP, W_AMP)

/** Le W respire quand la musique joue, et jamais sous mouvement réduit. */
internal fun breathes(playing: Boolean, reduced: Boolean): Boolean = playing && !reduced

/**
 * Où en est la respiration [elapsedMs] après son départ : chaque extrême
 * descend jusqu'à 73 % de sa hauteur (60 − 16) et remonte, en 1,5 s, un
 * peu plus d'un radian après son voisin.
 */
internal fun breathTargets(elapsedMs: Long): WHeights {
    val phase = elapsedMs.toFloat() / BREATH_PERIOD_MS * 2f * PI.toFloat()
    fun h(i: Int) = W_AMP - BREATH_DEPTH * (1f + sin(phase - i * BREATH_LAG)) / 2f
    return WHeights(h(0), h(1), h(2))
}

/** Les hauteurs suivent leur cible en 120 ms : c'est ce qui laisse le W retomber. */
internal fun settleTowards(current: WHeights, target: WHeights, stepMs: Long): WHeights {
    val suivi = min(1f, stepMs.toFloat() / SETTLE_MS)
    fun suit(h: Float, t: Float) = h + (t - h) * suivi
    return WHeights(
        suit(current.trough1, target.trough1),
        suit(current.crest, target.crest),
        suit(current.trough2, target.trough2),
    )
}

/**
 * Le trait central du W dans sa boîte de 256, chaque extrême à sa hauteur.
 * Ses bouts sont sur les points d'inflexion, pour qu'il se lise W et non M.
 *
 * Les abscisses et les poignées sont celles du tracé du bureau
 * (`WaveFlowLogo.tsx`), recopiées : elles sont le dessin, pas des réglages.
 */
@Suppress("MagicNumber")
internal fun wPath(h: WHeights): Path = Path().apply {
    fun bas(a: Float) = W_MID + a
    fun haut(a: Float) = W_MID - a
    fun poigneeBas(a: Float) = W_MID + a * W_HANDLE
    fun poigneeHaut(a: Float) = W_MID - a * W_HANDLE
    moveTo(35f, W_MID)
    cubicTo(45.23f, poigneeBas(h.trough1), 54.84f, bas(h.trough1), 66f, bas(h.trough1))
    cubicTo(77.16f, bas(h.trough1), 86.77f, poigneeBas(h.trough1), 97f, W_MID)
    cubicTo(107.23f, poigneeHaut(h.crest), 116.84f, haut(h.crest), 128f, haut(h.crest))
    cubicTo(139.16f, haut(h.crest), 148.77f, poigneeHaut(h.crest), 159f, W_MID)
    cubicTo(169.23f, poigneeBas(h.trough2), 178.84f, bas(h.trough2), 190f, bas(h.trough2))
    cubicTo(201.16f, bas(h.trough2), 210.77f, poigneeBas(h.trough2), 221f, W_MID)
}

private const val W_BOX = 256f
private const val W_MID = 124f
internal const val W_AMP = 60f
private const val W_HANDLE = 31.1f / 60f
private const val W_STROKE = 46f

private const val BREATH_PERIOD_MS = 1500f
internal const val BREATH_DEPTH = 16f
private const val BREATH_LAG = 1.05f
private const val SETTLE_MS = 120f

/** Trente images par seconde suffisent à une respiration de quelques dp. */
private const val FRAME_MS = 33L
private const val MAX_STEP_MS = 100L
private const val REST_EPSILON = 0.05f
