package app.waveflow.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import app.waveflow.model.ModeDeGain
import app.waveflow.model.ReglagesEgaliseur
import app.waveflow.model.ReglagesReplayGain
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

/**
 * Les deux `AudioProcessor`, sur des tampons, hors de la chaîne : Robolectric
 * ne décode pas l'audio. Ce qui est éprouvé ici, c'est ce que chacun fait de ce
 * que Media3 lui passe — format, `flush`, réglages relus à chaque tampon.
 * Règles G4, A4, E5, C1 et C2 de `docs/audio-avance.md`.
 */
class TraitementsTest {

    private val stereo16 = AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)

    /** Une chaîne au ReplayGain actif par titre, sans pré-amplification ni gain par défaut. */
    private fun chaine(): ChaineAudio = ChaineAudio().apply {
        replayGain = ReglagesReplayGain(actif = true, mode = ModeDeGain.ParTitre, eviterSaturation = false)
    }

    private fun flush(processeur: AudioProcessor, periode: Any?) {
        processeur.flush(AudioProcessor.StreamMetadata.Builder().setPeriodUid(periode).build())
    }

    private fun seize(vararg echantillons: Short): ByteBuffer =
        ByteBuffer.allocateDirect(echantillons.size * 2).order(ByteOrder.nativeOrder()).apply {
            echantillons.forEach { putShort(it) }
            flip()
        }

    private fun flottants(vararg echantillons: Float): ByteBuffer =
        ByteBuffer.allocateDirect(echantillons.size * 4).order(ByteOrder.nativeOrder()).apply {
            echantillons.forEach { putFloat(it) }
            flip()
        }

    private fun lireFlottants(sortie: ByteBuffer): FloatArray = FloatArray(sortie.remaining() / 4) { sortie.getFloat() }

    private fun lireSeize(sortie: ByteBuffer): ShortArray = ShortArray(sortie.remaining() / 2) { sortie.getShort() }

    private fun traiter(processeur: AudioProcessor, entree: ByteBuffer): ByteBuffer {
        processeur.queueInput(entree)
        return processeur.getOutput()
    }

    // ---------------------------------------------------------------------
    // Le ReplayGain
    // ---------------------------------------------------------------------

    /** C2 : du 16 bits en entrée, du flottant en sortie, à la même échelle que Media3. */
    @Test
    fun `le ReplayGain rend du flottant`() {
        val c = chaine()
        val rg = c.traitementReplayGain
        assertEquals(C.ENCODING_PCM_FLOAT, rg.configure(stereo16).encoding)
        flush(rg, null)
        c.replayGain = c.replayGain.copy(actif = false)

        val sortie = lireFlottants(traiter(rg, seize(16_384, -32_768)))

        assertArrayEquals(floatArrayOf(0.5f, -1f), sortie, 0f)
    }

    /**
     * G4 : le gain est celui de la période annoncée au `flush`, que la sortie a
     * retenu sous son identifiant. Une autre période, une autre piste.
     */
    @Test
    fun `le gain suit la piste annoncee au flush`() {
        val c = chaine()
        val rg = c.traitementReplayGain
        rg.configure(stereo16)
        c.gains.retenir("piste A", GainDePiste(gainDb = -6.0))
        c.gains.retenir("piste B", GainDePiste(gainDb = 0.0))

        flush(rg, "piste A")
        val a = lireFlottants(traiter(rg, seize(16_384, 16_384)))
        flush(rg, "piste B")
        val b = lireFlottants(traiter(rg, seize(16_384, 16_384)))

        assertTrue(a.contentToString(), abs(a[0] - 0.5f * 0.501f) < 1e-3f)
        assertArrayEquals(floatArrayOf(0.5f, 0.5f), b, 0f)
    }

    /** G4 : une période dont la sortie n'a rien retenu prend le gain par défaut. */
    @Test
    fun `une piste inconnue prend le gain par defaut`() {
        val c = chaine()
        c.replayGain = c.replayGain.copy(gainParDefautDb = -6f)
        val rg = c.traitementReplayGain
        rg.configure(stereo16)
        flush(rg, "jamais vue")

        val sortie = lireFlottants(traiter(rg, seize(16_384)))

        assertTrue(sortie.contentToString(), abs(sortie[0] - 0.5f * 0.501f) < 1e-3f)
    }

    /** A4 : un réglage changé vaut au tampon suivant, sans attendre de `flush`. */
    @Test
    fun `un reglage change s'entend au tampon suivant`() {
        val c = chaine()
        val rg = c.traitementReplayGain
        rg.configure(stereo16)
        c.gains.retenir("piste", GainDePiste(gainDb = -6.0))
        flush(rg, "piste")

        val avant = lireFlottants(traiter(rg, seize(16_384)))
        c.replayGain = c.replayGain.copy(preampliDb = 6f)
        val apres = lireFlottants(traiter(rg, seize(16_384)))

        assertTrue(avant.contentToString(), avant[0] < 0.3f)
        assertArrayEquals(floatArrayOf(0.5f), apres, 0f)
    }

    /** G7 : l'écoute aussi se relit à chaque tampon. */
    @Test
    fun `l'ecoute d'un disque change le gain sans attendre la piste suivante`() {
        val c = chaine()
        c.replayGain = c.replayGain.copy(mode = ModeDeGain.Automatique)
        val rg = c.traitementReplayGain
        rg.configure(stereo16)
        c.gains.retenir("piste", GainDePiste(gainDb = 0.0, gainAlbumDb = -6.0))
        flush(rg, "piste")

        val titre = lireFlottants(traiter(rg, seize(16_384)))
        c.ecoute = Ecoute.UnDisque
        val disque = lireFlottants(traiter(rg, seize(16_384)))

        assertArrayEquals(floatArrayOf(0.5f), titre, 0f)
        assertTrue(disque.contentToString(), abs(disque[0] - 0.5f * 0.501f) < 1e-3f)
    }

    @Test
    fun `le ReplayGain refuse ce qu'il ne sait pas lire`() {
        val rg = chaine().traitementReplayGain
        val refuse = runCatching { rg.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_24BIT)) }
        assertTrue(refuse.exceptionOrNull() is AudioProcessor.UnhandledAudioFormatException)
    }

    // ---------------------------------------------------------------------
    // L'égaliseur
    // ---------------------------------------------------------------------

    /** C2 : du flottant en entrée, du 16 bits en sortie, arrondi et borné. */
    @Test
    fun `l'egaliseur rend du 16 bits borne a pleine echelle`() {
        val eq = chaine().traitementEgaliseur
        assertEquals(
            C.ENCODING_PCM_16BIT,
            eq.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT)).encoding,
        )
        flush(eq, null)

        val sortie = lireSeize(traiter(eq, flottants(0.5f, -1f, 1.5f, -2f)))

        assertArrayEquals(shortArrayOf(16_384, -32_768, 32_767, -32_768), sortie)
    }

    /**
     * C1 et C2 : les deux traitements à la suite, le ReplayGain inactif et
     * l'égaliseur inactif, rendent les octets reçus. Ce qui ne joue pas ne
     * s'entend pas.
     */
    @Test
    fun `la chaine inactive rend le signal a l'identique`() {
        val c = chaine()
        c.replayGain = c.replayGain.copy(actif = false)
        val rg = c.traitementReplayGain
        val eq = c.traitementEgaliseur
        eq.configure(rg.configure(stereo16))
        flush(rg, null)
        flush(eq, null)
        val entree = ShortArray(512) { (sin(it * 0.3) * 30_000).toInt().toShort() }

        val sortie = lireSeize(traiter(eq, traiter(rg, seize(*entree))))

        assertArrayEquals(entree, sortie)
    }

    /**
     * E5 : un `flush` qui garde le format — une nouvelle piste, un saut — ne vide
     * pas la mémoire des filtres. Deux tampons séparés par lui sonnent comme un
     * seul tampon continu.
     */
    @Test
    fun `la memoire des filtres survit a un changement de piste`() {
        val reglages = ReglagesEgaliseur.PAR_DEFAUT.avecActif(true).avecBande(0, 9f).avecBande(4, -6f)
        val entree = FloatArray(400) { sin(it * 0.05).toFloat() * 0.4f }

        val continu = Egaliseur().run {
            val t = entree.copyOf()
            traiter(t, 200, 2, 44_100f, reglages)
            t.map(::versSeizeBits).toShortArray()
        }

        val c = chaine().apply { egaliseur = reglages }
        val eq = c.traitementEgaliseur
        eq.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
        flush(eq, "piste A")
        val premier = lireSeize(traiter(eq, flottants(*entree.copyOfRange(0, 200))))
        flush(eq, "piste B")
        val second = lireSeize(traiter(eq, flottants(*entree.copyOfRange(200, 400))))

        assertArrayEquals(continu, premier + second)
    }

    /**
     * E5 : un changement de format, lui, oublie la mémoire. Un réglage qui ne
     * fait que couper, pour que la marge (1 aux deux fréquences) ne se mêle pas
     * de la comparaison.
     */
    @Test
    fun `un changement de format oublie la memoire des filtres`() {
        val reglages = ReglagesEgaliseur.PAR_DEFAUT.avecActif(true).avecBande(0, -9f)
        val entree = FloatArray(200) { sin(it * 0.05).toFloat() * 0.4f }

        val neuf = Egaliseur().run {
            val t = entree.copyOf()
            traiter(t, 100, 2, 48_000f, reglages)
            t.map(::versSeizeBits).toShortArray()
        }

        val c = chaine().apply { egaliseur = reglages }
        val eq = c.traitementEgaliseur
        eq.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
        flush(eq, "piste A")
        traiter(eq, flottants(*entree))
        eq.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        flush(eq, "piste B")
        val apres = lireSeize(traiter(eq, flottants(*entree)))

        assertArrayEquals(neuf, apres)
    }

    /** E2, sur la vraie chaîne de traitement : actif et plat, rien ne change. */
    @Test
    fun `l'egaliseur actif et plat rend le signal a l'identique`() {
        val c = chaine().apply {
            replayGain = replayGain.copy(actif = false)
            egaliseur = ReglagesEgaliseur.PAR_DEFAUT.avecActif(true)
        }
        val rg = c.traitementReplayGain
        val eq = c.traitementEgaliseur
        eq.configure(rg.configure(stereo16))
        flush(rg, null)
        flush(eq, null)
        val entree = ShortArray(512) { (sin(it * 0.3) * 30_000).toInt().toShort() }

        val sortie = lireSeize(traiter(eq, traiter(rg, seize(*entree))))

        assertArrayEquals(entree, sortie)
    }
}
