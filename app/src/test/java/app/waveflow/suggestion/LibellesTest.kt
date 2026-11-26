package app.waveflow.suggestion

import app.waveflow.suggestion.SorteDeSuggestion.DansLEsprit
import app.waveflow.suggestion.SorteDeSuggestion.JamaisEcoutee
import app.waveflow.suggestion.SorteDeSuggestion.Oubliee
import app.waveflow.testing.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneOffset

/**
 * Les mots de la bande (S15) : ceux du bureau en français, et la tournure
 * choisie comme le fait `useHomeSuggestion`.
 *
 * Les tournures attendues sont calculées à la main sur la formule du bureau,
 * `((((j + e) × 7 + id × 13 + sel) % n) + n) % n + 1`.
 */
@RunWith(RobolectricTestRunner::class)
class LibellesTest {

    private val le26Novembre = Instant.parse("2026-11-26T12:00:00Z").toEpochMilli()

    @Test
    fun `la tournure suit la formule du bureau, positive pour un identifiant negatif`() {
        // 140 000 + 13 = 140 013, multiple de 3.
        assertEquals(1, tournure(20_000, 0, songId = 1, sel = 0, nombre = 3))
        // 140 000 − 13 = 139 987, reste 1.
        assertEquals(2, tournure(20_000, 0, songId = -1, sel = 0, nombre = 3))
        // −700 000 + 13 + 1 = −699 986 : en JavaScript −2 % 3 vaut −2, ramené à 1.
        assertEquals(2, tournure(-100_000, 0, songId = 1, sel = 1, nombre = 3))
    }

    @Test
    fun `la sorte change de tournure avec les appuis`() {
        val s = Suggestion(Oubliee, song(1), Raison(ecoutes = 3))
        assertEquals("Vous l'adoriez", libelleDeSorte(s, 20_000, 0))
        // 140 007 + 13 = 140 020, reste 1.
        assertEquals("Un favori un peu oublié", libelleDeSorte(s, 20_000, 1))
    }

    @Test
    fun `une oubliee dit ses ecoutes et le mois de la derniere`() {
        fun raison(id: Long, ecoutes: Int) = raisonDe(
            Suggestion(Oubliee, song(id), Raison(ecoutes = ecoutes, derniereEcouteMs = le26Novembre)),
            jour = 20_000,
            essais = 0,
            fuseau = ZoneOffset.UTC,
        )

        assertEquals("4 écoutes · dernière écoute en novembre 2026", raison(1, 4))
        assertEquals("1 écoute · dernière écoute en novembre 2026", raison(1, 1))
        assertEquals("Écouté 4 fois, la dernière en novembre 2026", raison(2, 4))
    }

    @Test
    fun `dans l'esprit nomme l'artiste recent`() {
        fun raison(id: Long) = raisonDe(
            Suggestion(DansLEsprit, song(id), Raison(artisteAncre = "Nina Simone")),
            jour = 20_000,
            essais = 0,
            fuseau = ZoneOffset.UTC,
        )

        assertEquals("Dans l'esprit de Nina Simone", raison(1))
        assertEquals("Parce que vous écoutez Nina Simone en ce moment", raison(2))
    }

    @Test
    fun `une jamais ecoutee nomme l'artiste ecoute, sinon la date d'ajout, sinon rien`() {
        fun raison(artiste: String?, ecoutesArtiste: Long, ajouteeMs: Long) = raisonDe(
            Suggestion(
                JamaisEcoutee,
                song(1, artist = artiste),
                Raison(ecoutesArtiste = ecoutesArtiste, ajouteeMs = ajouteeMs),
            ),
            jour = 20_000,
            essais = 0,
            fuseau = ZoneOffset.UTC,
        )

        assertEquals("De Nina Simone, que vous écoutez souvent", raison("Nina Simone", 5, le26Novembre))
        // Un artiste jamais écouté : la date d'ajout.
        assertEquals(
            "Dans votre bibliothèque depuis le 26 novembre 2026, toujours pas écouté",
            raison("Nina Simone", 0, le26Novembre),
        )
        // Un artiste inconnu ne se nomme pas, même écouté.
        assertEquals(
            "Dans votre bibliothèque depuis le 26 novembre 2026, toujours pas écouté",
            raison("<unknown>", 5, le26Novembre),
        )
        // Un fichier que le MediaStore ne date pas n'a pas de raison.
        assertNull(raison(null, 0, 0))
    }
}
