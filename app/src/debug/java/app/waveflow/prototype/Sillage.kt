package app.waveflow.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.waveflow.ui.formatDuration
import app.waveflow.ui.player.LoopMarks
import app.waveflow.ui.player.keyboardSeekTarget
import app.waveflow.ui.player.rememberGlidingProgress
import app.waveflow.ui.theme.WaveFlowTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * La progression de Pocket : **le sillage**.
 *
 * La partie jouée est une ligne droite et nette ; seuls les derniers
 * millimètres avant la tête ondulent — l'onde que laisse le son en passant —,
 * et la tête est un point lumineux dans un halo de l'accent. Le bureau dessine
 * toute la partie jouée en onde ; sur un téléphone, une onde de trois cents dp
 * devient une texture, d'où ce resserrement (`docs/design/sonic-editorial.md`).
 *
 * - En pause, le sillage se pose : son amplitude retombe, la ligne redevient
 *   droite. Il n'ondule pas non plus quand les animations sont supprimées.
 * - Sous le doigt (ou au clavier), la tête grossit, le halo s'élargit.
 * - L'ondulation et le glissement de la tête sont lus **au dessin** : ils ne
 *   recomposent rien.
 *
 * Les gestes reprennent ceux du lecteur de production (`PlayerSeekBar`), avec
 * la même leçon : chaque `pointerInput` a le morceau dans ses clés, sinon un
 * geste commencé sur un morceau finissait dans le suivant.
 *
 * @param loop les bornes A-B à marquer, `null` pour aucune.
 * @param center ce qui se pose entre les deux durées, sur une rangée de
 *   hauteur constante : y paraître ne déplace rien.
 */
@Composable
fun Sillage(
    positionMs: Long,
    durationMs: Long,
    trackKey: String,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    loop: LoopMarks? = null,
    center: (@Composable () -> Unit)? = null,
) {
    val hasDuration = durationMs > 0L
    var scrub by remember(trackKey, hasDuration) { mutableStateOf<Float?>(null) }
    val glisse by rememberGlidingProgress(positionMs, durationMs, trackKey, playing)
    val affichee = scrub?.let { (it * durationMs).toLong() } ?: positionMs

    val duree by rememberUpdatedState(durationMs)
    val position by rememberUpdatedState(positionMs)
    val chercher by rememberUpdatedState(onSeek)

    val interactions = remember { MutableInteractionSource() }
    val focalisee by interactions.collectIsFocusedAsState()
    val vif = scrub != null || focalisee

    val reduced = WaveFlowTheme.motion.reduced
    val coule = playing && !reduced
    val amplitude by animateFloatAsState(if (coule) 1f else 0f, WaveFlowTheme.motion.tone(), label = "sillage")
    val phase = rememberWakePhase(coule)
    val tete by animateFloatAsState(if (vif) 1f else 0f, WaveFlowTheme.motion.tone(), label = "tete")

    val colors = Pocket.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOUCH_HEIGHT)
                .pointerInput(hasDuration, trackKey) {
                    if (!hasDuration) return@pointerInput
                    detectTapGestures { point -> chercher((part(point.x, size.width) * duree).toLong()) }
                }
                .pointerInput(hasDuration, trackKey) {
                    if (!hasDuration) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { point -> scrub = part(point.x, size.width) },
                        onDragEnd = {
                            scrub?.let { chercher((it * duree).toLong()) }
                            scrub = null
                        },
                        onDragCancel = { scrub = null },
                    ) { change, _ ->
                        change.consume()
                        scrub = part(change.position.x, size.width)
                    }
                }
                .onKeyEvent { evenement ->
                    if (!hasDuration || evenement.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val cible = keyboardSeekTarget(evenement.key, position, duree)
                    cible?.let(chercher)
                    cible != null
                }
                .focusable(enabled = hasDuration, interactionSource = interactions)
                .sillageSemantics(positionMs, affichee, durationMs) { chercher(it) },
        ) {
            drawSillage(
                frame = SillageFrame(scrub ?: glisse, amplitude, phase.floatValue, tete),
                look = SillageLook(colors.accent, colors.ink.copy(alpha = TRACK_ALPHA), compact = false),
                loop = loop,
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TIMES_HEIGHT),
        ) {
            Text(
                text = formatDuration(affichee),
                style = MaterialTheme.typography.labelMedium,
                color = colors.ink3,
            )
            // Les deux durées se mesurent d'abord ; les pastilles prennent ce qui
            // reste, et cèdent si la police agrandie manque de place.
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { center?.invoke() }
            Text(
                // Le temps restant, en négatif : ce qu'on se demande en
                // écoutant, plus que la durée qu'on lit une fois.
                text = if (hasDuration) "−" + formatDuration((durationMs - affichee).coerceAtLeast(0L)) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = colors.ink3,
            )
        }
    }
}

/**
 * Le sillage du mini-lecteur : une ligne de 2 dp et sa tête, sans onde — il
 * reste à l'écran pendant toute la navigation, et rien n'y tourne sans fin.
 */
@Composable
fun CompactSillage(progress: () -> Float, modifier: Modifier = Modifier) {
    val colors = Pocket.colors
    Canvas(modifier = modifier) {
        drawSillage(
            frame = SillageFrame(progress(), amplitude = 0f, phase = 0f, head = 0f),
            look = SillageLook(colors.accent, colors.ink.copy(alpha = TRACK_ALPHA), compact = true),
            loop = null,
        )
    }
}

/**
 * La phase de l'ondulation, sur l'horloge d'images, seulement tant qu'elle
 * coule : arrêtée, elle ne coûte aucune image.
 */
@Composable
private fun rememberWakePhase(flowing: Boolean): androidx.compose.runtime.MutableFloatState {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(flowing) {
        if (!flowing) return@LaunchedEffect
        val depart = withFrameMillis { it } - (phase.floatValue * WAKE_PERIOD_MS).toLong()
        while (true) {
            withFrameMillis { t -> phase.floatValue = ((t - depart) % WAKE_PERIOD_MS.toLong()) / WAKE_PERIOD_MS }
        }
    }
    return phase
}

/**
 * Dessine la ligne, la partie jouée, le sillage et la tête.
 *
 * L'instant du dessin, dans [SillageFrame] :
 *
 * @param amplitude de 0 (posé) à 1 (qui coule) : l'onde du sillage.
 * @param phase de 0 à 1, l'avancement de l'ondulation.
 * @param head de 0 (au repos) à 1 (sous le doigt) : la taille de la tête.
 */
internal fun DrawScope.drawSillage(frame: SillageFrame, look: SillageLook, loop: LoopMarks?) {
    val progress = frame.progress
    val amplitude = frame.amplitude
    val phase = frame.phase
    val head = frame.head
    val (accent, track, compact) = look
    val y = size.height / 2f
    val trait = (if (compact) COMPACT_STROKE else STROKE).dp.toPx()
    val x = (progress.coerceIn(0f, 1f) * size.width)

    drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = trait, cap = StrokeCap.Round)

    loop?.let { marks ->
        val a = marks.start * size.width
        val b = (marks.end ?: marks.start) * size.width
        val bande = LOOP_BAND.dp.toPx()
        drawRect(
            accent.copy(alpha = LOOP_ALPHA),
            Offset(a, y - bande / 2f),
            androidx.compose.ui.geometry.Size(b - a, bande),
        )
        for (borne in listOfNotNull(a, marks.end?.let { it * size.width })) {
            drawLine(accent, Offset(borne, y - bande), Offset(borne, y + bande), strokeWidth = LOOP_STROKE.dp.toPx())
        }
    }

    val sillage = (WAKE_LENGTH.dp.toPx()).coerceAtMost(x)
    val debutSillage = x - sillage
    if (debutSillage > 0f) {
        drawLine(accent, Offset(0f, y), Offset(debutSillage, y), strokeWidth = trait, cap = StrokeCap.Round)
    }
    if (sillage > 0f) {
        val onde = Path()
        val ampl = WAKE_AMPLITUDE.dp.toPx() * amplitude
        val pas = WAKE_SEGMENTS
        for (i in 0..pas) {
            val t = i.toFloat() / pas
            // L'enveloppe ramène l'onde sur la ligne à ses deux bouts : le
            // sillage naît de la ligne et y retourne sous la tête.
            val enveloppe = sin(PI * t).toFloat()
            val dy = ampl * enveloppe * sin(2f * PI.toFloat() * (WAKE_CYCLES * t - phase))
            val px = debutSillage + sillage * t
            if (i == 0) onde.moveTo(px, y + dy) else onde.lineTo(px, y + dy)
        }
        drawPath(onde, accent, style = Stroke(width = trait, cap = StrokeCap.Round))
    }

    if (compact) {
        drawCircle(accent, radius = COMPACT_HEAD.dp.toPx(), center = Offset(x, y))
        return
    }
    val rayon = (HEAD + (HEAD_SCRUB - HEAD) * head).dp.toPx()
    val halo = (HALO + (HALO_SCRUB - HALO) * head).dp.toPx()
    drawCircle(accent.copy(alpha = HALO_ALPHA), radius = halo, center = Offset(x, y))
    drawCircle(accent, radius = rayon, center = Offset(x, y))
}

/** L'instant d'un dessin du sillage : où en est la musique, l'onde, la tête. */
internal data class SillageFrame(val progress: Float, val amplitude: Float, val phase: Float, val head: Float)

/** L'apparence du sillage : ses deux encres, et s'il est la tranche du mini-lecteur. */
internal data class SillageLook(val accent: Color, val track: Color, val compact: Boolean)

/**
 * Ce que le lecteur d'écran lit et règle : la position sur la durée, et
 * l'action qui la déplace. Sans durée, la ligne se dit désactivée.
 *
 * @param displayedMs la position affichée — celle du doigt pendant un
 *   glissement.
 */
private fun Modifier.sillageSemantics(positionMs: Long, displayedMs: Long, durationMs: Long, onSeek: (Long) -> Unit) =
    semantics {
        val hasDuration = durationMs > 0L
        contentDescription = "Position dans le morceau"
        stateDescription = if (hasDuration) {
            "${formatDuration(displayedMs)} sur ${formatDuration(durationMs)}"
        } else {
            "Durée inconnue"
        }
        progressBarRangeInfo = ProgressBarRangeInfo(
            current = if (hasDuration) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            range = 0f..1f,
        )
        if (hasDuration) {
            setProgress { valeur ->
                onSeek((valeur.coerceIn(0f, 1f) * durationMs).toLong())
                true
            }
        } else {
            disabled()
        }
    }

private fun part(x: Float, largeur: Int): Float = if (largeur > 0) (x / largeur).coerceIn(0f, 1f) else 0f

/** La rangée des durées : elle accueille une pastille sans grandir. */
private val TIMES_HEIGHT = 32.dp

/** La hauteur touchable : 48 dp, la cible minimale. */
private val TOUCH_HEIGHT = 48.dp

private const val TRACK_ALPHA = 0.14f
private const val STROKE = 3f
private const val COMPACT_STROKE = 2f
private const val WAKE_LENGTH = 44f
private const val WAKE_AMPLITUDE = 3.5f
private const val WAKE_CYCLES = 2f
private const val WAKE_SEGMENTS = 32
private const val WAKE_PERIOD_MS = 1_400f
private const val HEAD = 6f
private const val HEAD_SCRUB = 9f
private const val HALO = 13f
private const val HALO_SCRUB = 20f
private const val HALO_ALPHA = 0.22f
private const val COMPACT_HEAD = 3f
private const val LOOP_BAND = 10f
private const val LOOP_ALPHA = 0.18f
private const val LOOP_STROKE = 2f
