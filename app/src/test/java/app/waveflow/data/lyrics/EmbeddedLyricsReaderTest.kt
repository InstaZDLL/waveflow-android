package app.waveflow.data.lyrics

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * La lecture des paroles embarquées de bout en bout : un vrai fichier, le vrai
 * extracteur de Media3 par `MetadataRetriever` (`docs/paroles.md`, P2).
 *
 * Les fichiers sont fabriqués à l'octet près, juste assez pour que l'extracteur
 * les reconnaisse : c'est ce qu'il rend de leurs tags qu'on éprouve, pas leur
 * son.
 */
@RunWith(RobolectricTestRunner::class)
class EmbeddedLyricsReaderTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()

    /**
     * Lit [fichier] sur un autre fil, en faisant avancer l'horloge.
     *
     * `MetadataRetriever` guette les échecs de préparation par un message
     * différé sur son propre fil ; sous Robolectric, l'horloge ne bouge que si
     * on la pousse, et un fichier illisible ne remonterait jamais.
     */
    private fun lire(fichier: File): Lyrics? {
        val lecture = CompletableFuture<Lyrics?>()
        thread {
            runCatching { runBlocking { EmbeddedLyricsReader(app).read(Uri.fromFile(fichier)) } }
                .onSuccess { lecture.complete(it) }
                .onFailure { lecture.completeExceptionally(it) }
        }
        val echeance = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS)
        while (!lecture.isDone) {
            check(System.nanoTime() < echeance) { "lecture des tags trop longue" }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PAS_MS))
            Thread.sleep(PAS_MS)
        }
        return lecture.get()
    }

    @Test
    fun `un MP3 rend son USLT ID3`() {
        val mp3 = dossier.newFile("chanson.mp3").apply {
            writeBytes(
                id3v23(cadre("USLT", byteArrayOf(3) + "fra".toByteArray() + 0 + "[00:02.00]Bonjour".toByteArray())) +
                    tramesMp3(),
            )
        }

        assertEquals(Lyrics.Synced(listOf(SyncedLine(2_000, "Bonjour"))), lire(mp3))
    }

    @Test
    fun `un FLAC rend son commentaire Vorbis LYRICS`() {
        val flac = dossier.newFile("chanson.flac").apply { writeBytes(flacAvec("LYRICS=Une ligne\nUne autre")) }

        assertEquals(Lyrics.Plain(listOf("Une ligne", "Une autre")), lire(flac))
    }

    @Test
    fun `un fichier sans paroles n'en a pas`() {
        val mp3 = dossier.newFile("muet.mp3").apply { writeBytes(tramesMp3()) }

        assertNull(lire(mp3))
    }

    @Test
    fun `un fichier que l'extracteur ne reconnait pas n'a pas de paroles`() {
        val bruit = dossier.newFile("bruit.mp3").apply { writeBytes(ByteArray(64) { 7 }) }

        assertNull(lire(bruit))
    }

    // --- MP3 ------------------------------------------------------------------

    /** Un en-tête ID3v2.3 autour de [cadres] : taille en entiers « synchsafe ». */
    private fun id3v23(cadres: ByteArray): ByteArray {
        val n = cadres.size
        val taille = byteArrayOf(
            (n shr 21 and 0x7F).toByte(),
            (n shr 14 and 0x7F).toByte(),
            (n shr 7 and 0x7F).toByte(),
            (n and 0x7F).toByte(),
        )
        return "ID3".toByteArray() + byteArrayOf(3, 0, 0) + taille + cadres
    }

    private fun cadre(id: String, donnees: ByteArray): ByteArray =
        id.toByteArray() + entier32(donnees.size) + byteArrayOf(0, 0) + donnees

    /** Vingt trames MPEG-1 couche III, 128 kbit/s, 44,1 kHz : 417 octets chacune. */
    private fun tramesMp3(): ByteArray = ByteArrayOutputStream().apply {
        repeat(TRAMES) {
            val trame = ByteArray(TAILLE_TRAME)
            trame[0] = 0xFF.toByte()
            trame[1] = 0xFB.toByte()
            trame[2] = 0x90.toByte()
            trame[3] = 0x00
            write(trame)
        }
    }.toByteArray()

    // --- FLAC -----------------------------------------------------------------

    /**
     * `fLaC`, un bloc STREAMINFO, un bloc VORBIS_COMMENT (le dernier), puis un
     * début de trame pour que l'extracteur pose sa table de saut.
     */
    private fun flacAvec(commentaire: String): ByteArray = ByteArrayOutputStream().apply {
        write("fLaC".toByteArray())

        val info = ByteArrayOutputStream().apply {
            write(entier16(4096)) // taille de bloc minimale
            write(entier16(4096)) // maximale
            write(ByteArray(6)) // tailles de trame inconnues
            // 44 100 Hz sur 20 bits, 2 canaux (1 sur 3 bits), 16 bits (15 sur 5),
            // puis 36 bits de nombre d'échantillons : 441 000.
            val echantillons = 441_000L
            val paquet = (44_100L shl 44) or (1L shl 41) or (15L shl 36) or echantillons
            for (decalage in 56 downTo 0 step 8) write((paquet ushr decalage).toInt() and 0xFF)
            write(ByteArray(16)) // MD5
        }.toByteArray()
        write(0x00)
        write(entier24(info.size))
        write(info)

        val vendeur = "waveflow".toByteArray()
        val texte = commentaire.toByteArray(Charsets.UTF_8)
        val vorbis = ByteArrayOutputStream().apply {
            write(petitBoutiste32(vendeur.size))
            write(vendeur)
            write(petitBoutiste32(1))
            write(petitBoutiste32(texte.size))
            write(texte)
        }.toByteArray()
        write(0x84) // dernier bloc, type 4
        write(entier24(vorbis.size))
        write(vorbis)

        // Un début de trame (code de synchronisation), puis du remplissage.
        write(byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x69, 0x08, 0x00, 0x00))
        write(ByteArray(256))
    }.toByteArray()

    private fun entier32(n: Int) = byteArrayOf(
        (n ushr 24).toByte(),
        (n ushr 16).toByte(),
        (n ushr 8).toByte(),
        n.toByte(),
    )

    private fun entier24(n: Int) = byteArrayOf((n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())

    private fun entier16(n: Int) = byteArrayOf((n ushr 8).toByte(), n.toByte())

    private fun petitBoutiste32(n: Int) = byteArrayOf(
        n.toByte(),
        (n ushr 8).toByte(),
        (n ushr 16).toByte(),
        (n ushr 24).toByte(),
    )

    private operator fun ByteArray.plus(octet: Int): ByteArray = this + byteArrayOf(octet.toByte())

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val PAS_MS = 20L
        const val TRAMES = 20
        const val TAILLE_TRAME = 417
    }
}
