package app.waveflow.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le découpage en pages demandé par l'hôte.
 *
 * C'est la seule arithmétique du pont vers Media3, et celle où une borne
 * oubliée ne se voit pas : `subList` lève au lieu de rendre une liste vide, et
 * la navigation se couperait au dernier écran plutôt que de s'y terminer.
 */
class BrowsePageTest {

    private val cinq = listOf("a", "b", "c", "d", "e")

    @Test
    fun `la premiere page rend le debut de la liste`() {
        assertEquals(listOf("a", "b"), cinq.page(page = 0, pageSize = 2))
    }

    @Test
    fun `la derniere page est tronquee a ce qui reste`() {
        // Cinq éléments par pages de deux : la troisième page n'en a qu'un.
        assertEquals(listOf("e"), cinq.page(page = 2, pageSize = 2))
    }

    @Test
    fun `une page au-dela de la fin rend une liste vide`() {
        // L'hôte demande la page suivante jusqu'à ce qu'elle soit vide : c'est
        // ainsi qu'il sait qu'il a tout vu. Lever ici couperait la navigation.
        assertEquals(emptyList<String>(), cinq.page(page = 9, pageSize = 2))
    }

    @Test
    fun `une page qui commence pile a la fin rend une liste vide`() {
        // Le cas limite exact : `from` vaut la taille de la liste.
        assertEquals(emptyList<String>(), cinq.page(page = 1, pageSize = 5))
    }

    @Test
    fun `une taille de page plus grande que la liste la rend entiere`() {
        assertEquals(cinq, cinq.page(page = 0, pageSize = 100))
    }

    @Test
    fun `une taille de page nulle ou negative ne decrit aucune tranche`() {
        assertEquals(emptyList<String>(), cinq.page(page = 0, pageSize = 0))
        assertEquals(emptyList<String>(), cinq.page(page = 0, pageSize = -1))
    }

    @Test
    fun `un tres grand numero de page ne deborde pas`() {
        // `page * pageSize` sur des entiers déborderait et rendrait un indice
        // négatif, que `subList` refuserait.
        assertEquals(emptyList<String>(), cinq.page(page = Int.MAX_VALUE, pageSize = 1000))
    }

    @Test
    fun `une liste vide rend une liste vide`() {
        assertEquals(emptyList<String>(), emptyList<String>().page(page = 0, pageSize = 10))
    }
}
