package app.waveflow.playback.audio

import app.waveflow.model.ModeDeGain
import app.waveflow.model.ReglagesReplayGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

/**
 * Le calcul du gain, avec les vecteurs des tests du bureau
 * (`waveflow-core`, `playback/replay_gain/tests.rs`) : même entrée, même sortie.
 * Règles G5 et G6 de `docs/audio-avance.md`.
 *
 * Les tests du bureau sur le pic « non vérifié » et sur `prefer_tag` ne sont pas
 * portés : Android n'a pas d'analyse (G5).
 */
class ReplayGainTest {

    /** Tout ce qui suit décrit un titre écouté seul ; les cas d'album le disent. */
    private fun commeTitreDb(piste: GainDePiste, reglages: ReglagesReplayGain): Double =
        ReplayGain.gainEffectifDb(piste, reglages, Ecoute.UnTitre)

    private fun commeTitre(piste: GainDePiste, reglages: ReglagesReplayGain): Float =
        ReplayGain.lineaire(piste, reglages, Ecoute.UnTitre)

    /**
     * Le `on()` du bureau : actif, et le reste à ses défauts. Les défauts du
     * bureau sont ceux d'Android, hormis l'interrupteur (A2).
     */
    private val actif = ReglagesReplayGain(actif = true)

    private fun proche(obtenu: Double, attendu: Double) = abs(obtenu - attendu) < 1e-9

    @Test
    fun `un ReplayGain inactif n'applique rien du tout`() {
        val piste = GainDePiste(gainDb = -9.0, pic = 0.5)
        val reglages = ReglagesReplayGain(
            actif = false,
            preampliDb = 6f,
            gainParDefautDb = -3f,
            eviterSaturation = true,
            mode = ModeDeGain.Automatique,
        )
        assertTrue(proche(commeTitreDb(piste, reglages), 0.0))
        assertEquals(1f, commeTitre(piste, reglages))
    }

    @Test
    fun `six decibels doublent l'amplitude`() {
        val reglages = actif.copy(eviterSaturation = false)
        val haut = commeTitre(GainDePiste(gainDb = 6.0), reglages)
        val bas = commeTitre(GainDePiste(gainDb = -6.0), reglages)
        assertTrue("+6 dB a donné $haut", abs(haut - 1.995f) < 0.01f)
        assertTrue("-6 dB a donné $bas", abs(bas - 0.501f) < 0.01f)
        assertEquals(1f, commeTitre(GainDePiste(gainDb = 0.0), reglages))
    }

    @Test
    fun `la pre-amplification s'ajoute au gain du titre`() {
        val gain = commeTitreDb(GainDePiste(gainDb = -8.0), actif.copy(preampliDb = 3f))
        assertTrue("$gain", proche(gain, -5.0))
    }

    /** Le cœur de l'affaire : l'amplification est retenue à pleine échelle. */
    @Test
    fun `eviter la saturation plafonne le gain a la marge disponible`() {
        // Un pic de 0,5 laisse 6,02 dB de marge.
        val piste = GainDePiste(gainDb = 12.0, pic = 0.5)
        val plafonne = commeTitreDb(piste, actif)
        assertTrue("attendu 6,02 dB, obtenu $plafonne", abs(plafonne - 6.0206) < 1e-3)

        // Et l'échantillon le plus fort atteint l'unité, sans la dépasser.
        val facteur = commeTitre(piste, actif)
        assertTrue("0,5 × $facteur devrait atteindre la pleine échelle", abs(facteur.toDouble() * 0.5 - 1.0) < 1e-3)
    }

    @Test
    fun `eviter la saturation se coupe`() {
        val gain = commeTitreDb(GainDePiste(gainDb = 9.0, pic = 0.5), actif.copy(eviterSaturation = false))
        assertTrue("$gain", proche(gain, 9.0))
    }

    @Test
    fun `le plafond ne releve jamais un gain negatif`() {
        val gain = commeTitreDb(GainDePiste(gainDb = -6.0, pic = 0.1), actif)
        assertTrue("$gain", proche(gain, -6.0))
    }

    @Test
    fun `un master deja sature est baisse`() {
        val gain = commeTitreDb(GainDePiste(gainDb = 0.0, pic = 1.25), actif)
        assertTrue("attendu environ -1,94 dB, obtenu $gain", gain < -1.9 && gain > -2.0)
    }

    @Test
    fun `une piste dont on ne sait rien prend le gain par defaut`() {
        val gain = commeTitreDb(GainDePiste(), actif.copy(gainParDefautDb = -4f))
        assertTrue("$gain", proche(gain, -4.0))
    }

    @Test
    fun `le gain total est borne dans les deux sens`() {
        assertTrue(proche(commeTitreDb(GainDePiste(gainDb = 40.0), actif), ReplayGain.GAIN_TOTAL_MAX_DB))
        assertTrue(proche(commeTitreDb(GainDePiste(gainDb = -90.0), actif), ReplayGain.GAIN_TOTAL_MIN_DB))
    }

    /** Le plancher ne défait pas le plafond : borner d'abord, plafonner ensuite. */
    @Test
    fun `le plancher ne remonte jamais un gain au-dessus de sa marge`() {
        for (pic in listOf(31.62, 100.0, 1_000.0)) {
            val piste = GainDePiste(gainDb = 0.0, pic = pic)
            val marge = -20.0 * log10(pic)
            val gain = commeTitreDb(piste, actif)
            assertTrue("pic $pic : marge $marge dB, gain $gain", gain <= marge + 1e-9)
            val atteint = commeTitre(piste, actif).toDouble() * pic
            assertTrue("pic $pic ramené à $atteint", atteint <= 1.0 + 1e-6)
        }
    }

    @Test
    fun `un gain non fini prend le gain par defaut au lieu de se propager`() {
        val reglages = actif.copy(gainParDefautDb = -5f)
        for (bogue in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val gain = commeTitreDb(GainDePiste(gainDb = bogue), reglages)
            assertTrue("$bogue aurait dû prendre le gain par défaut, obtenu $gain", proche(gain, -5.0))
        }
    }

    @Test
    fun `un pic absurde est ignore plutot que cru`() {
        for (pic in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val piste = GainDePiste(gainDb = 3.0, pic = pic)
            assertTrue("le pic $pic aurait dû être ignoré", proche(commeTitreDb(piste, actif), 3.0))
            assertTrue(commeTitre(piste, actif).isFinite())
        }
    }

    // ---------------------------------------------------------------------
    // Le mode album
    // ---------------------------------------------------------------------

    /** Un disque plus calme que la moyenne, dont une piste culmine bien plus haut que celle-ci. */
    private val disque = GainDePiste(gainDb = -3.0, pic = 0.4, gainAlbumDb = -7.0, picAlbum = 0.98)

    private fun enMode(mode: ModeDeGain) = actif.copy(mode = mode)

    @Test
    fun `le mode choisit lequel des deux gains s'applique`() {
        val piste = disque.copy(pic = null, picAlbum = null)
        assertTrue(proche(ReplayGain.gainEffectifDb(piste, enMode(ModeDeGain.ParTitre), Ecoute.UnDisque), -3.0))
        assertTrue(proche(ReplayGain.gainEffectifDb(piste, enMode(ModeDeGain.ParAlbum), Ecoute.UnTitre), -7.0))
    }

    @Test
    fun `l'automatique suit l'ecoute`() {
        val piste = disque.copy(pic = null, picAlbum = null)
        val auto = enMode(ModeDeGain.Automatique)
        assertTrue(proche(ReplayGain.gainEffectifDb(piste, auto, Ecoute.UnDisque), -7.0))
        assertTrue(proche(ReplayGain.gainEffectifDb(piste, auto, Ecoute.UnTitre), -3.0))
    }

    /** Le plafond d'album est le même pour tout le disque, et le disque reste égal. */
    @Test
    fun `le mode album plafonne par le pic d'album et non par celui du titre`() {
        val fort = actif.copy(mode = ModeDeGain.ParAlbum, preampliDb = 12f)
        val gain = ReplayGain.gainEffectifDb(disque, fort, Ecoute.UnDisque)

        // 0,98 laisse 0,175 dB ; le pic de 0,4 du titre en aurait laissé près de 8.
        val margeAlbum = -20.0 * log10(0.98)
        assertTrue("attendu $margeAlbum, obtenu $gain", proche(gain, margeAlbum))

        val titreCalme = disque.copy(pic = 0.05)
        assertTrue(proche(ReplayGain.gainEffectifDb(titreCalme, fort, Ecoute.UnDisque), gain))
    }

    @Test
    fun `un fichier sans gain d'album retombe sur le sien`() {
        val sansAlbum = disque.copy(gainAlbumDb = null, picAlbum = null)
        assertTrue(proche(ReplayGain.gainEffectifDb(sansAlbum, enMode(ModeDeGain.ParAlbum), Ecoute.UnDisque), -3.0))
    }

    @Test
    fun `un gain d'album sans pic d'album garde la protection contre la saturation`() {
        val sansPicAlbum = disque.copy(picAlbum = null, pic = 0.5)
        // Assez de pré-amplification pour que le plafond décide : -7 + 15 = 8 dB,
        // au-dessus des 6,02 dB qu'un pic de 0,5 laisse.
        val reglages = actif.copy(mode = ModeDeGain.ParAlbum, preampliDb = 15f)
        val gain = ReplayGain.gainEffectifDb(sansPicAlbum, reglages, Ecoute.UnDisque)
        val marge = -20.0 * log10(0.5)
        assertTrue("attendu $marge, obtenu $gain", proche(gain, marge))
        assertTrue(gain < -7.0 + 15.0)
    }

    /** Un gain d'album non fini compte pour absent : le titre, pas le gain par défaut. */
    @Test
    fun `un gain d'album non fini retombe sur le gain du titre`() {
        val casse = disque.copy(gainAlbumDb = Double.NaN, pic = null, picAlbum = null)
        val reglages = enMode(ModeDeGain.ParAlbum).copy(gainParDefautDb = -10f)
        assertTrue(proche(ReplayGain.gainEffectifDb(casse, reglages, Ecoute.UnDisque), -3.0))
    }

    @Test
    fun `le mode ne fait rien quand le ReplayGain est inactif`() {
        val reglages = ReglagesReplayGain(actif = false, mode = ModeDeGain.ParAlbum)
        assertTrue(proche(ReplayGain.gainEffectifDb(disque, reglages, Ecoute.UnDisque), 0.0))
    }

    // ---------------------------------------------------------------------
    // Le serveur d'abord, les tags ensuite (G2)
    // ---------------------------------------------------------------------

    @Test
    fun `le serveur l'emporte champ par champ, les tags comblent le reste`() {
        val serveur = GainDePiste(gainDb = -7.0, gainAlbumDb = -5.0)
        val tags = GainDePiste(gainDb = -3.0, pic = 0.9, gainAlbumDb = -2.0, picAlbum = 0.95)
        val fusion = serveur.ouSinon(tags)
        assertEquals(GainDePiste(gainDb = -7.0, pic = 0.9, gainAlbumDb = -5.0, picAlbum = 0.95), fusion)
    }

    // ---------------------------------------------------------------------
    // L'écoute d'un disque (G7)
    // ---------------------------------------------------------------------

    @Test
    fun `une file d'un seul album, sans aleatoire, est un disque`() {
        assertEquals(Ecoute.UnDisque, ecouteDeLaFile(listOf("local:7", "local:7", "local:7"), aleatoire = false))
    }

    /** L'aléatoire d'Android mélange les titres : c'est le `ShuffleMode::Tracks` du bureau. */
    @Test
    fun `le meme album melange n'est plus un disque`() {
        assertEquals(Ecoute.UnTitre, ecouteDeLaFile(listOf("local:7", "local:7"), aleatoire = true))
    }

    @Test
    fun `un titre lance seul n'est pas un disque`() {
        assertEquals(Ecoute.UnTitre, ecouteDeLaFile(listOf("remote:a"), aleatoire = false))
    }

    @Test
    fun `une file de plusieurs albums n'est pas un disque`() {
        assertEquals(Ecoute.UnTitre, ecouteDeLaFile(listOf("local:7", "local:8"), aleatoire = false))
    }

    @Test
    fun `une file dont on ignore l'album n'est pas un disque`() {
        assertEquals(Ecoute.UnTitre, ecouteDeLaFile(listOf(null, null), aleatoire = false))
        assertEquals(Ecoute.UnTitre, ecouteDeLaFile(listOf("local:7", null), aleatoire = false))
    }
}
