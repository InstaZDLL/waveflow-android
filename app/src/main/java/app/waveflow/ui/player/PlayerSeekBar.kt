package app.waveflow.ui.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
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
import app.waveflow.playback.AbLoopState
import app.waveflow.ui.formatDuration

/**
 * La barre de position du lecteur plein écran : la progression ([ProgressLine])
 * qu'on touche ou qu'on fait glisser, les deux durées dessous, et entre elles
 * le bouton de la boucle A-B.
 *
 * Elle remplace le `Slider` de Material, déprécié par Compose 1.13 et qui ne
 * savait de toute façon ni onduler ni marquer les bornes d'une boucle. Ce
 * qu'il donnait sans qu'on le demande se refait ici : un appui va à l'endroit
 * touché, un glissement fait suivre la tête au doigt et ne déplace la lecture
 * qu'au lâcher, le clavier la règle par ses flèches, et un lecteur d'écran
 * lit la position et la règle.
 *
 * @param trackKey l'identité du morceau : un glissement en cours est oublié
 *   quand il change.
 */
@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    trackKey: String,
    playing: Boolean,
    abLoop: AbLoopState,
    onSeek: (Long) -> Unit,
    onMarkAbLoop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDuration = durationMs > 0L

    // Pendant un glissement, la position affichée suit le doigt et non le
    // lecteur ; remise à zéro dès qu'on change de morceau, ou que la durée se
    // perd ou revient : le geste interrompu ne finit jamais, et la tête
    // resterait là où le doigt l'avait laissée.
    var scrub by remember(trackKey, hasDuration) { mutableStateOf<Float?>(null) }

    // Une boucle d'un autre morceau n'allume pas le bouton : l'écran peut
    // recevoir la piste suivante un tic avant que le service ne l'efface.
    val boucle = loopOfTrack(abLoop, trackKey)
    val glisse by rememberGlidingProgress(positionMs, durationMs, trackKey, playing)
    val displayedPositionMs = scrub?.let { (it * durationMs).toLong() } ?: positionMs

    // Les gestes vivent plus longtemps qu'une composition : ils relisent la
    // durée, la position et le rappel du moment plutôt que ceux de leur
    // création.
    val duree by rememberUpdatedState(durationMs)
    val position by rememberUpdatedState(positionMs)
    val chercher by rememberUpdatedState(onSeek)

    // Au clavier, la ligne prend le focus et s'avive comme sous le doigt :
    // c'est l'anneau de focus de la ligne.
    val interactions = remember { MutableInteractionSource() }
    val focalisee by interactions.collectIsFocusedAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        ProgressLine(
            progress = { scrub ?: glisse },
            playing = playing,
            loop = loopMarks(abLoop, trackKey, durationMs),
            scrubbing = scrub != null || focalisee,
            modifier = Modifier
                .fillMaxWidth()
                .height(TOUCH_HEIGHT.dp)
                // Relancé à chaque morceau, comme le glissement : un appui
                // commencé sur le précédent ne finit pas dans le suivant.
                .pointerInput(hasDuration, trackKey) {
                    if (!hasDuration) return@pointerInput
                    detectTapGestures { point ->
                        chercher((part(point.x, size.width) * duree).toLong())
                    }
                }
                // Relancé à chaque morceau : sans la clé, le geste en cours
                // garde l'état du morceau précédent, et le lâcher sauterait
                // dans le suivant à une position choisie pour l'autre.
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
                // Les touches d'un curseur, comme le `Slider` les donnait et
                // comme le bureau les donne : flèches de 5 s, pages de 30 s,
                // début et fin.
                .onKeyEvent { evenement ->
                    if (!hasDuration || evenement.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val cible = keyboardSeekTarget(evenement.key, position, duree)
                    cible?.let(chercher)
                    cible != null
                }
                .focusable(enabled = hasDuration, interactionSource = interactions)
                .semantics {
                    contentDescription = "Position dans le morceau"
                    stateDescription = if (hasDuration) {
                        "${formatDuration(displayedPositionMs)} sur ${formatDuration(durationMs)}"
                    } else {
                        "Durée inconnue"
                    }
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = if (hasDuration) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                        range = 0f..1f,
                    )
                    if (hasDuration) {
                        setProgress { valeur ->
                            chercher((valeur.coerceIn(0f, 1f) * duree).toLong())
                            true
                        }
                    } else {
                        disabled()
                    }
                },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(displayedPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Entre les deux durées, et non dans l'en-tête : A et B sont des
            // positions, et se posent en regardant celle qui défile. L'en-tête
            // est par ailleurs plein.
            AbLoopButton(abLoop = boucle, onClick = onMarkAbLoop)

            Text(
                text = if (hasDuration) formatDuration(durationMs) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Où mène [key] depuis [positionMs] dans un morceau de [durationMs], ou
 * `null` si la touche n'est pas l'une de celles d'un curseur.
 */
internal fun keyboardSeekTarget(key: Key, positionMs: Long, durationMs: Long): Long? {
    val cible = when (key) {
        Key.DirectionLeft, Key.DirectionDown -> positionMs - KEY_STEP_MS
        Key.DirectionRight, Key.DirectionUp -> positionMs + KEY_STEP_MS
        Key.PageDown -> positionMs - KEY_PAGE_MS
        Key.PageUp -> positionMs + KEY_PAGE_MS
        Key.MoveHome -> 0L
        Key.MoveEnd -> durationMs
        else -> return null
    }
    return cible.coerceIn(0L, durationMs)
}

/** La part de la largeur sous le doigt, bornée à la piste. */
private fun part(x: Float, largeur: Int): Float = if (largeur > 0) (x / largeur).coerceIn(0f, 1f) else 0f

/**
 * La boucle A-B : un seul bouton pour les trois temps.
 *
 * D'abord A, puis B, puis efface — c'est ce qu'on attend de quelque chose qu'on
 * presse en écoutant, sans quitter la musique des yeux. Le libellé dit où en est
 * la pose plutôt que ce que fera le prochain appui : « A-… » se lit comme une
 * phrase laissée en suspens, ce qu'est précisément une boucle dont B manque.
 *
 * Une boucle active et muette serait le même défaut que la vitesse invisible —
 * on chercherait longtemps pourquoi le morceau se répète. D'où la teinte, les
 * bornes marquées sur la progression, et une description qui donne les deux
 * bornes.
 */
@Composable
private fun AbLoopButton(abLoop: AbLoopState, onClick: () -> Unit) {
    val libelle = when (abLoop) {
        is AbLoopState.Started -> "A-…"
        else -> "A-B"
    }

    val description = when (abLoop) {
        AbLoopState.Off -> "Boucle A-B : poser le début"
        is AbLoopState.Started -> "Boucle A-B : poser la fin"
        is AbLoopState.Armed ->
            "Boucle active de ${formatDuration(abLoop.startMs)} " +
                "à ${formatDuration(abLoop.endMs)} — appuyer pour l'effacer"
    }

    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = libelle,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = if (abLoop == AbLoopState.Off) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

/** La hauteur touchable de la ligne : bien plus que son trait, pour le pouce. */
private const val TOUCH_HEIGHT = 40

private const val KEY_STEP_MS = 5_000L
private const val KEY_PAGE_MS = 30_000L
