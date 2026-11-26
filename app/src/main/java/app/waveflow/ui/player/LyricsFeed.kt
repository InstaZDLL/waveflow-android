package app.waveflow.ui.player

import app.waveflow.data.lyrics.LyricsRepository
import app.waveflow.data.lyrics.LyricsResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Ce que l'écran sait des paroles.
 *
 * @property shown les paroles remplacent la pochette (P11).
 * @property mediaId le morceau pour lequel [result] a été cherché : l'écran ne
 *   dessine que les paroles du morceau qu'il affiche (P8).
 * @property result `null` tant que la recherche court, ou quand les paroles ne
 *   sont pas affichées — rien n'est alors cherché (P6).
 */
data class LyricsUiState(
    val shown: Boolean = false,
    val mediaId: String? = null,
    val result: LyricsResult? = null,
)

/**
 * Les paroles du morceau courant, cherchées seulement quand on les regarde
 * (`docs/paroles.md`, P6 à P8).
 *
 * Hors du ViewModel pour s'éprouver seule ; le ViewModel la porte et lui
 * prête sa portée.
 *
 * @param mediaIds le morceau courant, tel que le lecteur le nomme. Il émet à
 *   chaque tic de position : seul un changement de morceau relance une
 *   recherche.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LyricsFeed(
    scope: CoroutineScope,
    mediaIds: Flow<String?>,
    private val repository: LyricsRepository,
    started: SharingStarted,
) {

    /** Le choix de l'utilisateur : les paroles à la place de la pochette (P11). */
    private val shown = MutableStateFlow(false)

    /**
     * La zone des paroles est réellement à l'écran. Le choix [shown] tient quand
     * la file d'attente prend sa place ou qu'on replie le lecteur ; la zone,
     * elle, n'y est plus, et rien ne se cherche pour elle (P6).
     */
    private val enVue = MutableStateFlow(false)

    /** Chaque nouvel essai relance la recherche, même pour le même morceau. */
    private val essais = MutableStateFlow(0)

    /**
     * `flatMapLatest` annule la recherche du morceau précédent dès que le
     * suivant arrive : une réponse tardive ne peut pas s'afficher sous un autre
     * morceau (P8). Le dépôt garde ce qu'il a trouvé, si bien que rouvrir les
     * paroles ou revenir à un morceau ne cherche pas deux fois (P7).
     */
    val state: StateFlow<LyricsUiState> = combine(
        shown,
        enVue,
        mediaIds.distinctUntilChanged(),
        essais,
    ) { visibles, vues, mediaId, _ -> Triple(visibles, vues, mediaId) }
        .flatMapLatest { (visibles, vues, mediaId) ->
            if (!visibles || !vues || mediaId == null) {
                flowOf(LyricsUiState(shown = visibles, mediaId = mediaId))
            } else {
                flow {
                    emit(LyricsUiState(shown = true, mediaId = mediaId))
                    emit(LyricsUiState(shown = true, mediaId = mediaId, result = repository.lyricsFor(mediaId)))
                }
            }
        }
        .stateIn(scope, started, LyricsUiState())

    /** Remplace la pochette par les paroles, ou l'inverse. */
    fun toggle() = shown.update { !it }

    /** La zone des paroles entre à l'écran, ou en sort. */
    fun onScreen(visible: Boolean) {
        enVue.value = visible
    }

    /** Cherche de nouveau, après un serveur qui n'a pas répondu (P17). */
    fun retry() = essais.update { it + 1 }
}
