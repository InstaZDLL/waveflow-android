package app.waveflow.playback

import androidx.core.net.toUri
import app.waveflow.testing.remoteSong
import app.waveflow.testing.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La traduction vers Media3, et surtout la distinction des sources.
 *
 * Robolectric pour disposer d'un vrai `android.net.Uri`.
 */
@RunWith(RobolectricTestRunner::class)
class MediaItemMapperTest {

    @Test
    fun `une piste locale garde son identifiant MediaStore`() {
        val item = song(id = 42L, title = "Ciel Bas").toMediaItem()

        assertEquals(42L, item.localSongId)
        assertEquals(TrackSource.Local, item.toPlayingTrack().source)
        assertEquals("Ciel Bas", item.toPlayingTrack().title)
    }

    @Test
    fun `une piste distante n'est pas prise pour une piste locale`() {
        // Sans préfixe de source, un UUID rendrait `null` à la conversion en
        // Long — indiscernable d'une piste locale non résolue.
        val item = remoteSong(id = "c07f8d98").toMediaItem()

        assertNull(item.localSongId)
        assertEquals(TrackSource.Remote, item.toPlayingTrack().source)
    }

    @Test
    fun `un identifiant distant purement numerique reste distant`() {
        // Le cas qui piège : `"1234"` se convertit en Long sans erreur.
        val item = remoteSong(id = "1234").toMediaItem()

        assertNull(item.localSongId)
        assertEquals(TrackSource.Remote, item.toPlayingTrack().source)
    }

    @Test
    fun `l'URI d'une piste distante n'est pas joignable telle quelle`() {
        // C'est un marqueur : le frapper au moment de bâtir la file périmerait
        // les tickets des derniers morceaux avant qu'on ne les atteigne.
        val item = remoteSong(id = "c07f8d98").toMediaItem()

        val uri = item.localConfiguration?.uri
        assertEquals("waveflow", uri?.scheme)
        assertEquals("c07f8d98", uri?.let { trackIdOfRemoteUri(it) })
    }

    @Test
    fun `une URI locale n'est pas prise pour une piste a resoudre`() {
        val uri = "content://media/external/audio/media/42".toUri()

        assertNull(trackIdOfRemoteUri(uri))
    }

    @Test
    fun `l'identite d'une piste distante est la meme des deux cotes`() {
        // L'écran s'en sert pour souligner la ligne en cours sans construire de
        // MediaItem : les deux doivent coïncider.
        val remote = remoteSong(id = "c07f8d98")

        assertEquals(remote.mediaId, remote.toMediaItem().toPlayingTrack().mediaId)
    }

    @Test
    fun `un debit qui ne decrit rien n'entre pas dans la cle`() {
        // Zéro n'est pas un rendu : le laisser dans la clé ouvrirait une
        // seconde entrée de cache pour ce que le serveur sert déjà sous
        // l'absence de débit.
        val sansDebit = cacheKeyOf("piste", "raw")

        assertEquals(sansDebit, cacheKeyOf("piste", "raw", bitrate = 0))
        assertEquals(sansDebit, cacheKeyOf("piste", "raw", bitrate = -1))
        assertNotEquals(sansDebit, cacheKeyOf("piste", "raw", bitrate = 128))
    }

    @Test
    fun `les metadonnees d'une piste distante suivent jusqu'au lecteur`() {
        val track = remoteSong(id = "a", title = "Résonance", artist = "Bruit de Fond", album = "Écho")
            .toMediaItem()
            .toPlayingTrack()

        assertEquals("Résonance", track.title)
        assertEquals("Bruit de Fond", track.artist)
        assertEquals("Écho", track.album)
        // Le catalogue v2 n'expose aucun point d'accès aux pochettes.
        assertNull(track.artworkUri)
        assertTrue(track.source == TrackSource.Remote)
    }
}
