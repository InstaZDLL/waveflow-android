package app.waveflow.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.waveflow.WaveFlowApp
import app.waveflow.data.LibraryStore
import app.waveflow.data.PlayHistoryRepository
import app.waveflow.data.TirageStore
import app.waveflow.model.Album
import app.waveflow.model.Song
import app.waveflow.playback.localSongIdOf
import app.waveflow.suggestion.SorteDeSuggestion
import app.waveflow.suggestion.SuggestionEpinglee
import app.waveflow.suggestion.TirageDuJour
import app.waveflow.suggestion.apresUnAppui
import app.waveflow.suggestion.epinglee
import app.waveflow.suggestion.epingler
import app.waveflow.suggestion.libelleDeSorte
import app.waveflow.suggestion.pour
import app.waveflow.suggestion.raisonDe
import app.waveflow.suggestion.suggestionDuJour
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Ce que l'accueil a à montrer.
 *
 * @property resume la dernière piste écoutée, s'il en reste une trace et
 *   qu'elle est toujours sur l'appareil.
 * @property resumeAlbum l'album de [resume], pour « Voir l'album » ; nul s'il
 *   n'est plus dans la bibliothèque.
 * @property songCount le nombre de morceaux sur l'appareil, pour la ligne de
 *   compteurs de l'accueil.
 * @property albumCount le nombre d'albums, même usage.
 * @property suggestion la bande « suggestion du jour », nulle quand il n'y a
 *   rien à suggérer (S13 de `docs/suggestion-du-jour.md`).
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val resume: Song? = null,
    val resumeAlbum: Album? = null,
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val recentlyPlayed: List<Song> = emptyList(),
    val recentlyAdded: List<Album> = emptyList(),
    val suggestion: SuggestionBand? = null,
) {
    /** Rien à proposer : ni écoute passée, ni musique sur l'appareil. */
    val isEmpty: Boolean
        get() = !isLoading && resume == null && recentlyPlayed.isEmpty() && recentlyAdded.isEmpty() &&
            suggestion == null
}

/**
 * La suggestion du jour, prête à dessiner.
 *
 * @property kindLabel la sorte dans l'une de ses tournures (« Vous l'adoriez »).
 * @property reason la ligne qui dit pourquoi, nulle quand la sorte n'a pas de
 *   quoi la dire.
 */
data class SuggestionBand(
    val song: Song,
    val kind: SorteDeSuggestion,
    val kindLabel: String,
    val reason: String?,
)

/**
 * L'accueil, composé de ce que l'appareil sait déjà.
 *
 * Rien n'est demandé au réseau : l'écoute passée vient de la base locale, les
 * ajouts récents du MediaStore. L'écran s'affiche donc hors ligne comme
 * connecté.
 *
 * @param horloge l'heure du tirage et la date du jour (S2) ; le fuseau des
 *   dates que les raisons affichent.
 * @param calcul où se tire la suggestion : elle parcourt la bibliothèque et
 *   l'historique, ce qui n'a rien à faire sur le fil principal.
 */
class HomeViewModel(
    libraryStore: LibraryStore,
    history: PlayHistoryRepository,
    private val tirages: TirageStore,
    private val horloge: Clock = Clock.systemDefaultZone(),
    calcul: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /**
     * La suggestion du jour, et ce qu'il faut épingler pour qu'elle revienne.
     *
     * Le jour vient de [joursDe] : un accueil laissé ouvert passe de lui-même à
     * la suggestion du lendemain à minuit (S2).
     */
    private val suggestion: Flow<SuggestionBand?> = combine(
        libraryStore.library,
        history.observeLocal(HISTORIQUE_SUGGESTION),
        tirages.tirage,
        joursDe(horloge),
    ) { library, ecoutes, enregistre, jour ->
        // Pendant un rechargement, la bibliothèque garde ses morceaux : la
        // bande reste. Au premier chargement, elle est vide et rien n'est tiré.
        val depuis = enregistre.pour(jour)
        val tiree = suggestionDuJour(depuis, library, ecoutes, horloge.millis())
        Tirage(
            band = tiree?.let { s ->
                SuggestionBand(
                    song = s.song,
                    kind = s.sorte,
                    kindLabel = libelleDeSorte(s, jour, depuis.essais),
                    reason = raisonDe(s, jour, depuis.essais, horloge.zone),
                )
            },
            depuis = depuis,
            aEpingler = tiree?.epinglee()?.takeIf { it != depuis.epinglee },
        )
    }
        .flowOn(calcul)
        .onEach { tirage ->
            // Un nouveau tirage est gardé pour sa journée : une fois écoutée, la
            // piste ne serait plus tirée dans sa sorte, et l'accueil changerait
            // sous les yeux de l'utilisateur (S9). L'écriture ne vaut que si
            // rien n'a bougé depuis le tirage (S11).
            val nouvelle = tirage.aEpingler ?: return@onEach
            tirages.modifier { it.epingler(tirage.depuis, nouvelle) }
        }
        .map { it.band }

    val state: StateFlow<HomeUiState> = combine(
        libraryStore.library,
        history.observeRecent(HISTORIQUE),
        suggestion,
    ) { library, ecoutes, bande ->
        // Les pistes du serveur sont bien notées dans l'historique, mais
        // l'accueil ne sait pas encore les rendre : il faudrait les redemander
        // au catalogue, et l'écran ne parle qu'à l'appareil. Elles sont donc
        // ignorées ici plutôt qu'affichées à moitié.
        val ecoutees = ecoutes.mapNotNull { entree ->
            localSongIdOf(entree.mediaId)?.let { library.songsById[it] }
        }

        val reprise = ecoutees.firstOrNull()
        HomeUiState(
            isLoading = library.isLoading,
            resume = reprise,
            resumeAlbum = reprise?.let { library.album(it.albumId) },
            songCount = library.songs.size,
            albumCount = library.albums.size,
            // La reprise occupe déjà la première : la répéter juste en dessous
            // ferait doublon.
            recentlyPlayed = ecoutees.drop(1).take(RECENTES),
            recentlyAdded = library.albums
                .sortedByDescending { it.addedAtMs }
                .take(AJOUTS),
            suggestion = bande,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ARRET_MS), HomeUiState())

    /**
     * « Une autre suggestion » : un appui de plus pour aujourd'hui, écrit d'après
     * ce que le fichier contient et non d'après l'écran (S11).
     */
    fun anotherSuggestion() {
        val jour = LocalDate.now(horloge).toEpochDay()
        viewModelScope.launch { tirages.modifier { it.apresUnAppui(jour) } }
    }

    /**
     * Un tirage et l'état d'après lequel il a été fait.
     *
     * @property aEpingler la suggestion à garder pour la journée, nulle quand
     *   c'est déjà celle qui est gardée.
     */
    private class Tirage(
        val band: SuggestionBand?,
        val depuis: TirageDuJour,
        val aEpingler: SuggestionEpinglee?,
    )

    companion object {
        private const val HISTORIQUE = 21
        private const val RECENTES = 20

        /** S17 : les lignes de l'historique que la suggestion parcourt, au plus. */
        internal const val HISTORIQUE_SUGGESTION = 20_000

        /** Ce que tient la mosaïque de l'accueil : une tuile de tête et cinq autres. */
        private const val AJOUTS = 6
        private const val ARRET_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as WaveFlowApp
                HomeViewModel(
                    libraryStore = app.container.libraryStore,
                    history = app.container.playHistoryRepository,
                    tirages = app.container.tirageStore,
                )
            }
        }
    }
}

/**
 * Le jour du calendrier local (S2), émis à nouveau à chaque minuit.
 *
 * L'attente court jusqu'au prochain minuit **de l'horloge**, puis le jour est
 * relu : une attente qui finirait un peu tôt relit le même jour, que
 * `distinctUntilChanged` tait, et repart pour le reste.
 */
internal fun joursDe(horloge: Clock): Flow<Long> = flow {
    while (true) {
        val maintenant = ZonedDateTime.now(horloge)
        emit(maintenant.toLocalDate().toEpochDay())
        delay(Duration.between(maintenant, prochainMinuit(maintenant)).toMillis())
    }
}.distinctUntilChanged()

/**
 * Le prochain minuit après [maintenant], dans son fuseau. Un jour qui ne commence
 * pas à minuit — un passage à l'heure d'été posé à 0 h — commence à la première
 * heure qui existe.
 */
internal fun prochainMinuit(maintenant: ZonedDateTime): ZonedDateTime =
    maintenant.toLocalDate().plusDays(1).atStartOfDay(maintenant.zone)
