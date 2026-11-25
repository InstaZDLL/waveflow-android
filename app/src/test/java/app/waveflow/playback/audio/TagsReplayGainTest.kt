package app.waveflow.playback.audio

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * La lecture des tags, avec les vecteurs des tests du scanner du bureau
 * (`waveflow-core`, `scanner/replay_gain/tests.rs`). Règles G1 et G3 de
 * `docs/audio-avance.md`.
 */
class TagsReplayGainTest {

    private fun avec(vararg paires: Pair<String, String>) = TagsReplayGain.deValeurs(mapOf(*paires))

    @Test
    fun `un gain textuel se lit avec ou sans son unite`() {
        for ((brut, attendu) in listOf(
            "-7.89 dB" to -7.89,
            "-7.89dB" to -7.89,
            "-7.89" to -7.89,
            "+3.20 dB" to 3.20,
            "  0.00 DB  " to 0.0,
        )) {
            val lu = avec("REPLAYGAIN_TRACK_GAIN" to brut).gainDb ?: error("$brut n'a rien donné")
            assertTrue("$brut a donné $lu", abs(lu - attendu) < 1e-9)
        }
    }

    @Test
    fun `un pic est lineaire et un pic nul est refuse`() {
        assertEquals(0.987654, avec("REPLAYGAIN_TRACK_PEAK" to "0.987654").pic)
        // Les masters saturés dépassent vraiment l'unité.
        assertEquals(1.0234, avec("REPLAYGAIN_TRACK_PEAK" to "1.0234").pic)
        for (bogue in listOf("0", "0.0", "-0.5", "not a number", "120.0")) {
            assertNull("$bogue ne devrait pas se lire", avec("REPLAYGAIN_TRACK_PEAK" to bogue).pic)
        }
    }

    @Test
    fun `un gain R128 arrive sur l'echelle ReplayGain`() {
        // 0 LU sous -23 LUFS, c'est -18 + 5 : +5 dB ici.
        assertEquals(5.0, avec("R128_TRACK_GAIN" to "0").gainDb)
        // -1536/256 = -6 LU, donc -6 + 5 = -1 dB.
        assertEquals(-1.0, avec("R128_TRACK_GAIN" to "-1536").gainDb)
    }

    @Test
    fun `le gain textuel l'emporte sur R128`() {
        assertEquals(-4.0, avec("REPLAYGAIN_TRACK_GAIN" to "-4.00 dB", "R128_TRACK_GAIN" to "0").gainDb)
    }

    @Test
    fun `les valeurs d'album se lisent independamment de celles du titre`() {
        val lu = avec("REPLAYGAIN_ALBUM_GAIN" to "-6.50 dB", "REPLAYGAIN_ALBUM_PEAK" to "0.995")
        assertEquals(-6.5, lu.gainAlbumDb)
        assertEquals(0.995, lu.picAlbum)
        assertNull(lu.gainDb)
    }

    @Test
    fun `un fichier sans tag ReplayGain se lit vide`() {
        assertEquals(GainDePiste(), avec())
        assertEquals(GainDePiste(), TagsReplayGain.lire(null))
    }

    /** Un gain hors de toute vraisemblance est un tag cassé : le confier au mixeur abîmerait des enceintes. */
    @Test
    fun `un gain invraisemblable est ecarte`() {
        for (bogue in listOf("999 dB", "-500", "inf", "NaN", "")) {
            assertNull("$bogue ne devrait pas se lire", avec("REPLAYGAIN_TRACK_GAIN" to bogue).gainDb)
        }
    }

    /** Ce que le Kotlin lirait et que le bureau refuse. */
    @Test
    fun `l'hexadecimal et le suffixe de type ne passent pas`() {
        for (bogue in listOf("0x1p3", "2.5d", "2.5f")) {
            assertNull("$bogue ne devrait pas se lire", avec("REPLAYGAIN_TRACK_GAIN" to bogue).gainDb)
        }
    }

    // ---------------------------------------------------------------------
    // Ce que l'extracteur de Media3 range dans le Format (G1)
    // ---------------------------------------------------------------------

    @Test
    fun `un cadre TXXX d'un MP3 porte le gain`() {
        val lu = TagsReplayGain.lire(
            Metadata(
                TextInformationFrame("TXXX", "replaygain_track_gain", ImmutableList.of("-6.50 dB")),
                TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_PEAK", ImmutableList.of("0.9")),
                // Un cadre de texte ordinaire, sans description : ignoré.
                TextInformationFrame("TIT2", null, ImmutableList.of("Titre")),
            ),
        )
        assertEquals(GainDePiste(gainDb = -6.5, pic = 0.9), lu)
    }

    @Test
    fun `un commentaire Vorbis d'un FLAC ou d'un Opus porte le gain`() {
        val lu = TagsReplayGain.lire(
            Metadata(
                VorbisComment("REPLAYGAIN_ALBUM_GAIN", "-8.00 dB"),
                VorbisComment("R128_TRACK_GAIN", "-1536"),
            ),
        )
        assertEquals(GainDePiste(gainDb = -1.0, gainAlbumDb = -8.0), lu)
    }

    @Test
    fun `un atome iTunes d'un MP4 porte le gain`() {
        val lu = TagsReplayGain.lire(
            Metadata(InternalFrame("com.apple.iTunes", "replaygain_track_gain", "-2.10 dB")),
        )
        assertEquals(GainDePiste(gainDb = -2.1), lu)
    }
}
