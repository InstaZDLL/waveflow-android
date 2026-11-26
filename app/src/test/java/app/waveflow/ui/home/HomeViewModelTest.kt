package app.waveflow.ui.home

import app.waveflow.data.LibraryStore
import app.waveflow.data.PlayHistoryEntry
import app.waveflow.data.PlayHistoryRepository
import app.waveflow.data.RoomPlayHistoryRepository
import app.waveflow.data.TirageStore
import app.waveflow.data.local.PlayHistoryDao
import app.waveflow.data.local.PlayHistoryEntity
import app.waveflow.model.Song
import app.waveflow.suggestion.Raison
import app.waveflow.suggestion.SorteDeSuggestion
import app.waveflow.suggestion.SuggestionEpinglee
import app.waveflow.suggestion.TirageDuJour
import app.waveflow.testing.FakeMusicRepository
import app.waveflow.testing.MainDispatcherRule
import app.waveflow.testing.song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Ce que l'accueil compose à partir de ce que l'appareil sait.
 *
 * L'historique retient des `mediaId`, la bibliothèque contient des morceaux :
 * tout l'écran tient dans la façon dont ces deux listes se rejoignent, et dans
 * ce qui se passe quand elles ne se rejoignent pas.
 *
 * Robolectric parce que les morceaux de test portent une `Uri`, que la JVM
 * seule ne sait pas construire.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeHistory(entrees: List<PlayHistoryEntry>) : PlayHistoryRepository {
        private val flux = MutableStateFlow(entrees)
        override suspend fun record(mediaId: String) = Unit
        override fun observeRecent(limit: Int): Flow<List<PlayHistoryEntry>> =
            flux.map { it.take(limit) }
        override fun observeMostPlayed(limit: Int): Flow<List<PlayHistoryEntry>> = flux

        // Comme la requête : les pistes de l'appareil, la plus récente d'abord, bornées.
        override fun observeLocal(limit: Int): Flow<List<PlayHistoryEntry>> = flux.map { lignes ->
            lignes.filter { it.mediaId.startsWith("local:") }.sortedByDescending { it.lastPlayedAtMs }.take(limit)
        }

        /** Une écoute de plus, notée comme le ferait le service. */
        fun ecouter(mediaId: String, at: Long) = flux.update { lignes ->
            val avant = lignes.firstOrNull { it.mediaId == mediaId }
            lignes.filterNot { it.mediaId == mediaId } + PlayHistoryEntry(mediaId, at, (avant?.playCount ?: 0) + 1)
        }
    }

    /**
     * Le tirage en mémoire, modifié d'un seul tenant comme `DataStore.edit` : la
     * transformation lit ce qui est enregistré au moment d'écrire.
     */
    private class FakeTirageStore(
        initial: TirageDuJour? = null,
        /** Comme un disque plein : l'écriture est perdue, sans rien lever (S12). */
        private val refuse: Boolean = false,
    ) : TirageStore {
        val flux = MutableStateFlow(initial)
        override val tirage: Flow<TirageDuJour?> = flux
        override suspend fun modifier(transform: (TirageDuJour?) -> TirageDuJour?) {
            if (!refuse) flux.update(transform)
        }
    }

    /** Une horloge qui avance avec le temps virtuel du test, en UTC. */
    private fun TestScope.horlogeVirtuelle(debut: Instant = LE_26_NOVEMBRE_10H) = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = debut.plusMillis(testScheduler.currentTime)
    }

    private fun TestScope.accueil(
        libraryStore: LibraryStore,
        history: PlayHistoryRepository,
        tirages: TirageStore = FakeTirageStore(),
        horloge: Clock = horlogeVirtuelle(),
    ) = HomeViewModel(libraryStore, history, tirages, horloge, mainDispatcherRule.dispatcher)

    private fun ecoute(mediaId: String, at: Long) = PlayHistoryEntry(mediaId, at, 1)

    private suspend fun TestScope.bibliotheque(vararg pistes: Song): LibraryStore =
        LibraryStore(FakeMusicRepository(flowOf(pistes.toList())), backgroundScope).also { it.load() }

    @Test
    fun `la bande suggere une piste de l'appareil et la garde pour la journee`() =
        runTest(mainDispatcherRule.dispatcher) {
            val tirages = FakeTirageStore()
            val vue = accueil(bibliotheque(song(1), song(2), song(3)), FakeHistory(emptyList()), tirages)

            val bande = vue.state.first { it.suggestion != null }.suggestion!!

            // Rien d'écouté : c'est une jamais écoutée, quelle que soit la sorte du jour.
            assertEquals(SorteDeSuggestion.JamaisEcoutee, bande.kind)
            val garde = tirages.flux.value
            assertEquals(JOUR_DU_26, garde?.jour)
            assertEquals(0, garde?.essais)
            assertEquals(bande.song.id, garde?.epinglee?.songId)
        }

    @Test
    fun `la suggestion du jour revient une fois ecoutee`() =
        runTest(mainDispatcherRule.dispatcher) {
            // S9 : écoutée, elle n'est plus « jamais écoutée » ; un nouveau tirage
            // la remplacerait sous les yeux de l'utilisateur.
            val historique = FakeHistory(emptyList())
            val tirages = FakeTirageStore()
            val vue = accueil(bibliotheque(song(1), song(2), song(3)), historique, tirages)
            backgroundScope.launch { vue.state.collect {} }
            runCurrent()
            val avant = vue.state.value.suggestion!!

            historique.ecouter("local:${avant.song.id}", LE_26_NOVEMBRE_10H.toEpochMilli())
            runCurrent()

            assertEquals(avant, vue.state.value.suggestion)
            assertEquals(avant.song.id, tirages.flux.value?.epinglee?.songId)
        }

    @Test
    fun `un fichier qui refuse d'ecrire ne fait pas changer la bande une fois la piste ecoutee`() =
        runTest(mainDispatcherRule.dispatcher) {
            // S12 : rien n'est épinglé sur disque, l'accueil s'en souvient lui-même.
            val historique = FakeHistory(emptyList())
            val vue = accueil(
                bibliotheque(song(1), song(2), song(3)),
                historique,
                FakeTirageStore(refuse = true),
            )
            backgroundScope.launch { vue.state.collect {} }
            runCurrent()
            val avant = vue.state.value.suggestion!!

            historique.ecouter("local:${avant.song.id}", LE_26_NOVEMBRE_10H.toEpochMilli())
            runCurrent()

            assertEquals(avant, vue.state.value.suggestion)
        }

    @Test
    fun `une epinglee disparue de l'appareil ne fait pas oublier celle tiree a sa place`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Le fichier garde la piste 99, effacée depuis, et refuse d'écrire la
            // remplaçante : c'est la mémoire qui la tient, même une fois écoutée.
            val historique = FakeHistory(emptyList())
            val effacee = SuggestionEpinglee(SorteDeSuggestion.JamaisEcoutee, 99, Raison(ajouteeMs = 1))
            val vue = accueil(
                bibliotheque(song(1), song(2), song(3)),
                historique,
                FakeTirageStore(TirageDuJour(JOUR_DU_26, 0, effacee), refuse = true),
            )
            backgroundScope.launch { vue.state.collect {} }
            runCurrent()
            val avant = vue.state.value.suggestion!!

            historique.ecouter("local:${avant.song.id}", LE_26_NOVEMBRE_10H.toEpochMilli())
            runCurrent()

            assertEquals(avant, vue.state.value.suggestion)
        }

    @Test
    fun `autre suggestion vaut pour le jour que la bande montre, meme apres un changement de fuseau`() =
        runTest(mainDispatcherRule.dispatcher) {
            // 23 h 30 en UTC ; le voyageur passe à UTC+3, où il est déjà 2 h 30
            // le lendemain. La bande reste sur le 26 jusqu'au minuit qu'elle
            // attend : l'appui doit compter pour le 26, pas pour un 27 qu'elle
            // ne montre pas.
            var fuseau: ZoneId = ZoneOffset.UTC
            val debut = Instant.parse("2026-11-26T23:30:00Z")
            val horloge = object : Clock() {
                override fun getZone(): ZoneId = fuseau
                override fun withZone(zone: ZoneId?): Clock = this
                override fun instant(): Instant = debut.plusMillis(testScheduler.currentTime)
            }
            val tirages = FakeTirageStore()
            val pistes = (1L..30L).map { song(it, addedAtMs = it) }.toTypedArray()
            val vue = accueil(bibliotheque(*pistes), FakeHistory(emptyList()), tirages, horloge)
            backgroundScope.launch { vue.state.collect {} }
            runCurrent()

            fuseau = ZoneOffset.ofHours(3)
            vue.anotherSuggestion()
            runCurrent()

            val garde = tirages.flux.value!!
            assertEquals(JOUR_DU_26, garde.jour)
            assertEquals(1, garde.essais)
        }

    @Test
    fun `un fuseau change pendant l'attente de minuit est rattrape dans l'heure`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Midi en UTC : minuit est à douze heures. Passé à UTC+14, il est
            // déjà 2 h le lendemain ; la bande ne doit pas attendre midi.
            var fuseau: ZoneId = ZoneOffset.UTC
            val debut = Instant.parse("2026-11-26T12:00:00Z")
            val horloge = object : Clock() {
                override fun getZone(): ZoneId = fuseau
                override fun withZone(zone: ZoneId?): Clock = this
                override fun instant(): Instant = debut.plusMillis(testScheduler.currentTime)
            }
            val tirages = FakeTirageStore()
            val vue = accueil(bibliotheque(song(1), song(2)), FakeHistory(emptyList()), tirages, horloge)
            backgroundScope.launch { vue.state.collect {} }
            runCurrent()
            assertEquals(JOUR_DU_26, tirages.flux.value?.jour)

            fuseau = ZoneOffset.ofHours(14)
            advanceTimeBy(HEURE_MS + 1)
            runCurrent()

            assertEquals(JOUR_DU_26 + 1, tirages.flux.value?.jour)
        }

    @Test
    fun `l'epinglee relue apres une lecture en echec passe avant celle tiree entre-temps`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Au démarrage, la lecture du fichier échoue et rend « aucun tirage » :
            // l'accueil en tire un. Puis la lecture reprend et rend l'épinglée du
            // matin, qui doit rester.
            val tirages = FakeTirageStore()
            val pistes = (1L..30L).map { song(it, addedAtMs = it) }.toTypedArray()
            val vue = accueil(bibliotheque(*pistes), FakeHistory(emptyList()), tirages)
            backgroundScope.launch { vue.state.collect {} }
            runCurrent()
            val tiree = vue.state.value.suggestion!!.song.id
            val duMatin = if (tiree == 1L) 2L else 1L

            tirages.flux.value = TirageDuJour(
                JOUR_DU_26,
                0,
                SuggestionEpinglee(SorteDeSuggestion.JamaisEcoutee, duMatin, Raison(ecoutesArtiste = 0, ajouteeMs = 1)),
            )
            runCurrent()

            assertEquals(duMatin, vue.state.value.suggestion?.song?.id)
            assertEquals(duMatin, tirages.flux.value?.epinglee?.songId)
        }

    @Test
    fun `l'horloge par defaut suit le fuseau du systeme`() {
        val avant = TimeZone.getDefault()
        try {
            val horloge = horlogeDuSysteme()
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            assertEquals(ZoneId.of("Pacific/Kiritimati"), horloge.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Adak"))
            assertEquals(ZoneId.of("America/Adak"), horloge.zone)
        } finally {
            TimeZone.setDefault(avant)
        }
    }

    @Test
    fun `autre suggestion tire de nouveau et retient le nouveau tirage`() =
        runTest(mainDispatcherRule.dispatcher) {
            val tirages = FakeTirageStore()
            val pistes = (1L..30L).map { song(it, addedAtMs = it) }.toTypedArray()
            val vue = accueil(bibliotheque(*pistes), FakeHistory(emptyList()), tirages)
            backgroundScope.launch { vue.state.collect {} }
            runCurrent()

            vue.anotherSuggestion()
            runCurrent()

            val garde = tirages.flux.value!!
            assertEquals(1, garde.essais)
            assertEquals(vue.state.value.suggestion?.song?.id, garde.epinglee?.songId)
        }

    @Test
    fun `a minuit l'accueil reste ouvert passe a la suggestion du lendemain`() =
        runTest(mainDispatcherRule.dispatcher) {
            // S2 : la bascule se lit sur l'horloge, sans retour au premier plan.
            val tirages = FakeTirageStore()
            val vue = accueil(
                bibliotheque(song(1), song(2), song(3)),
                FakeHistory(emptyList()),
                tirages,
                horlogeVirtuelle(Instant.parse("2026-11-26T23:59:00Z")),
            )
            backgroundScope.launch { vue.state.collect {} }
            runCurrent()
            assertEquals(JOUR_DU_26, tirages.flux.value?.jour)

            advanceTimeBy(MINUTE_MS + 1)
            runCurrent()

            assertEquals(JOUR_DU_26 + 1, tirages.flux.value?.jour)
        }

    @Test
    fun `la derniere ecoute se reprend et ne se repete pas juste en dessous`() =
        runTest(mainDispatcherRule.dispatcher) {
            val magasin = LibraryStore(
                FakeMusicRepository(flowOf(listOf(song(1), song(2), song(3)))),
                backgroundScope,
            )
            magasin.load()

            val vue = accueil(
                libraryStore = magasin,
                history = FakeHistory(
                    listOf(ecoute("local:3", 300), ecoute("local:2", 200), ecoute("local:1", 100)),
                ),
            )
            advanceUntilIdle()

            val etat = vue.state.first { !it.isLoading }
            assertEquals(3L, etat.resume?.id)
            // La reprise occupe déjà la première place : la répéter ferait
            // doublon à deux lignes d'intervalle.
            assertEquals(listOf(2L, 1L), etat.recentlyPlayed.map { it.id })
        }

    @Test
    fun `une piste ecoutee puis disparue de l'appareil ne remonte pas`() =
        runTest(mainDispatcherRule.dispatcher) {
            // L'historique garde la trace d'un morceau que le MediaStore ne
            // connaît plus : l'accueil ne peut ni l'afficher ni le jouer.
            val magasin = LibraryStore(
                FakeMusicRepository(flowOf(listOf(song(1)))),
                backgroundScope,
            )
            magasin.load()

            val vue = accueil(
                libraryStore = magasin,
                history = FakeHistory(listOf(ecoute("local:99", 900), ecoute("local:1", 100))),
            )
            advanceUntilIdle()

            val etat = vue.state.first { !it.isLoading }
            assertEquals(1L, etat.resume?.id)
            assertEquals(emptyList<Long>(), etat.recentlyPlayed.map { it.id })
        }

    @Test
    fun `une piste du serveur est ignoree faute de savoir la rendre`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Elle est bien notée dans l'historique — ce que le service fait
            // pour les deux sources — mais l'accueil ne parle qu'à l'appareil.
            val magasin = LibraryStore(FakeMusicRepository(flowOf(emptyList())), backgroundScope)
            magasin.load()

            val vue = accueil(
                libraryStore = magasin,
                history = FakeHistory(listOf(ecoute("remote:uuid-a", 900))),
            )
            advanceUntilIdle()

            assertNull(vue.state.first { !it.isLoading }.resume)
        }

    @Test
    fun `un historique illisible coute l'historique, pas la page`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Un flux Room propage l'erreur de sa requête. Sans filet, elle
            // emporterait la collecte entière : l'accueil perdrait aussi les
            // ajouts récents, qui ne doivent pourtant rien à l'historique.
            val magasin = LibraryStore(
                FakeMusicRepository(flowOf(listOf(song(1, albumId = 10, addedAtMs = 100)))),
                backgroundScope,
            )
            magasin.load()

            val vue = accueil(
                libraryStore = magasin,
                history = object : PlayHistoryRepository {
                    override suspend fun record(mediaId: String) = Unit
                    override fun observeRecent(limit: Int): Flow<List<PlayHistoryEntry>> =
                        RoomPlayHistoryRepository(DaoEnPanne()).observeRecent(limit)
                    override fun observeMostPlayed(limit: Int): Flow<List<PlayHistoryEntry>> =
                        flowOf(emptyList())
                    override fun observeLocal(limit: Int): Flow<List<PlayHistoryEntry>> =
                        RoomPlayHistoryRepository(DaoEnPanne()).observeLocal(limit)
                },
            )
            advanceUntilIdle()

            val etat = vue.state.first { !it.isLoading }
            assertNull(etat.resume)
            assertEquals(listOf(10L), etat.recentlyAdded.map { it.id })
        }

    /** Un DAO dont la lecture échoue, comme une base devenue illisible. */
    private class DaoEnPanne : PlayHistoryDao {
        override suspend fun record(mediaId: String, playedAtMs: Long) = Unit
        override suspend fun insertIfAbsent(mediaId: String, playedAtMs: Long) = Unit
        override suspend fun countPlay(mediaId: String, playedAtMs: Long) = Unit
        override fun observeRecent(limit: Int): Flow<List<PlayHistoryEntity>> =
            flow { throw IllegalStateException("base illisible") }
        override fun observeMostPlayed(limit: Int): Flow<List<PlayHistoryEntity>> =
            flowOf(emptyList())
        override fun observeLocal(limit: Int): Flow<List<PlayHistoryEntity>> =
            flow { throw IllegalStateException("base illisible") }
    }

    @Test
    fun `les ajouts recents viennent du plus frais au plus ancien`() =
        runTest(mainDispatcherRule.dispatcher) {
            val magasin = LibraryStore(
                FakeMusicRepository(
                    flowOf(
                        listOf(
                            song(1, albumId = 10, addedAtMs = 100),
                            song(2, albumId = 20, addedAtMs = 300),
                            song(3, albumId = 30, addedAtMs = 200),
                        ),
                    ),
                ),
                backgroundScope,
            )
            magasin.load()

            val vue = accueil(libraryStore = magasin, history = FakeHistory(emptyList()))
            advanceUntilIdle()

            val etat = vue.state.first { !it.isLoading }
            assertEquals(listOf(20L, 30L, 10L), etat.recentlyAdded.map { it.id })
        }

    @Test
    fun `la reprise mene a son propre album et l'accueil compte la bibliotheque`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Deux albums : « Voir l'album » doit ouvrir celui de la reprise,
            // pas le premier venu.
            val magasin = LibraryStore(
                FakeMusicRepository(
                    flowOf(listOf(song(1, albumId = 10), song(2, albumId = 10), song(3, albumId = 20))),
                ),
                backgroundScope,
            )
            magasin.load()

            val vue = accueil(libraryStore = magasin, history = FakeHistory(listOf(ecoute("local:3", 300))))
            advanceUntilIdle()

            val etat = vue.state.first { !it.isLoading }
            assertEquals(20L, etat.resumeAlbum?.id)
            assertEquals(3, etat.songCount)
            assertEquals(2, etat.albumCount)
        }

    @Test
    fun `la mosaique des ajouts ne retient que six albums`() =
        runTest(mainDispatcherRule.dispatcher) {
            val magasin = LibraryStore(
                FakeMusicRepository(
                    flowOf((1L..8L).map { song(it, albumId = it * 10, addedAtMs = it) }),
                ),
                backgroundScope,
            )
            magasin.load()

            val vue = accueil(libraryStore = magasin, history = FakeHistory(emptyList()))
            advanceUntilIdle()

            assertEquals(
                listOf(80L, 70L, 60L, 50L, 40L, 30L),
                vue.state.first {
                    !it.isLoading
                }.recentlyAdded.map { it.id },
            )
        }

    private companion object {
        val LE_26_NOVEMBRE_10H: Instant = Instant.parse("2026-11-26T10:00:00Z")
        val JOUR_DU_26: Long = LocalDate.of(2026, 11, 26).toEpochDay()
        const val MINUTE_MS = 60_000L
        const val HEURE_MS = 3_600_000L
    }
}
