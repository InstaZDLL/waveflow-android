package app.waveflow.suggestion

import app.waveflow.data.PlayHistoryEntry
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.playback.localSongIdOf

/**
 * Pourquoi l'accueil propose une piste. L'ordre est celui du bureau et compte :
 * il fixe la rotation (S1) et entre dans la graine du tirage (S3).
 */
enum class SorteDeSuggestion {
    /** Écoutée souvent, puis délaissée (S5). */
    Oubliee,

    /** D'un artiste écouté ces jours-ci, pas entendue depuis un moment (S6). */
    DansLEsprit,

    /** Jamais lancée (S7). */
    JamaisEcoutee,
}

/**
 * Ce que la raison d'une suggestion dit, selon sa sorte ; le reste est nul.
 *
 * Gardée avec l'épinglée (S9) : une suggestion revient avec la raison de son
 * tirage, même quand l'historique a changé depuis — une piste oubliée qu'on
 * vient d'écouter ne se dit pas « pas écoutée depuis aujourd'hui ».
 *
 * @property ecoutes *Oubliée* : le nombre d'écoutes de la piste.
 * @property derniereEcouteMs *Oubliée* : la dernière, en millisecondes.
 * @property artisteAncre *Dans l'esprit* : l'artiste récent dont elle vient.
 * @property ecoutesArtiste *Jamais écoutée* : les écoutes de son artiste.
 * @property ajouteeMs *Jamais écoutée* : son ajout au MediaStore, 0 s'il ne la
 *   date pas.
 */
data class Raison(
    val ecoutes: Int? = null,
    val derniereEcouteMs: Long? = null,
    val artisteAncre: String? = null,
    val ecoutesArtiste: Long? = null,
    val ajouteeMs: Long? = null,
)

/** La suggestion du jour : une piste de l'appareil, et pourquoi elle. */
data class Suggestion(
    val sorte: SorteDeSuggestion,
    val song: Song,
    val raison: Raison,
)

/** Une suggestion telle qu'on la garde pour sa journée : la piste par son identifiant (S9). */
data class SuggestionEpinglee(
    val sorte: SorteDeSuggestion,
    val songId: Long,
    val raison: Raison,
)

fun Suggestion.epinglee(): SuggestionEpinglee = SuggestionEpinglee(sorte, song.id, raison)

/**
 * La suggestion de l'accueil pour le jour et le nombre d'appuis sur « Autre
 * suggestion » de [tirage].
 *
 * Son épinglée, celle que ce jour et ce nombre d'appuis ont déjà donnée, revient
 * telle quelle tant que sa piste est dans la bibliothèque (S9) ; sinon un
 * nouveau tirage est fait. Nulle quand aucune sorte n'a rien à offrir (S13).
 *
 * @param tirage le tirage qui vaut pour aujourd'hui (voir [pour]).
 * @param historique les lignes de l'historique ; seules celles des pistes de
 *   l'appareil comptent (S8), et l'appelant les borne (S17).
 * @param maintenantMs l'instant du tirage, pour les fenêtres de S5 et S6.
 */
fun suggestionDuJour(
    tirage: TirageDuJour,
    bibliotheque: Library,
    historique: List<PlayHistoryEntry>,
    maintenantMs: Long,
): Suggestion? {
    tirage.epinglee?.let { epinglee ->
        bibliotheque.songsById[epinglee.songId]?.let { piste ->
            return Suggestion(epinglee.sorte, piste, epinglee.raison)
        }
    }
    val (jour, essais) = tirage
    val ecoutes = Ecoutes(bibliotheque, historique)
    for (sorte in ordreDesSortes(jour, essais)) {
        val candidats = ecoutes.courteListe(sorte, maintenantMs)
        if (candidats.isNotEmpty()) return candidats[rangTire(candidats.size, jour, essais, sorte)]
    }
    return null
}

/** La courte liste d'une sorte, la meilleure d'abord (S4 à S7) : ce parmi quoi l'on tire. */
internal fun courteListeDe(
    sorte: SorteDeSuggestion,
    bibliotheque: Library,
    historique: List<PlayHistoryEntry>,
    maintenantMs: Long,
): List<Suggestion> = Ecoutes(bibliotheque, historique).courteListe(sorte, maintenantMs)

/**
 * L'artiste d'une piste, ou nul quand le fichier n'en porte pas : le MediaStore
 * donne un identifiant même à `<unknown>`, que l'on ne compte pas comme un
 * artiste (E6).
 */
internal val Song.artisteConnu: Long?
    get() = artistId.takeIf { !artist.isNullOrBlank() && artist != ARTISTE_INCONNU }

/** L'historique rapproché de la bibliothèque, et les courtes listes de chaque sorte. */
private class Ecoutes(
    private val bibliotheque: Library,
    historique: List<PlayHistoryEntry>,
) {
    /** Chaque piste de l'appareil qu'on a écoutée, par identifiant MediaStore. */
    private val parPiste: Map<Long, PlayHistoryEntry> = HashMap<Long, PlayHistoryEntry>(historique.size).apply {
        for (entree in historique) {
            localSongIdOf(entree.mediaId)?.let { id -> put(id, entree) }
        }
    }

    /** Les pistes écoutées encore dans la bibliothèque, avec leur ligne. */
    private val ecouteesPresentes: List<Pair<Song, PlayHistoryEntry>> = parPiste.mapNotNull { (id, entree) ->
        bibliotheque.songsById[id]?.let { piste -> piste to entree }
    }

    /** Les mêmes, quand leur fichier porte un artiste (E6). */
    private val ecouteesAvecArtiste: List<EcouteDArtiste> = ecouteesPresentes.mapNotNull { (piste, entree) ->
        piste.artisteConnu?.let { EcouteDArtiste(it, piste, entree) }
    }

    fun courteListe(sorte: SorteDeSuggestion, maintenantMs: Long): List<Suggestion> = when (sorte) {
        SorteDeSuggestion.Oubliee -> oubliees(maintenantMs)
        SorteDeSuggestion.DansLEsprit -> dansLEsprit(maintenantMs)
        SorteDeSuggestion.JamaisEcoutee -> jamaisEcoutees()
    }

    /** S5 : écoutée au moins trois fois, et pas depuis soixante jours. */
    private fun oubliees(maintenantMs: Long): List<Suggestion> {
        val avant = maintenantMs - JOURS_OUBLI * JOUR_MS
        val classees = ecouteesPresentes
            .filter { (_, entree) -> entree.playCount >= ECOUTES_MIN_OUBLIEE && entree.lastPlayedAtMs < avant }
            .sortedWith(
                compareByDescending<Pair<Song, PlayHistoryEntry>> { it.second.playCount }
                    .thenBy { it.second.lastPlayedAtMs }
                    .thenBy { it.first.id },
            )
            .take(RESERVE)
        return courteListe(classees, { it.first.artisteConnu }, PAR_ARTISTE, COURTE_LISTE).map { (piste, entree) ->
            Suggestion(
                SorteDeSuggestion.Oubliee,
                piste,
                Raison(ecoutes = entree.playCount, derniereEcouteMs = entree.lastPlayedAtMs),
            )
        }
    }

    /** S6 : d'un des artistes écoutés ces deux semaines, pas entendue depuis trente jours. */
    private fun dansLEsprit(maintenantMs: Long): List<Suggestion> {
        val depuis = maintenantMs - JOURS_RECENTS * JOUR_MS
        val pistesRecentes = HashMap<Long, Int>()
        val noms = HashMap<Long, String>()
        for ((artiste, piste, _) in ecouteesAvecArtiste.filter { it.entree.lastPlayedAtMs >= depuis }) {
            pistesRecentes[artiste] = (pistesRecentes[artiste] ?: 0) + 1
            piste.artist?.let { noms.putIfAbsent(artiste, it) }
        }
        if (pistesRecentes.isEmpty()) return emptyList()
        val recents = pistesRecentes.entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .take(ARTISTES_RECENTS)
            .mapTo(HashSet()) { it.key }

        val fraiche = maintenantMs - JOURS_FRAICHEUR * JOUR_MS
        val classees = bibliotheque.songs
            .filter { piste ->
                piste.artisteConnu?.let { it in recents } == true &&
                    (parPiste[piste.id]?.let { it.lastPlayedAtMs < fraiche } ?: true)
            }
            .sortedBy { it.id }
        return courteListe(classees, { it.artisteConnu }, PAR_ARTISTE, COURTE_LISTE).map { piste ->
            Suggestion(
                SorteDeSuggestion.DansLEsprit,
                piste,
                Raison(artisteAncre = piste.artisteConnu?.let(noms::get)),
            )
        }
    }

    /** S7 : jamais lancée, la dernière ajoutée de chaque artiste. */
    private fun jamaisEcoutees(): List<Suggestion> {
        val ecoutesParArtiste = HashMap<Long, Long>()
        for ((artiste, _, entree) in ecouteesAvecArtiste) {
            ecoutesParArtiste[artiste] = (ecoutesParArtiste[artiste] ?: 0L) + entree.playCount
        }

        fun ecoutesDe(piste: Song): Long = piste.artisteConnu?.let { ecoutesParArtiste[it] } ?: 0L
        val classees = uneParArtiste(bibliotheque.songs.filter { it.id !in parPiste })
            .sortedWith(compareByDescending<Song> { ecoutesDe(it) }.then(PlusRecenteDAbord))
            .take(RESERVE)
        return courteListe(classees, { it.artisteConnu }, 1, COURTE_LISTE).map { piste ->
            Suggestion(
                SorteDeSuggestion.JamaisEcoutee,
                piste,
                Raison(ecoutesArtiste = ecoutesDe(piste), ajouteeMs = piste.addedAtMs),
            )
        }
    }
}

/** Une écoute d'une piste dont le fichier porte un artiste. */
private data class EcouteDArtiste(val artiste: Long, val piste: Song, val entree: PlayHistoryEntry)

/**
 * La dernière ajoutée de chaque artiste ; une piste sans artiste fait son propre
 * groupe (S7 et E6), comme la partition `COALESCE(primary_artist, -id)` du bureau.
 */
private fun uneParArtiste(pistes: List<Song>): List<Song> {
    val derniereDeLArtiste = HashMap<Long, Song>()
    val sansArtiste = ArrayList<Song>()
    for (piste in pistes) {
        val artiste = piste.artisteConnu
        val tenue = artiste?.let(derniereDeLArtiste::get)
        when {
            artiste == null -> sansArtiste += piste
            tenue == null || PlusRecenteDAbord.compare(piste, tenue) < 0 -> derniereDeLArtiste[artiste] = piste
        }
    }
    return derniereDeLArtiste.values + sansArtiste
}

/** La plus récemment ajoutée d'abord ; à égalité, le plus petit identifiant. */
private val PlusRecenteDAbord: Comparator<Song> =
    compareByDescending<Song> { it.addedAtMs }.thenBy { it.id }

/** Ce que le MediaStore écrit quand le tag d'artiste manque. */
private const val ARTISTE_INCONNU = "<unknown>"

internal const val JOUR_MS = 86_400_000L

/** S5 : écoutée au moins autant de fois… */
internal const val ECOUTES_MIN_OUBLIEE = 3

/** … et pas du tout depuis autant de jours. */
internal const val JOURS_OUBLI = 60L

/** S6 : jusqu'où remonte « ce qu'on écoute ces jours-ci ». */
internal const val JOURS_RECENTS = 14L

/** S6 : une piste *Dans l'esprit* n'a pas été entendue depuis autant de jours. */
internal const val JOURS_FRAICHEUR = 30L

/** S6 : les artistes qui résument l'écoute récente. */
internal const val ARTISTES_RECENTS = 5

/** S4 : les pistes parmi lesquelles on tire. */
internal const val COURTE_LISTE = 20

/** S4 : les places d'un même artiste dans la courte liste. */
internal const val PAR_ARTISTE = 2

/** S4 : les candidats retenus avant la courte liste (`POOL_LIMIT` du bureau). */
internal const val RESERVE = 400
