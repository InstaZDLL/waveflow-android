package app.waveflow.playback.audio

import app.waveflow.model.Prereglage
import app.waveflow.model.ReglagesEgaliseur
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/**
 * L'égaliseur, avec les vecteurs des tests du bureau
 * (`src-tauri/crates/app/src/audio/eq/tests.rs`). Règles E1 à E5 de
 * `docs/audio-avance.md`.
 */
class EgaliseurTest {

    private val frequence = 44_100f

    private fun chaine(gains: List<Float>): FloatArray {
        val coefficients = FloatArray(Egaliseur.NOMBRE_DE_BANDES * 5)
        ReglagesEgaliseur.FREQUENCES_HZ.forEachIndexed { i, f ->
            Egaliseur.cloche(f, frequence, gains[i], Egaliseur.Q).copyInto(coefficients, i * 5)
        }
        return coefficients
    }

    private fun db(lineaire: Float): Float = 20f * log10(lineaire)

    private fun reglage(vararg bandes: Pair<Int, Float>): ReglagesEgaliseur {
        var r = ReglagesEgaliseur.PAR_DEFAUT.avecActif(true)
        bandes.forEach { (i, g) -> r = r.avecBande(i, g) }
        return r
    }

    @Test
    fun `la marge s'atteint au fil du tampon`() {
        val tampon = FloatArray(8) { 1f } // 4 trames stéréo
        Egaliseur.appliquerPreampli(tampon, trames = 4, canaux = 2, debut = 1f, cible = 0.5f)
        assertArrayEquals(floatArrayOf(0.875f, 0.875f, 0.75f, 0.75f, 0.625f, 0.625f, 0.5f, 0.5f), tampon, 0f)

        val stable = floatArrayOf(0.3f, -0.7f, 0.1f, 0.9f)
        Egaliseur.appliquerPreampli(stable, trames = 2, canaux = 2, debut = 1f, cible = 1f)
        assertArrayEquals(floatArrayOf(0.3f, -0.7f, 0.1f, 0.9f), stable, 0f)
    }

    /** Plus d'atténuation tout de suite, moins d'atténuation au tampon suivant (E4). */
    @Test
    fun `la marge baisse tout de suite et remonte en un tampon`() {
        val egaliseur = Egaliseur()
        egaliseur.traiter(FloatArray(64), 64, 1, frequence, reglage(3 to 6f))
        val amplifie = egaliseur.preampli
        assertTrue("$amplifie", amplifie < 0.5f)

        val tampon = FloatArray(4) { 1f }
        egaliseur.traiter(tampon, 4, 1, frequence, reglage())
        // De nouveau plat : le gain remonte vers 1 au fil du tampon, d'où il
        // était, au lieu de sauter.
        assertTrue(tampon.contentToString(), tampon[0] < 0.9f && tampon[0] > amplifie)
        assertTrue(tampon.contentToString(), abs(tampon[3] - 1f) < 1e-6f)
        assertEquals(1f, egaliseur.preampli)
    }

    @Test
    fun `un reglage plat ou qui coupe laisse le niveau tranquille`() {
        assertEquals(1f, Egaliseur.preampliDeMarge(chaine(List(6) { 0f }), frequence))
        assertEquals(1f, Egaliseur.preampliDeMarge(chaine(Prereglage.ReductionDesGraves.bandesDb), frequence))
    }

    @Test
    fun `une amplification isolee est compensee par la marge`() {
        val gains = MutableList(6) { 0f }.apply { this[3] = 6f }
        // L'amplification, plus le dB des transitoires.
        val preampli = db(Egaliseur.preampliDeMarge(chaine(gains), frequence))
        assertTrue("$preampli dB", abs(preampli + 7f) < 0.1f)
    }

    @Test
    fun `des amplifications voisines demandent plus que le plus grand curseur`() {
        // +6, +5 et +3 dB sur les trois bandes basses, qui se recouvrent.
        val preampli = db(Egaliseur.preampliDeMarge(chaine(Prereglage.AmplificationDesGraves.bandesDb), frequence))
        assertTrue("$preampli dB", preampli < -7.5f)
    }

    /** Le pire transitoire mesuré par le bureau : +12 dB à 15 kHz sous un motif net. */
    @Test
    fun `un motif net a pleine echelle sous les aigus amplifies reste sous l'unite`() {
        val egaliseur = Egaliseur()
        val motif = floatArrayOf(1f, -1f, -1f, 1f, -1f, -1f, 1f)
        val tampon = FloatArray(motif.size * 3) { motif[it % motif.size] }
        egaliseur.traiter(tampon, tampon.size, 1, frequence, reglage(5 to ReglagesEgaliseur.GAIN_MAX_DB))
        val pic = tampon.maxOf { abs(it) }
        assertTrue("pic $pic", pic <= 1f)
    }

    @Test
    fun `une sinusoide a pleine echelle sous l'amplification maximale reste sous l'unite`() {
        val egaliseur = Egaliseur()
        // Une seconde d'un 60 Hz à pleine échelle, en mono.
        val n = frequence.toInt()
        val tampon = FloatArray(n) { sin(2.0 * PI * 60.0 * it / frequence).toFloat() }
        egaliseur.traiter(tampon, n, 1, frequence, reglage(0 to ReglagesEgaliseur.GAIN_MAX_DB))
        // Une fois le filtre établi, la sortie culmine juste sous la pleine
        // échelle (le dB de marge), au lieu de 12 dB au-dessus.
        val pic = tampon.copyOfRange(n / 2, n).maxOf { abs(it) }
        assertTrue("pic $pic", pic <= 0.9f && pic > 0.85f)
    }

    // ---------------------------------------------------------------------
    // Ce que le bureau n'a pas à prouver
    // ---------------------------------------------------------------------

    /** E2 : actif et plat, le tampon ressort identique, au bit près. */
    @Test
    fun `actif et plat, le tampon ressort identique`() {
        val egaliseur = Egaliseur()
        val entree = FloatArray(512) { sin(it * 0.37).toFloat() * 0.8f }
        val tampon = entree.copyOf()
        egaliseur.traiter(tampon, 256, 2, frequence, reglage())
        assertArrayEquals(entree, tampon, 0f)
    }

    /** E2 : inactif, rien n'est touché, même un réglage qui amplifie. */
    @Test
    fun `inactif, le tampon passe tel quel`() {
        val egaliseur = Egaliseur()
        val entree = FloatArray(64) { if (it % 2 == 0) 0.9f else -0.9f }
        val tampon = entree.copyOf()
        egaliseur.traiter(tampon, 64, 1, frequence, reglage(0 to 12f).avecActif(false))
        assertArrayEquals(entree, tampon, 0f)
    }

    /**
     * L'écart assumé de E1 : à 22,05 kHz, la cloche de 15 kHz est au-delà de
     * Nyquist. Calculée telle quelle, elle diverge ; neutralisée, la sortie
     * reste bornée.
     */
    @Test
    fun `une bande au-dela de Nyquist est neutre et ne diverge pas`() {
        val basse = 22_050f
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 0f), Egaliseur.cloche(15_000f, basse, 12f, Egaliseur.Q), 0f)

        val egaliseur = Egaliseur()
        val tampon = FloatArray(basse.toInt()) { sin(2.0 * PI * 1_000.0 * it / basse).toFloat() * 0.5f }
        egaliseur.traiter(tampon, tampon.size, 1, basse, reglage(5 to 12f))
        assertTrue(tampon.all { it.isFinite() && abs(it) <= 1f })
    }

    /** E5 : la mémoire des filtres continue d'un tampon à l'autre. */
    @Test
    fun `deux tampons a la suite sonnent comme un seul`() {
        val reglages = reglage(0 to 6f, 3 to -4f)
        val entree = FloatArray(400) { sin(it * 0.05).toFloat() * 0.5f }

        val dUnBloc = entree.copyOf()
        Egaliseur().apply { traiter(dUnBloc, 400, 1, frequence, reglages) }

        val enDeux = Egaliseur()
        val premier = entree.copyOfRange(0, 200)
        val second = entree.copyOfRange(200, 400)
        enDeux.traiter(premier, 200, 1, frequence, reglages)
        enDeux.traiter(second, 200, 1, frequence, reglages)

        assertArrayEquals(dUnBloc, premier + second, 0f)
    }
}
