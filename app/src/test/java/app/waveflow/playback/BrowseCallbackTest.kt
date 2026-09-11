package app.waveflow.playback

import app.waveflow.model.StreamQuality
import app.waveflow.model.StreamRendering
import app.waveflow.testing.remoteSong
import app.waveflow.testing.song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ce que devient une file en entrant dans le service.
 *
 * Par [BrowseCallback.addMediaItems] plutôt que par `onAddMediaItems` : la
 * session et l'appelant n'y sont pas consultés, et les fabriquer n'éprouverait
 * que leur fabrication. `PlaybackServiceQualityTest` prouve de son côté qu'une
 * file envoyée par un vrai contrôleur passe bien par là.
 *
 * Robolectric pour disposer d'un vrai `android.net.Uri`.
 */
@RunWith(RobolectricTestRunner::class)
class BrowseCallbackTest {

    private fun rappel(rendu: () -> ListenableFuture<StreamRendering>) =
        BrowseCallback(BrowseTree { BrowseSnapshot() }, rendu)

    private fun immediat(qualite: StreamQuality): () -> ListenableFuture<StreamRendering> =
        { Futures.immediateFuture(qualite.rendering) }

    @Test
    fun `une piste du serveur entre dans la file dans la qualite choisie`() {
        val file = rappel(immediat(StreamQuality.Economie))
            .addMediaItems(listOf(remoteSong(id = "c07f8d98").toMediaItem()))
            .get()

        val config = file.single().localConfiguration!!
        assertEquals(StreamQuality.Economie.rendering, renderingOfRemoteUri(config.uri))
        assertEquals(cacheKeyOf("c07f8d98", "opus", 96), config.customCacheKey)
    }

    @Test
    fun `une piste de l'appareil traverse sans recevoir de rendu`() {
        val locale = song(id = 42L).toMediaItem()

        val file = rappel(immediat(StreamQuality.Economie)).addMediaItems(listOf(locale)).get()

        assertEquals(listOf(locale), file)
    }

    @Test
    fun `tant que le reglage n'est pas lu, la file l'attend`() {
        // Le démarrage à froid : Android Auto relance la lecture avant que le
        // fichier de préférences n'ait été lu. Poser le défaut à ce moment-là
        // ferait partir en qualité d'origine une écoute réglée en Économie.
        val reglage = SettableFuture.create<StreamRendering>()

        val file = rappel { reglage }.addMediaItems(listOf(remoteSong(id = "a").toMediaItem()))

        assertFalse("la file ne doit pas partir sans le réglage", file.isDone)

        reglage.set(StreamQuality.Economie.rendering)

        assertTrue(file.isDone)
        assertEquals(
            StreamQuality.Economie.rendering,
            renderingOfRemoteUri(file.get().single().localConfiguration!!.uri),
        )
    }

    @Test
    fun `une file entiere se traduit dans une seule qualite`() {
        // Le réglage est lu une fois pour la file, et non une fois par piste :
        // un changement pendant la traduction partagerait sinon un album entre
        // deux qualités.
        var lectures = 0
        val rappel = rappel {
            lectures++
            Futures.immediateFuture(StreamQuality.Haute.rendering)
        }

        rappel.addMediaItems(listOf("a", "b", "c").map { remoteSong(id = it).toMediaItem() }).get()

        assertEquals("une seule lecture du réglage pour trois pistes", 1, lectures)
    }
}
