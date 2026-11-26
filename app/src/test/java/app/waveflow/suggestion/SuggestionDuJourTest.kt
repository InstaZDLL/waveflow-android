package app.waveflow.suggestion

import app.waveflow.data.PlayHistoryEntry
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.suggestion.SorteDeSuggestion.DansLEsprit
import app.waveflow.suggestion.SorteDeSuggestion.JamaisEcoutee
import app.waveflow.suggestion.SorteDeSuggestion.Oubliee
import app.waveflow.testing.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Les trois sortes de la suggestion du jour, sur l'historique et la bibliothèque
 * de l'appareil. Les règles citées sont celles de `docs/suggestion-du-jour.md`.
 *
 * Robolectric parce que les morceaux de test portent une `Uri`.
 */
@RunWith(RobolectricTestRunner::class)
class SuggestionDuJourTest {

    private fun bibliotheque(vararg pistes: Song) = Library(isLoading = false, songs = pistes.toList())

    /** Une ligne d'historique : [ecoutes] écoutes, la dernière il y a [joursAvant] jours. */
    private fun ecoute(id: Long, joursAvant: Long, ecoutes: Int = 1) =
        PlayHistoryEntry("local:$id", MAINTENANT - joursAvant * JOUR_MS, ecoutes)

    private fun liste(sorte: SorteDeSuggestion, lib: Library, historique: List<PlayHistoryEntry>) =
        courteListeDe(sorte, lib, historique, MAINTENANT)

    @Test
    fun `une oubliee a ete ecoutee au moins trois fois et pas depuis soixante jours`() {
        val lib = bibliotheque(song(1), song(2), song(3), song(4))
        val historique = listOf(
            ecoute(1, joursAvant = 61, ecoutes = 3),
            // Deux écoutes ne font pas un favori.
            ecoute(2, joursAvant = 200, ecoutes = 2),
            // Écoutée le mois dernier : pas encore oubliée.
            ecoute(3, joursAvant = 59, ecoutes = 10),
        )

        val oubliees = liste(Oubliee, lib, historique)

        assertEquals(listOf(1L), oubliees.map { it.song.id })
        assertEquals(Raison(ecoutes = 3, derniereEcouteMs = MAINTENANT - 61 * JOUR_MS), oubliees.single().raison)
    }

    @Test
    fun `les oubliees se classent par ecoutes puis par la plus ancienne, deux par artiste`() {
        val lib = bibliotheque(
            song(1, artistId = 7),
            song(2, artistId = 7),
            song(3, artistId = 7),
            song(4, artistId = 8),
            song(5, artistId = 9),
            song(6, artistId = 9),
        )
        val historique = listOf(
            ecoute(1, joursAvant = 100, ecoutes = 9),
            ecoute(2, joursAvant = 100, ecoutes = 8),
            // Le troisième de l'artiste 7 est le mieux classé après eux, mais
            // l'artiste a déjà ses deux places.
            ecoute(3, joursAvant = 100, ecoutes = 7),
            ecoute(4, joursAvant = 100, ecoutes = 5),
            // À égalité d'écoutes, la dernière écoute la plus ancienne d'abord,
            // puis l'identifiant.
            ecoute(6, joursAvant = 90, ecoutes = 5),
            ecoute(5, joursAvant = 300, ecoutes = 5),
        )

        assertEquals(listOf(1L, 2L, 5L, 4L, 6L), liste(Oubliee, lib, historique).map { it.song.id })
    }

    @Test
    fun `une piste qui n'est plus sur l'appareil ni une piste du serveur n'est suggeree`() {
        val lib = bibliotheque(song(1))
        val historique = listOf(
            PlayHistoryEntry("remote:1", MAINTENANT - 100 * JOUR_MS, 9),
            // Effacée de l'appareil depuis.
            ecoute(42, joursAvant = 100, ecoutes = 9),
        )

        assertTrue(liste(Oubliee, lib, historique).isEmpty())
        // La ligne du serveur ne rend pas « écoutée » la piste locale de même numéro.
        assertEquals(listOf(1L), liste(JamaisEcoutee, lib, historique).map { it.song.id })
    }

    @Test
    fun `les fichiers sans artiste ne se partagent pas deux places`() {
        // E6 : le MediaStore donne un identifiant même à `<unknown>`.
        val lib = bibliotheque(
            song(1, artist = "<unknown>", artistId = 99),
            song(2, artist = "<unknown>", artistId = 99),
            song(3, artist = null, artistId = 99),
        )
        val historique = (1L..3L).map { ecoute(it, joursAvant = 100, ecoutes = 5) }

        assertEquals(3, liste(Oubliee, lib, historique).size)
    }

    @Test
    fun `dans l'esprit vient des cinq artistes les plus ecoutes ces deux semaines`() {
        // Un artiste par dizaine : pistes 10, 11… de l'artiste 1 ; 20, 21… de l'artiste 2.
        val pistes = (1L..7L).flatMap { artiste ->
            (0L..2L).map { rang -> song(artiste * 10 + rang, artist = "Artiste $artiste", artistId = artiste) }
        }
        val lib = Library(isLoading = false, songs = pistes)
        val historique = buildList {
            // L'artiste 1 a deux pistes écoutées cette semaine, les artistes 2 à 7
            // une chacun : à égalité, les plus petits identifiants passent, et 6
            // et 7 restent dehors.
            add(ecoute(10, joursAvant = 1))
            add(ecoute(11, joursAvant = 2))
            for (artiste in 2L..7L) add(ecoute(artiste * 10, joursAvant = 3))
            // Il y a trois semaines : hors de la fenêtre, ne compte pas pour l'artiste 7.
            add(ecoute(71, joursAvant = 21))
        }

        val esprit = liste(DansLEsprit, lib, historique)

        // Les artistes 1 à 5 ; de chacun, les pistes pas entendues depuis trente
        // jours, par identifiant, deux au plus.
        assertEquals(listOf(12L, 21L, 22L, 31L, 32L, 41L, 42L, 51L, 52L), esprit.map { it.song.id })
        assertEquals("Artiste 2", esprit[1].raison.artisteAncre)
        assertTrue(esprit.all { it.sorte == DansLEsprit })
    }

    @Test
    fun `dans l'esprit reprend une piste entendue il y a plus de trente jours`() {
        val lib = bibliotheque(song(1, artistId = 5), song(2, artistId = 5), song(3, artistId = 5))
        val historique = listOf(
            ecoute(1, joursAvant = 2),
            ecoute(2, joursAvant = 29),
            ecoute(3, joursAvant = 31),
        )

        assertEquals(listOf(3L), liste(DansLEsprit, lib, historique).map { it.song.id })
    }

    @Test
    fun `une jamais ecoutee par artiste, la derniere ajoutee, les artistes ecoutes d'abord`() {
        val lib = bibliotheque(
            // L'artiste 1, écouté cinq fois : sa dernière ajoutée jamais lancée.
            song(1, artistId = 1, addedAtMs = 100),
            song(2, artistId = 1, addedAtMs = 300),
            song(3, artistId = 1, addedAtMs = 200),
            // L'artiste 2, jamais écouté, ajouté plus récemment.
            song(4, artistId = 2, addedAtMs = 900),
            // Deux fichiers sans artiste : chacun son groupe.
            song(5, artist = null, artistId = 99, addedAtMs = 500),
            song(6, artist = null, artistId = 99, addedAtMs = 400),
        )
        val historique = listOf(ecoute(1, joursAvant = 1, ecoutes = 5))

        val jamais = liste(JamaisEcoutee, lib, historique)

        assertEquals(listOf(2L, 4L, 5L, 6L), jamais.map { it.song.id })
        assertEquals(Raison(ecoutesArtiste = 5, ajouteeMs = 300), jamais.first().raison)
        assertEquals(Raison(ecoutesArtiste = 0, ajouteeMs = 900), jamais[1].raison)
    }

    @Test
    fun `le tirage prend la sorte du jour et le rang tire dans sa courte liste`() {
        val lib = bibliotheque(*(1L..30L).map { song(it, addedAtMs = it) }.toTypedArray())
        // Le jour 20 000 commence par Jamais écoutée (S1).
        val jour = 20_000L

        val tiree = suggestionDuJour(TirageDuJour(jour, 0, null), lib, emptyList(), MAINTENANT)

        val courte = liste(JamaisEcoutee, lib, emptyList())
        assertEquals(courte[rangTire(courte.size, jour, 0, JamaisEcoutee)], tiree)
    }

    @Test
    fun `une sorte vide passe a la suivante`() {
        // Le jour 20 001 commence par Oubliée, que rien ne remplit ; Dans l'esprit
        // non plus, sans écoute récente : c'est Jamais écoutée qui répond.
        val lib = bibliotheque(song(1))

        val tiree = suggestionDuJour(TirageDuJour(20_001, 0, null), lib, emptyList(), MAINTENANT)

        assertEquals(JamaisEcoutee, tiree?.sorte)
    }

    @Test
    fun `l'epinglee revient telle quelle meme une fois ecoutee`() {
        // S9 : écoutée aujourd'hui, elle n'est plus « jamais écoutée » ; elle
        // revient quand même, avec la raison de son tirage.
        val lib = bibliotheque(song(1), song(2))
        val epinglee = SuggestionEpinglee(JamaisEcoutee, 1, Raison(ecoutesArtiste = 0, ajouteeMs = 5))

        val tiree =
            suggestionDuJour(TirageDuJour(20_000, 0, epinglee), lib, listOf(ecoute(1, joursAvant = 0)), MAINTENANT)

        assertEquals(Suggestion(JamaisEcoutee, song(1), epinglee.raison), tiree)
    }

    @Test
    fun `une epinglee disparue de l'appareil fait tirer de nouveau`() {
        val lib = bibliotheque(song(2))
        val epinglee = SuggestionEpinglee(Oubliee, 1, Raison(ecoutes = 4))

        val tiree = suggestionDuJour(TirageDuJour(20_000, 0, epinglee), lib, emptyList(), MAINTENANT)

        assertEquals(2L, tiree?.song?.id)
        assertEquals(JamaisEcoutee, tiree?.sorte)
    }

    @Test
    fun `une bibliotheque vide n'a rien a suggerer`() {
        assertNull(
            suggestionDuJour(TirageDuJour(20_000, 0, null), bibliotheque(), listOf(ecoute(1, 100, 9)), MAINTENANT),
        )
    }

    private companion object {
        /** Le 15 janvier 2027 à 8 h UTC. */
        const val MAINTENANT = 1_800_000_000_000L
    }
}
