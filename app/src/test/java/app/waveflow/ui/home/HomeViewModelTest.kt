package app.waveflow.ui.home

import app.waveflow.data.LibraryStore
import app.waveflow.data.PlayHistoryEntry
import app.waveflow.data.PlayHistoryRepository
import app.waveflow.data.RoomPlayHistoryRepository
import app.waveflow.data.local.PlayHistoryDao
import app.waveflow.data.local.PlayHistoryEntity
import app.waveflow.model.Song
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
    }

    private fun ecoute(mediaId: String, at: Long) = PlayHistoryEntry(mediaId, at, 1)

    @Test
    fun `la derniere ecoute se reprend et ne se repete pas juste en dessous`() =
        runTest(mainDispatcherRule.dispatcher) {
            val magasin = LibraryStore(
                FakeMusicRepository(flowOf(listOf(song(1), song(2), song(3)))),
                backgroundScope,
            )
            magasin.load()

            val vue = HomeViewModel(
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

            val vue = HomeViewModel(
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

            val vue = HomeViewModel(
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

            val vue = HomeViewModel(
                libraryStore = magasin,
                history = object : PlayHistoryRepository {
                    override suspend fun record(mediaId: String) = Unit
                    override fun observeRecent(limit: Int): Flow<List<PlayHistoryEntry>> =
                        RoomPlayHistoryRepository(DaoEnPanne()).observeRecent(limit)
                    override fun observeMostPlayed(limit: Int): Flow<List<PlayHistoryEntry>> =
                        flowOf(emptyList())
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

            val vue = HomeViewModel(libraryStore = magasin, history = FakeHistory(emptyList()))
            advanceUntilIdle()

            val etat = vue.state.first { !it.isLoading }
            assertEquals(listOf(20L, 30L, 10L), etat.recentlyAdded.map { it.id })
        }
}
