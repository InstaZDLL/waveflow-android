package app.waveflow.data.lyrics

import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import kotlin.system.measureTimeMillis

/**
 * Les paroles embarquées, telles que Media3 1.11.1 les rend (`docs/paroles.md`,
 * P2) : ID3 en cadres bruts, Vorbis en commentaires, MP4 en cadre texte.
 */
@RunWith(RobolectricTestRunner::class)
class EmbeddedLyricsTest {

    @Test
    fun `un USLT se decode dans chacun des quatre encodages d'ID3`() {
        assertEquals("Latin-1 é", decodeUslt(uslt(LATIN1, "desc", "Latin-1 é")))
        assertEquals("UTF-8 ♪", decodeUslt(uslt(UTF8, "desc", "UTF-8 ♪")))
        assertEquals("UTF-16 ♪", decodeUslt(uslt(UTF16, "desc", "UTF-16 ♪")))
        assertEquals("UTF-16BE ♪", decodeUslt(uslt(UTF16BE, "", "UTF-16BE ♪")))
    }

    @Test
    fun `un USLT tronque ou vide ne donne rien`() {
        assertNull(decodeUslt(byteArrayOf(3, 'f'.code.toByte())))
        assertNull(decodeUslt(uslt(UTF8, "desc", "   ")))
    }

    @Test
    fun `un SYLT en millisecondes donne des lignes rangees`() {
        val lignes = decodeSylt(sylt(UTF16, MILLISECONDES, listOf("deux" to 4_000, "une" to 1_000)))

        assertEquals(listOf(SyncedLine(1_000, "une"), SyncedLine(4_000, "deux")), lignes)
    }

    @Test
    fun `un SYLT en trames MPEG ne se lit pas`() {
        assertNull(decodeSylt(sylt(UTF8, TRAMES_MPEG, listOf("une" to 1_000))))
    }

    @Test
    fun `un SYLT d'accords ne passe pas pour des paroles`() {
        val accords = BinaryFrame("SYLT", sylt(UTF8, MILLISECONDES, listOf("Bb F" to 1_000), contenu = ACCORDS))
        val texte = BinaryFrame("USLT", uslt(UTF8, "", "Les vraies paroles"))

        assertNull(decodeSylt(accords.data))
        assertEquals(Lyrics.Plain(listOf("Les vraies paroles")), lyricsOf(listOf(accords, texte)))
    }

    @Test
    fun `un SYLT horodate syllabe par syllabe se regroupe en lignes`() {
        val lignes = decodeSylt(
            sylt(
                UTF8,
                MILLISECONDES,
                listOf("Bon" to 1_000, "jour" to 1_300, "\nle " to 2_000, "monde" to 2_400),
            ),
        )

        assertEquals(listOf(SyncedLine(1_000, "Bonjour"), SyncedLine(2_000, "le monde")), lignes)
    }

    /**
     * 400 000 syllabes sur une seule ligne : recoller la ligne entière à chaque
     * syllabe prend une demi-minute ; l'assembler dans un tampon, une fraction
     * de seconde. La durée se mesure ici : Robolectric n'applique pas le
     * `timeout` de `@Test`.
     */
    @Test
    fun `une ligne SYLT de tres nombreuses syllabes s'assemble en un passage`() {
        val cadre = sylt(UTF8, MILLISECONDES, List(400_000) { "a" to it } + ("\nfin" to 400_000))

        var lignes: List<SyncedLine>? = null
        val dureeMs = measureTimeMillis { lignes = decodeSylt(cadre) }

        assertEquals(listOf(400_000, 3), lignes?.map { it.text.length })
        assertTrue("décodage en $dureeMs ms", dureeMs < 5_000)
    }

    @Test
    fun `un fragment vide d'un SYLT syllabique ferme sa ligne a son instant`() {
        val lignes = decodeSylt(
            sylt(
                UTF8,
                MILLISECONDES,
                listOf("Bon" to 1_000, "jour" to 1_300, "" to 1_800, "Pause" to 5_000, "\nFin" to 7_000),
            ),
        )

        assertEquals(
            listOf(
                SyncedLine(1_000, "Bonjour"),
                SyncedLine(1_800, ""),
                SyncedLine(5_000, "Pause"),
                SyncedLine(7_000, "Fin"),
            ),
            lignes,
        )
    }

    @Test
    fun `les cadres ID3v2_2 ULT et SLT portent aussi des paroles`() {
        // ID3v2.2 nomme ses cadres en trois lettres, et Media3 les garde tels quels.
        assertEquals(
            Lyrics.Plain(listOf("Texte ancien")),
            lyricsOf(listOf(BinaryFrame("ULT", uslt(LATIN1, "", "Texte ancien")))),
        )
        assertEquals(
            Lyrics.Synced(listOf(SyncedLine(700, "Ancienne"))),
            lyricsOf(listOf(BinaryFrame("SLT", sylt(LATIN1, MILLISECONDES, listOf("Ancienne" to 700))))),
        )
    }

    @Test
    fun `un SYLT en Latin-1 garde ses accents`() {
        val lignes = decodeSylt(sylt(LATIN1, MILLISECONDES, listOf("été" to 500)))

        assertEquals(listOf(SyncedLine(500, "été")), lignes)
    }

    @Test
    fun `les commentaires Vorbis LYRICS et UNSYNCEDLYRICS portent des paroles, quelle que soit leur casse`() {
        assertEquals(
            Lyrics.Plain(listOf("Une ligne")),
            lyricsOf(listOf(VorbisComment("lyrics", "Une ligne"))),
        )
        assertEquals(
            Lyrics.Synced(listOf(SyncedLine(1_000, "Une ligne"))),
            lyricsOf(listOf(VorbisComment("UNSYNCEDLYRICS", "[00:01.00]Une ligne"))),
        )
        assertNull(lyricsOf(listOf(VorbisComment("COMMENT", "Pas des paroles"))))
    }

    @Test
    fun `l'atome MP4 de paroles arrive en cadre texte USLT`() {
        val cadre = TextInformationFrame("USLT", null, ImmutableList.of("Première\nSeconde"))

        assertEquals(Lyrics.Plain(listOf("Première", "Seconde")), lyricsOf(listOf(cadre)))
    }

    @Test
    fun `des paroles synchronisees passent devant des simples, quel que soit l'ordre des tags`() {
        val simple = BinaryFrame("USLT", uslt(UTF8, "", "Texte simple"))
        val synchro = BinaryFrame("SYLT", sylt(UTF8, MILLISECONDES, listOf("chantée" to 2_000)))

        assertEquals(Lyrics.Synced(listOf(SyncedLine(2_000, "chantée"))), lyricsOf(listOf(simple, synchro)))
    }

    @Test
    fun `un USLT qui contient un LRC compte comme synchronise`() {
        val simple = VorbisComment("UNSYNCEDLYRICS", "Texte simple")
        val lrc = BinaryFrame("USLT", uslt(UTF8, "", "[00:03.00]chantée"))

        assertEquals(Lyrics.Synced(listOf(SyncedLine(3_000, "chantée"))), lyricsOf(listOf(simple, lrc)))
    }

    @Test
    fun `les autres cadres ne portent pas de paroles`() {
        assertNull(lyricsOf(listOf(TextInformationFrame("TIT2", null, ImmutableList.of("Titre")))))
        assertNull(lyricsOf(listOf(BinaryFrame("PRIV", byteArrayOf(1, 2, 3)))))
        assertNull(lyricsOf(emptyList()))
    }

    // --- Fabrique de cadres ID3, octet par octet ------------------------------

    private fun uslt(encodage: Int, descripteur: String, texte: String): ByteArray =
        ByteArrayOutputStream().apply {
            write(encodage)
            write("fra".toByteArray(Charsets.ISO_8859_1))
            write(chaine(encodage, descripteur))
            write(texte.encoder(encodage))
        }.toByteArray()

    private fun sylt(encodage: Int, format: Int, lignes: List<Pair<String, Int>>, contenu: Int = PAROLES): ByteArray =
        ByteArrayOutputStream().apply {
            write(encodage)
            write("fra".toByteArray(Charsets.ISO_8859_1))
            write(format)
            write(contenu)
            write(chaine(encodage, ""))
            for ((texte, instant) in lignes) {
                write(chaine(encodage, texte))
                write(
                    byteArrayOf(
                        (instant ushr 24).toByte(),
                        (instant ushr 16).toByte(),
                        (instant ushr 8).toByte(),
                        instant.toByte(),
                    ),
                )
            }
        }.toByteArray()

    /** Une chaîne terminée : un nul, deux en UTF-16. */
    private fun chaine(encodage: Int, texte: String): ByteArray =
        texte.encoder(encodage) + if (encodage == UTF16 || encodage == UTF16BE) byteArrayOf(0, 0) else byteArrayOf(0)

    private fun String.encoder(encodage: Int): ByteArray = when (encodage) {
        // UTF-16 avec marque d'ordre d'octets, petit-boutiste comme l'écrivent
        // la plupart des éditeurs de tags.
        UTF16 -> byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + toByteArray(Charsets.UTF_16LE)
        UTF16BE -> toByteArray(Charsets.UTF_16BE)
        UTF8 -> toByteArray(Charsets.UTF_8)
        else -> toByteArray(Charsets.ISO_8859_1)
    }

    private companion object {
        const val LATIN1 = 0
        const val UTF16 = 1
        const val UTF16BE = 2
        const val UTF8 = 3
        const val TRAMES_MPEG = 1
        const val MILLISECONDES = 2
        const val PAROLES = 1
        const val ACCORDS = 5
    }
}
