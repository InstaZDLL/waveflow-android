package app.waveflow.suggestion

import app.waveflow.suggestion.SorteDeSuggestion.DansLEsprit
import app.waveflow.suggestion.SorteDeSuggestion.JamaisEcoutee
import app.waveflow.suggestion.SorteDeSuggestion.Oubliee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'arithmétique du tirage, contre les vecteurs du bureau
 * (`home_suggestion/tests.rs`) : mêmes entrées, mêmes sorties.
 *
 * Le bureau n'y vérifie que des propriétés. Les valeurs exactes plus bas ont été
 * calculées en compilant ses `kind_order`, `pick_index` et `splitmix64` tels
 * quels : c'est ce qui prouve le « au bit près » de S3.
 */
class TirageTest {

    @Test
    fun `la sorte tourne d'un jour a l'autre et d'un appui a l'autre`() {
        // the_kind_rotates_by_day_and_by_click
        val a = ordreDesSortes(20_000, 0)
        val b = ordreDesSortes(20_001, 0)
        val c = ordreDesSortes(20_000, 1)
        assertTrue(a[0] != b[0])
        assertEquals(b[0], c[0])
        // Chaque sorte est essayée, une fois chacune.
        for (ordre in listOf(a, b, c)) {
            for (sorte in SorteDeSuggestion.entries) assertEquals(1, ordre.count { it == sorte })
        }
        // Trois appuis ramènent à la sorte du jour.
        assertEquals(a[0], ordreDesSortes(20_000, 3)[0])
    }

    @Test
    fun `l'ordre des sortes est celui du bureau, jours negatifs compris`() {
        assertEquals(listOf(JamaisEcoutee, Oubliee, DansLEsprit), ordreDesSortes(20_000, 0))
        assertEquals(listOf(Oubliee, DansLEsprit, JamaisEcoutee), ordreDesSortes(20_001, 0))
        assertEquals(listOf(Oubliee, DansLEsprit, JamaisEcoutee), ordreDesSortes(0, 0))
        // Le modulo euclidien de Rust : jamais négatif.
        assertEquals(listOf(JamaisEcoutee, Oubliee, DansLEsprit), ordreDesSortes(-1, 0))
        assertEquals(listOf(Oubliee, DansLEsprit, JamaisEcoutee), ordreDesSortes(-5, 2))
        assertEquals(listOf(DansLEsprit, JamaisEcoutee, Oubliee), ordreDesSortes(20_418, 7))
    }

    @Test
    fun `le tirage est stable et dans la liste`() {
        // the_draw_is_stable_and_in_range
        for (taille in 1 until 30) {
            val premier = rangTire(taille, 20_000, 2, DansLEsprit)
            assertTrue(premier < taille)
            assertEquals(premier, rangTire(taille, 20_000, 2, DansLEsprit))
        }
        assertEquals(0, rangTire(0, 20_000, 0, JamaisEcoutee))
        // Un autre appui est un autre tirage, pas toujours la même place.
        val tirages = (0 until 12).map { rangTire(20, 20_000, it, Oubliee) }.toSet()
        assertTrue(tirages.size > 3)
    }

    @Test
    fun `le tirage tombe au bit pres sur celui du bureau`() {
        // (taille, jour, appuis) → rangs pour Oubliée, Dans l'esprit, Jamais écoutée,
        // tels que `pick_index` les rend.
        val vecteurs = listOf(
            Triple(20, 20_000L, 0) to listOf(18, 3, 12),
            Triple(20, 20_000L, 1) to listOf(12, 9, 3),
            Triple(20, 20_000L, 2) to listOf(19, 3, 8),
            Triple(7, 20_418L, 0) to listOf(0, 3, 1),
            Triple(13, 20_418L, 5) to listOf(10, 4, 7),
            Triple(1, 20_000L, 0) to listOf(0, 0, 0),
            Triple(400, 20_418L, 3) to listOf(59, 86, 356),
            Triple(20, -3L, 0) to listOf(16, 1, 4),
        )
        for ((entree, attendus) in vecteurs) {
            val (taille, jour, essais) = entree
            val obtenus = SorteDeSuggestion.entries.map { rangTire(taille, jour, essais, it) }
            assertEquals("pick_index($taille, $jour, $essais)", attendus, obtenus)
        }
    }

    @Test
    fun `splitmix64 rend les valeurs du bureau`() {
        assertEquals(16_294_208_416_658_607_535uL, splitmix64(0uL))
        assertEquals(16_490_336_266_968_443_936uL, splitmix64(ULong.MAX_VALUE))
    }

    @Test
    fun `la courte liste plafonne chaque artiste`() {
        // the_shortlist_caps_each_artist
        val classes = listOf(1 to 7L, 2 to 7L, 3 to 7L, 4 to null, 5 to 8L)
        val retenus = courteListe(classes, { it.second }, parArtiste = 2, limite = 10)
        assertEquals(listOf(1, 2, 4, 5), retenus.map { it.first })

        val courte = courteListe(listOf(1, 2, 3), { null }, parArtiste = 1, limite = 2)
        assertEquals(listOf(1, 2), courte)
    }
}
