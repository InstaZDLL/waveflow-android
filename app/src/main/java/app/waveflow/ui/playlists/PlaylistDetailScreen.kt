package app.waveflow.ui.playlists

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.waveflow.model.Playlist
import app.waveflow.model.Song
import app.waveflow.ui.browse.DetailHeader
import app.waveflow.ui.components.CenteredMessage
import app.waveflow.ui.components.SongRow
import kotlin.math.roundToInt

/**
 * Détail d'une playlist : en-tête puis morceaux.
 *
 * L'appui long sur une ligne propose de la retirer — c'est l'action attendue
 * ici, là où ailleurs il propose l'ajout à une playlist. Le déplacement passe
 * donc par une poignée dédiée plutôt que par le geste habituel, qui est déjà
 * pris.
 */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    songs: List<Song>,
    nowPlayingId: Long?,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onReorder: (order: List<Song>, onFailure: () -> Unit) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    if (playlist == null) {
        Box(modifier = modifier.fillMaxSize()) {
            CenteredMessage(
                message = "Cette playlist n'existe plus.",
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }

    var songToRemove by remember { mutableStateOf<Song?>(null) }

    val listState = rememberLazyListState()

    // Ordre affiché pendant le geste. Room ne répond qu'au relâchement : la
    // liste doit suivre le doigt sans attendre l'aller-retour. La clé [songs]
    // la ramène à l'ordre stocké dès que la base a parlé — y compris quand
    // l'écriture a échoué.
    var working by remember(songs) { mutableStateOf(songs) }
    var drag by remember { mutableStateOf<DragState?>(null) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = bottomPadding),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = HEADER_KEY) {
            DetailHeader(
                artworkUri = working.firstOrNull()?.artworkUri,
                title = playlist.name,
                subtitle = "Playlist",
                trackCount = working.size,
                durationMs = working.sumOf { it.durationMs },
                onPlay = onPlay,
                onShuffle = onShuffle,
                playEnabled = working.isNotEmpty(),
            )
        }

        if (working.isEmpty()) {
            item(key = "empty") {
                CenteredMessage(
                    message = "Playlist vide. Appuyez longuement sur un morceau, " +
                        "ailleurs dans l'app, pour l'ajouter ici.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        items(working, key = { it.id }) { song ->
            val isDragged = drag?.songId == song.id
            val index = working.indexOfFirst { it.id == song.id }

            // Le glissement est hors de portée de TalkBack et du clavier :
            // les mêmes déplacements sont exposés en actions nommées. Elles
            // passent par le même chemin d'écriture, restauration comprise.
            fun moveTo(target: Int): Boolean {
                if (index < 0 || target !in working.indices) return false
                val stored = songs
                val reordered = working.moved(index, target)
                working = reordered
                onReorder(reordered) { working = stored }
                return true
            }

            val moveActions = buildList {
                if (index > 0) add(CustomAccessibilityAction("Monter") { moveTo(index - 1) })
                if (index in 0 until working.lastIndex) {
                    add(CustomAccessibilityAction("Descendre") { moveTo(index + 1) })
                }
            }

            SongRow(
                song = song,
                isCurrent = song.id == nowPlayingId,
                onClick = { onSongClick(song) },
                onLongClick = { songToRemove = song },
                trailing = {
                    DragHandle(
                        modifier = Modifier.pointerInput(song.id) {
                            detectDragGestures(
                                onDragStart = {
                                    drag = startDrag(song.id, working, listState)
                                },
                                onDrag = { change, amount ->
                                    // La poignée s'approprie le geste : sans
                                    // ça, la LazyColumn le prendrait pour un
                                    // défilement.
                                    change.consume()
                                    drag?.let { current ->
                                        val moved = current.advance(amount.y, working.lastIndex)
                                        if (moved.toIndex != current.toIndex) {
                                            working = working.moved(current.toIndex, moved.toIndex)
                                        }
                                        drag = moved
                                    }
                                },
                                onDragEnd = {
                                    drag?.let { current ->
                                        if (current.toIndex != current.fromIndex) {
                                            // Une écriture qui échoue ne fait
                                            // rien émettre à Room : sans ce
                                            // retour, l'écran garderait un
                                            // ordre que la base ignore.
                                            val stored = songs
                                            onReorder(working) { working = stored }
                                        }
                                    }
                                    drag = null
                                },
                                onDragCancel = {
                                    working = songs
                                    drag = null
                                },
                            )
                        },
                    )
                },
                modifier = Modifier
                    // La ligne saisie passe au-dessus de ses voisines, sans
                    // quoi elle glisserait dessous en les croisant.
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = drag?.translationFor(song.id) ?: 0f }
                    // Portées par la ligne plutôt que par la poignée : c'est
                    // la ligne que TalkBack met au foyer.
                    .semantics { customActions = moveActions },
            )
        }
    }

    songToRemove?.let { song ->
        AlertDialog(
            onDismissRequest = { songToRemove = null },
            title = { Text("Retirer de la playlist") },
            text = { Text("Retirer « ${song.title} » de « ${playlist.name} » ?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveSong(song)
                        songToRemove = null
                    },
                ) {
                    Text("Retirer")
                }
            },
            dismissButton = {
                TextButton(onClick = { songToRemove = null }) { Text("Annuler") }
            },
        )
    }
}

/**
 * Geste de déplacement en cours.
 *
 * `internal` plutôt que `private` : c'est la seule partie du glisser-déposer
 * qui soit du calcul pur, donc la seule vérifiable sans appareil. Le reste —
 * détection du geste, rendu — ne l'est pas.
 *
 * @property fromIndex rang au moment de la saisie, origine du calcul de
 *   destination — le rang courant, lui, bouge sous le doigt.
 * @property offset déplacement vertical cumulé depuis la saisie.
 * @property rowHeight hauteur mesurée de la ligne saisie, en pixels. Mesurée
 *   plutôt que supposée : elle dépend de la densité et de l'échelle de police.
 */
internal data class DragState(
    val songId: Long,
    val fromIndex: Int,
    val toIndex: Int,
    val offset: Float,
    val rowHeight: Float,
) {

    /** Reporte un delta vertical et en déduit le nouveau rang. */
    fun advance(deltaY: Float, lastIndex: Int): DragState {
        val moved = offset + deltaY
        val target = (fromIndex + (moved / rowHeight).roundToInt()).coerceIn(0, lastIndex)
        return copy(offset = moved, toIndex = target)
    }

    /**
     * Décalage visuel restant pour la ligne saisie.
     *
     * Le déplacement déjà absorbé par le changement de rang est retranché :
     * la ligne suit le doigt sans sauter d'une hauteur à chaque permutation.
     */
    fun translationFor(candidateId: Long): Float =
        if (candidateId == songId) offset - (toIndex - fromIndex) * rowHeight else 0f
}

/**
 * Ouvre un geste, ou renvoie `null` si la ligne n'est pas mesurable.
 *
 * Sans hauteur, la destination ne se calcule pas ; mieux vaut ignorer le
 * geste que déplacer au hasard.
 */
private fun startDrag(songId: Long, songs: List<Song>, listState: LazyListState): DragState? {
    val index = songs.indexOfFirst { it.id == songId }
    if (index < 0) return null

    val height = listState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.key == songId }
        ?.size
        ?.takeIf { it > 0 }
        ?.toFloat()
        ?: return null

    return DragState(songId, fromIndex = index, toIndex = index, offset = 0f, rowHeight = height)
}

/** Déplace l'élément de [from] vers [to] sans modifier la liste d'origine. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }

/**
 * Poignée de déplacement.
 *
 * L'icône reste discrète, mais la zone sensible fait 48 dp — c'est elle qui
 * porte le geste, et un doigt ne vise pas 24 dp.
 */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(HANDLE_TOUCH_TARGET),
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "Déplacer le morceau",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .alpha(HANDLE_ALPHA),
        )
    }
}

private const val HEADER_KEY = "header"

/** La poignée doit se voir sans concurrencer le titre du morceau. */
private const val HANDLE_ALPHA = 0.6f

/** Minimum tactile recommandé, indépendant de la taille de l'icône. */
private val HANDLE_TOUCH_TARGET = 48.dp
