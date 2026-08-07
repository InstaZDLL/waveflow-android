package app.waveflow.data

import app.waveflow.testing.FakeMusicRepository
import app.waveflow.testing.song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryStoreTest {

    /**
     * Le store reçoit d'ordinaire une portée de vie du processus. En test on
     * lui donne la portée d'arrière-plan — annulée avec le test — en dispatch
     * immédiat, pour que `load()` prenne effet sans attendre.
     */
    private fun TestScope.testStoreScope(): CoroutineScope =
        backgroundScope + UnconfinedTestDispatcher(testScheduler)

    @Test
    fun `le chargement expose les morceaux et leurs regroupements`() = runTest {
        val songs = listOf(
            song(id = 1L, albumId = 100L, artistId = 10L),
            song(id = 2L, albumId = 100L, artistId = 10L),
            song(id = 3L, albumId = 200L, artistId = 20L),
        )
        val store = LibraryStore(FakeMusicRepository(flowOf(songs)), testStoreScope())

        store.load()
        advanceUntilIdle()

        val library = store.library.value
        assertEquals(false, library.isLoading)
        assertEquals(3, library.songs.size)
        assertEquals(2, library.albums.size)
        assertEquals(2, library.artists.size)
        assertEquals(songs[1], library.songsById[2L])
    }

    @Test
    fun `une erreur de lecture arrete le chargement et remonte le message`() = runTest {
        val store = LibraryStore(
            FakeMusicRepository(flow { throw SecurityException("permission révoquée") }),
            testStoreScope(),
        )

        store.load()
        advanceUntilIdle()

        val library = store.library.value
        assertEquals(false, library.isLoading)
        assertEquals("permission révoquée", library.errorMessage)
    }

    @Test
    fun `charger deux fois n'interroge la source qu'une fois`() = runTest {
        var subscriptions = 0
        val store = LibraryStore(
            FakeMusicRepository(
                flow {
                    subscriptions++
                    emit(listOf(song(id = 1L)))
                    // Comme le vrai dépôt, adossé à un ContentObserver : le
                    // flux reste ouvert au lieu de se terminer.
                    awaitCancellation()
                },
            ),
            testStoreScope(),
        )

        store.load()
        store.load()
        advanceUntilIdle()

        assertEquals(
            "c'est tout l'intérêt du store partagé : une seule requête MediaStore",
            1,
            subscriptions,
        )
    }

    @Test
    fun `load repart apres une observation terminee en erreur`() = runTest {
        var attempts = 0
        val store = LibraryStore(
            FakeMusicRepository(
                flow {
                    attempts++
                    if (attempts == 1) throw SecurityException("permission révoquée")
                    emit(listOf(song(id = 1L)))
                },
            ),
            testStoreScope(),
        )

        store.load()
        advanceUntilIdle()

        // La permission est ré-accordée depuis les paramètres : la porte
        // rappelle load(), qui ne doit pas se croire encore en cours.
        store.load()
        advanceUntilIdle()

        assertEquals(2, attempts)
        assertEquals(null, store.library.value.errorMessage)
        assertEquals(1, store.library.value.songs.size)
    }

    @Test
    fun `retry relance la lecture apres une erreur`() = runTest {
        var attempts = 0
        val store = LibraryStore(
            FakeMusicRepository(
                flow {
                    attempts++
                    if (attempts == 1) throw SecurityException("permission révoquée")
                    emit(listOf(song(id = 1L)))
                },
            ),
            testStoreScope(),
        )

        store.load()
        advanceUntilIdle()
        assertTrue(store.library.value.errorMessage != null)

        store.retry()
        advanceUntilIdle()

        val library = store.library.value
        assertEquals(null, library.errorMessage)
        assertEquals(1, library.songs.size)
    }
}
