package app.waveflow.ui.search

import app.waveflow.data.LibraryStore
import app.waveflow.testing.FakeMusicRepository
import app.waveflow.testing.MainDispatcherRule
import app.waveflow.testing.song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songs = listOf(
        song(id = 1L, title = "Nuit blanche", artist = "Zoé", artistId = 10L, album = "Été", albumId = 100L),
        song(id = 2L, title = "Aube", artist = "Alba", artistId = 20L, album = "Mer", albumId = 200L),
    )

    private fun CoroutineScope.loadedStore(): LibraryStore =
        LibraryStore(FakeMusicRepository(flowOf(songs)), this).also { it.load() }

    @Test
    fun `sans requete les resultats sont vides`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SearchViewModel(backgroundScope.loadedStore())
        advanceUntilIdle()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.results.collect {} }

        assertTrue(
            "une requête vide ne doit pas recopier l'onglet Titres",
            viewModel.results.value.isEmpty,
        )

        job.cancel()
    }

    @Test
    fun `la requete filtre la bibliotheque partagee`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SearchViewModel(backgroundScope.loadedStore())
        advanceUntilIdle()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.results.collect {} }
        viewModel.onQueryChange("aube")

        assertEquals(listOf(2L), viewModel.results.value.songs.map { it.id })

        job.cancel()
    }

    @Test
    fun `vider la requete vide les resultats`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = SearchViewModel(backgroundScope.loadedStore())
        advanceUntilIdle()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.results.collect {} }
        viewModel.onQueryChange("aube")
        viewModel.clear()

        assertEquals("", viewModel.query.value)
        assertTrue(viewModel.results.value.isEmpty)

        job.cancel()
    }

    @Test
    fun `les resultats suivent le chargement de la bibliotheque`() = runTest(mainDispatcherRule.dispatcher) {
        // La requête est posée avant que le store ait quoi que ce soit à
        // offrir : le résultat doit arriver avec la bibliothèque, sans que
        // l'écran ait à redemander.
        val store = LibraryStore(FakeMusicRepository(flowOf(songs)), backgroundScope)
        val viewModel = SearchViewModel(store)

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.results.collect {} }
        viewModel.onQueryChange("nuit")
        assertTrue(viewModel.results.value.isEmpty)

        store.load()
        advanceUntilIdle()

        assertEquals(listOf(1L), viewModel.results.value.songs.map { it.id })

        job.cancel()
    }
}
