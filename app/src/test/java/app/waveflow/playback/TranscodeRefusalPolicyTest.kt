package app.waveflow.playback

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.random.Random

/**
 * Ce que la politique répond à chaque échec, sans lecteur.
 *
 * La chaîne réelle — un serveur qui répond 429, le lecteur qui attend —
 * s'éprouve dans `TranscodeRefusalChainTest`. Ici, les bornes : délai, gigue,
 * nombre d'essais, et ce qui n'est pas un refus. Les renvois R17 à R20 pointent
 * vers `docs/deplacement-dans-un-transcodage.md`.
 */
@RunWith(RobolectricTestRunner::class)
class TranscodeRefusalPolicyTest {

    private val politique = TranscodeRefusalPolicy(Random(SEMENCE))

    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    /**
     * Un refus tel que la vraie source HTTP le lève, et non tel qu'on
     * l'imagine : `HttpURLConnection` range par exemple la ligne de statut sous
     * une clé `null`, ce qu'un refus construit à la main n'aurait pas montré.
     */
    private fun refus(vararg entetes: Pair<String, String>): InvalidResponseCodeException {
        server.enqueue(
            MockResponse().setResponseCode(429).apply {
                entetes.forEach { (nom, valeur) -> setHeader(nom, valeur) }
            },
        )
        val source = DefaultHttpDataSource.Factory().createDataSource()
        return try {
            source.open(DataSpec(server.url("/api/v2/stream/t?format=opus").toString().toUri()))
            throw AssertionError("le serveur factice devait refuser")
        } catch (refus: InvalidResponseCodeException) {
            refus
        } finally {
            source.close()
        }
    }

    private fun echec(erreur: IOException, numero: Int) = LoadErrorInfo(
        LoadEventInfo.Builder(0L, DataSpec.Builder().setUri("http://serveur/").build(), 0L).build(),
        MediaLoadData(C.DATA_TYPE_MEDIA),
        erreur,
        numero,
    )

    /** Assez de tirages pour que la gigue se voie, et ses bornes avec elle. */
    private fun delais(erreur: IOException, numero: Int = 1) =
        List(TIRAGES) { politique.getRetryDelayMsFor(echec(erreur, numero)) }

    @Test
    fun `le delai annonce est attendu, jamais moins`() {
        // R18 : la politique de Media3 retentait aussitôt, puis à 1 s.
        val tires = delais(refus("Retry-After" to "3"))

        assertTrue("jamais avant le délai : $tires", tires.all { it >= 3_000L })
    }

    @Test
    fun `la gigue s'ajoute au delai sans en depasser la moitie`() {
        // R19 : sans elle, les appareils refusés ensemble reviennent ensemble.
        val tires = delais(refus("Retry-After" to "2"))

        assertTrue("dans [2 s, 3 s] : $tires", tires.all { it in 2_000L..3_000L })
        assertTrue("des tirages différents : $tires", tires.toSet().size > 1)
    }

    @Test
    fun `l'en-tete se lit quelle que soit sa casse`() {
        val tires = delais(refus("retry-after" to "4"))

        assertTrue(tires.all { it >= 4_000L })
    }

    @Test
    fun `sans en-tete lisible, une seconde`() {
        // R18 : la forme date de l'en-tête compte comme illisible.
        val sans = delais(refus())
        val date = delais(refus("Retry-After" to "Wed, 21 Oct 2026 07:28:00 GMT"))

        assertTrue(sans.all { it in 1_000L..1_500L })
        assertTrue(date.all { it in 1_000L..1_500L })
    }

    @Test
    fun `un delai aberrant est borne`() {
        val tires = delais(refus("Retry-After" to "86400000000000"))

        assertTrue(tires.all { it in TranscodeRefusalPolicy.DELAI_MAX_MS..TranscodeRefusalPolicy.DELAI_MAX_MS * 3 / 2 })
    }

    @Test
    fun `quatre relances, puis l'abandon`() {
        // R20 : le compte d'échecs inclut le premier refus.
        val erreur = refus("Retry-After" to "1")

        assertTrue(politique.getRetryDelayMsFor(echec(erreur, TranscodeRefusalPolicy.RELANCES_MAX)) >= 1_000L)
        assertEquals(C.TIME_UNSET, politique.getRetryDelayMsFor(echec(erreur, TranscodeRefusalPolicy.RELANCES_MAX + 1)))
    }

    @Test
    fun `les relances tiennent sous le seuil ou Media3 rend l'erreur`() {
        // Au-delà, le lecteur recevrait l'erreur relances encore en cours.
        assertTrue(politique.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA) >= TranscodeRefusalPolicy.RELANCES_MAX)
    }

    @Test
    fun `une autre panne garde les delais et le seuil de Media3`() {
        // R20. Un quatrième échec n'est pas forcément fatal : Media3 remet son
        // compte à zéro quand le flux avait progressé, et doit donc encore
        // proposer une relance — ce qu'une politique qui y renonçait d'elle-même
        // empêchait.
        val media3 = DefaultLoadErrorHandlingPolicy()
        val coupure = IOException("connexion perdue")

        for (numero in 1..6) {
            assertEquals(
                "échec n° $numero",
                media3.getRetryDelayMsFor(echec(coupure, numero)),
                politique.getRetryDelayMsFor(echec(coupure, numero)),
            )
        }
        assertEquals(
            media3.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA),
            politique.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA),
        )
    }

    @Test
    fun `un refus enveloppe par la chaine de lecture se reconnait`() {
        val enveloppe = IOException("cache", refus("Retry-After" to "2"))

        assertTrue(delais(enveloppe).all { it >= 2_000L })
    }

    @Test
    fun `un autre statut HTTP n'est pas un refus de transcodage`() {
        val introuvable = InvalidResponseCodeException(
            /* responseCode = */ 503,
            /* responseMessage = */ "Service Unavailable",
            /* cause = */ null,
            /* headerFields = */ mapOf("Retry-After" to listOf("9")),
            /* dataSpec = */ DataSpec.Builder().setUri("http://serveur/").build(),
            /* responseBody = */ ByteArray(0),
        )

        assertEquals(0L, politique.getRetryDelayMsFor(echec(introuvable, 1)))
    }

    private companion object {
        const val SEMENCE = 57
        const val TIRAGES = 50
    }
}
