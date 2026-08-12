package app.waveflow.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Les adresses de pochettes, et surtout quand il ne faut pas en produire. */
@RunWith(RobolectricTestRunner::class)
class ArtworkUrlsTest {

    private fun urls(serverUrl: String = "https://musique.test") =
        ArtworkUrls(serverUrl, ServerHttp())

    @Test
    fun `une entite avec pochette donne une adresse sur son identifiant`() {
        // Sur l'identifiant plutôt que sur le hachage : le point d'API accepte
        // les deux, et l'identifiant ne bouge pas quand la jaquette est
        // réencodée.
        val uri = urls().forEntity(entityId = "1daf991a", artworkHash = "abc123")

        assertEquals("https://musique.test/api/v2/artwork/1daf991a", uri.toString())
    }

    @Test
    fun `sans pochette aucune adresse n'est produite`() {
        // Une adresse produirait un 404 par ligne de liste, à chaque défilement.
        assertNull(urls().forEntity(entityId = "1daf991a", artworkHash = null))
        assertNull(urls().forEntity(entityId = "1daf991a", artworkHash = "   "))
    }

    @Test
    fun `le prefixe de proxy est conserve`() {
        val uri = urls("https://hote.test/musique").forEntity("1daf991a", "abc123")

        assertEquals("https://hote.test/musique/api/v2/artwork/1daf991a", uri.toString())
    }

    @Test
    fun `une adresse de serveur invalide ne fait pas tomber une liste`() {
        // Ces adresses sont construites en plein rendu d'une liste : mieux vaut
        // une ligne sans vignette qu'une exception qui vide l'écran.
        assertNull(urls(serverUrl = "   ").forEntity("1daf991a", "abc123"))
    }
}
