package app.waveflow.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun `une pochette donne une adresse sur son hachage`() {
        val uri = urls().forHash("abc123")

        assertEquals("https://musique.test/api/v2/artwork/abc123", uri.toString())
    }

    @Test
    fun `une jaquette remplacee change d'adresse`() {
        // C'est tout l'intérêt du hachage : sur l'identifiant de l'entité,
        // l'adresse serait identique et le cache resservirait l'ancienne image
        // — le serveur annonce `max-age=86400`.
        val avant = urls().forHash("abc123")
        val apres = urls().forHash("def456")

        assertNotEquals(avant, apres)
    }

    @Test
    fun `un album et ses pistes partagent une seule adresse`() {
        // Relevé sur le serveur : ils portent le même hachage. Une entrée de
        // cache et un téléchargement, au lieu d'un par ligne affichée.
        val album = urls().forHash("abc123")
        val piste = urls().forHash("abc123")

        assertEquals(album, piste)
    }

    @Test
    fun `un hachage est encode et ne peut pas designer un autre point d'API`() {
        val uri = urls().forHash("../stream/vole")

        assertEquals("https://musique.test/api/v2/artwork/..%2Fstream%2Fvole", uri.toString())
    }

    @Test
    fun `sans pochette aucune adresse n'est produite`() {
        // Une adresse produirait un 404 par ligne de liste, à chaque défilement.
        assertNull(urls().forHash(null))
        assertNull(urls().forHash("   "))
    }

    @Test
    fun `le prefixe de proxy est conserve`() {
        val uri = urls("https://hote.test/musique").forHash("abc123")

        assertEquals("https://hote.test/musique/api/v2/artwork/abc123", uri.toString())
    }

    @Test
    fun `une adresse de serveur invalide ne fait pas tomber une liste`() {
        // Ces adresses sont construites en plein rendu d'une liste : mieux vaut
        // une ligne sans vignette qu'une exception qui vide l'écran.
        assertNull(urls(serverUrl = "   ").forHash("abc123"))
    }
}
