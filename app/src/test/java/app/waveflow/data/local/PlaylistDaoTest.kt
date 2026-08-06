package app.waveflow.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests du DAO sur une base en mémoire.
 *
 * Robolectric fournit SQLite côté JVM : ces tests tournent sans émulateur.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistDaoTest {

    private lateinit var database: WaveFlowDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WaveFlowDatabase::class.java,
        ).build()
        dao = database.playlistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createPlaylist(name: String = "Ma playlist", at: Long = 1_000L): Long =
        dao.insertPlaylist(PlaylistEntity(name = name, createdAt = at, updatedAt = at))

    private suspend fun updatedAtOf(playlistId: Long): Long =
        dao.observePlaylists().first().first { it.id == playlistId }.updatedAt

    @Test
    fun `un morceau ajoute deux fois ne cree qu'une entree`() = runTest {
        val playlistId = createPlaylist()

        dao.addSong(playlistId, songId = 42L, updatedAt = 2_000L)
        dao.addSong(playlistId, songId = 42L, updatedAt = 3_000L)

        val entries = dao.observeEntries().first()
        assertEquals(1, entries.size)
        assertEquals(42L, entries.single().songId)
    }

    @Test
    fun `un ajout en doublon ne modifie pas updatedAt`() = runTest {
        val playlistId = createPlaylist(at = 1_000L)

        dao.addSong(playlistId, songId = 42L, updatedAt = 2_000L)
        assertEquals(2_000L, updatedAtOf(playlistId))

        dao.addSong(playlistId, songId = 42L, updatedAt = 3_000L)
        assertEquals(
            "un ajout sans effet ne doit pas signaler une modification",
            2_000L,
            updatedAtOf(playlistId),
        )
    }

    @Test
    fun `retirer un morceau absent ne modifie pas updatedAt`() = runTest {
        val playlistId = createPlaylist(at = 1_000L)

        dao.removeSong(playlistId, songId = 99L, updatedAt = 5_000L)

        assertEquals(1_000L, updatedAtOf(playlistId))
    }

    @Test
    fun `les positions s'incrementent dans l'ordre d'ajout`() = runTest {
        val playlistId = createPlaylist()

        dao.addSong(playlistId, songId = 10L, updatedAt = 2_000L)
        dao.addSong(playlistId, songId = 20L, updatedAt = 3_000L)
        dao.addSong(playlistId, songId = 30L, updatedAt = 4_000L)

        val positions = dao.observeEntries().first().map { it.songId to it.position }
        assertEquals(listOf(10L to 0, 20L to 1, 30L to 2), positions)
    }

    @Test
    fun `createWithSong cree la playlist et son premier morceau`() = runTest {
        val playlistId = dao.createWithSong(
            playlist = PlaylistEntity(name = "Nouvelle", createdAt = 1_000L, updatedAt = 1_000L),
            songId = 7L,
        )

        assertEquals(1, dao.observePlaylists().first().size)
        val entry = dao.observeEntries().first().single()
        assertEquals(playlistId, entry.playlistId)
        assertEquals(7L, entry.songId)
        assertEquals(0, entry.position)
    }

    @Test
    fun `createWithSong ne laisse aucune playlist si l'ajout du morceau echoue`() = runTest {
        // Rien dans le schéma ne peut faire échouer la seconde écriture : on la
        // fait donc échouer nous-mêmes, pour observer le retour arrière.
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_entrees BEFORE INSERT ON playlist_songs " +
                "BEGIN SELECT RAISE(ABORT, 'insertion refusée'); END",
        )

        val result = runCatching {
            dao.createWithSong(
                playlist = PlaylistEntity(name = "Nouvelle", createdAt = 1_000L, updatedAt = 1_000L),
                songId = 7L,
            )
        }

        assertTrue("l'échec doit remonter à l'appelant", result.isFailure)
        assertEquals(
            "la playlist ne doit pas survivre à l'échec de son premier morceau",
            emptyList<PlaylistEntity>(),
            dao.observePlaylists().first(),
        )
    }

    @Test
    fun `supprimer une playlist supprime ses entrees en cascade`() = runTest {
        val playlistId = createPlaylist()
        dao.addSong(playlistId, songId = 1L, updatedAt = 2_000L)
        dao.addSong(playlistId, songId = 2L, updatedAt = 3_000L)

        dao.deletePlaylist(playlistId)

        assertEquals(emptyList<PlaylistEntity>(), dao.observePlaylists().first())
        assertEquals(emptyList<PlaylistSongEntity>(), dao.observeEntries().first())
    }
}
