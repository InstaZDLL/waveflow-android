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

    /** Une lecture coupée, et les en-têtes de la réponse qu'elle lisait. */
    private class Coupure(val erreur: IOException, val entetes: Map<String, List<String>>)

    /**
     * Une coupure telle que la vraie source HTTP la lève : le serveur ferme la
     * liaison au milieu d'un flux sans longueur, et la lecture échoue. Le
     * serveur dit `Accept-Ranges: none` d'un transcodage en direct, `bytes`
     * d'un fichier entier.
     */
    private fun coupure(plages: String = "none"): Coupure {
        server.enqueue(
            MockResponse()
                .setHeader("Accept-Ranges", plages)
                .setChunkedBody(okio.Buffer().write(ByteArray(64 * 1_024)), 4_096)
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val source = DefaultHttpDataSource.Factory().createDataSource()
        return try {
            source.open(DataSpec(server.url("/api/v2/stream/t?format=opus").toString().toUri()))
            val tampon = ByteArray(4_096)
            do {
                val lus = source.read(tampon, 0, tampon.size)
            } while (lus != C.RESULT_END_OF_INPUT)
            throw AssertionError("le serveur factice devait couper")
        } catch (erreur: IOException) {
            Coupure(erreur, source.responseHeaders)
        } finally {
            source.close()
        }
    }

    /** Un échec vu par le lecteur : sur le flux d'un marqueur, et non sur l'URL résolue. */
    private fun echecSur(
        marqueur: String,
        erreur: IOException,
        numero: Int = 1,
        entetes: Map<String, List<String>> = emptyMap(),
    ) = LoadErrorInfo(
        LoadEventInfo.Builder(0L, DataSpec.Builder().setUri(marqueur).build(), 0L)
            .setResponseHeaders(entetes)
            .build(),
        MediaLoadData(C.DATA_TYPE_MEDIA),
        erreur,
        numero,
    )

    private fun echecSur(marqueur: String, coupure: Coupure) =
        echecSur(marqueur, coupure.erreur, entetes = coupure.entetes)

    private fun delaiDeMedia3(marqueur: String, coupure: Coupure) =
        DefaultLoadErrorHandlingPolicy().getRetryDelayMsFor(echecSur(marqueur, coupure))

    @Test
    fun `un transcodage coupe en route n'est pas retente`() {
        // R25 : Media3 le rouvrirait à l'octet atteint, ce que le serveur
        // refuse, ou du début, ce qui rejouerait ce qu'on a entendu.
        assertEquals(C.TIME_UNSET, politique.getRetryDelayMsFor(echecSur(TRANSCODE, coupure())))
    }

    @Test
    fun `un transcodage servi entier et coupe en route garde les delais de Media3`() {
        // R25 : le serveur avait le transcodage entier et l'a servi comme un
        // fichier ; Media3 le rouvre à l'octet atteint, et le serveur l'accepte.
        val coupure = coupure(plages = "bytes")

        assertEquals(delaiDeMedia3(TRANSCODE, coupure), politique.getRetryDelayMsFor(echecSur(TRANSCODE, coupure)))
    }

    @Test
    fun `un original coupe en route garde les delais de Media3`() {
        // R26 : l'original accepte les plages, Media3 le rouvre à l'octet
        // atteint. C'est le marqueur qui en décide, même quand la réponse ne le
        // dit pas — un mandataire peut retirer l'en-tête — : avec lui, ce test
        // ne verrait plus que l'en-tête, et non le marqueur.
        val coupure = coupure(plages = "none")

        assertEquals(delaiDeMedia3(ORIGINAL, coupure), politique.getRetryDelayMsFor(echecSur(ORIGINAL, coupure)))
    }

    @Test
    fun `un transcodage qui ne s'ouvre pas garde les delais de Media3`() {
        // R30 : une coupure qui dure fait échouer la demande à l'ouverture, une
        // panne réseau ordinaire, que Media3 retente puis rend.
        val adresse = server.url("/api/v2/stream/t?format=opus").toString().toUri()
        server.shutdown()
        val muet = DefaultHttpDataSource.Factory().createDataSource()
        val erreur = try {
            muet.open(DataSpec(adresse))
            throw AssertionError("le serveur arrêté devait refuser la connexion")
        } catch (erreur: IOException) {
            erreur
        }

        assertEquals(
            DefaultLoadErrorHandlingPolicy().getRetryDelayMsFor(echecSur(TRANSCODE, erreur, 2)),
            politique.getRetryDelayMsFor(echecSur(TRANSCODE, erreur, 2)),
        )
    }

    private companion object {
        const val TRANSCODE = "waveflow://track/c07f8d98?format=opus&bitrate=96"
        const val ORIGINAL = "waveflow://track/c07f8d98"
        const val SEMENCE = 57
        const val TIRAGES = 50
    }
}
