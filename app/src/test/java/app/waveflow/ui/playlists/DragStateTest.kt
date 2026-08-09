package app.waveflow.ui.playlists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Arithmétique du glisser-déposer.
 *
 * Le geste lui-même demanderait un appareil ; le calcul de destination et de
 * décalage visuel, non. C'est aussi la partie où une erreur se verrait le
 * moins à la relecture.
 */
class DragStateTest {

    private val rowHeight = 100f

    private fun dragFrom(index: Int) = DragState(
        songId = 1L,
        fromIndex = index,
        toIndex = index,
        offset = 0f,
        rowHeight = rowHeight,
    )

    @Test
    fun `une hauteur de ligne nulle est refusee a la construction`() {
        // Elle rendrait le rang visé infini plutôt que borné.
        assertThrows(IllegalArgumentException::class.java) {
            DragState(songId = 1L, fromIndex = 0, toIndex = 0, offset = 0f, rowHeight = 0f)
        }
    }

    @Test
    fun `une hauteur de ligne negative est refusee a la construction`() {
        // Elle inverserait le sens du déplacement.
        assertThrows(IllegalArgumentException::class.java) {
            DragState(songId = 1L, fromIndex = 0, toIndex = 0, offset = 0f, rowHeight = -10f)
        }
    }

    @Test
    fun `un deplacement inferieur a la demi-hauteur ne change pas de rang`() {
        val state = dragFrom(2).advance(deltaY = 49f, lastIndex = 5)

        assertEquals(2, state.toIndex)
    }

    @Test
    fun `passe la demi-hauteur le rang suivant est vise`() {
        val state = dragFrom(2).advance(deltaY = 51f, lastIndex = 5)

        assertEquals(3, state.toIndex)
    }

    @Test
    fun `un deplacement vers le haut vise le rang precedent`() {
        val state = dragFrom(2).advance(deltaY = -51f, lastIndex = 5)

        assertEquals(1, state.toIndex)
    }

    @Test
    fun `les deltas s'accumulent sur la duree du geste`() {
        // Le doigt bouge par petits pas : aucun ne franchit seul la moitié
        // d'une ligne, leur somme si.
        val state = dragFrom(0)
            .advance(deltaY = 30f, lastIndex = 5)
            .advance(deltaY = 30f, lastIndex = 5)
            .advance(deltaY = 30f, lastIndex = 5)

        assertEquals(1, state.toIndex)
        assertEquals(90f, state.offset, 0.01f)
    }

    @Test
    fun `la destination est bornee par le haut de la liste`() {
        val state = dragFrom(1).advance(deltaY = -1_000f, lastIndex = 5)

        assertEquals(0, state.toIndex)
    }

    @Test
    fun `la destination est bornee par le bas de la liste`() {
        val state = dragFrom(1).advance(deltaY = 1_000f, lastIndex = 5)

        assertEquals(5, state.toIndex)
    }

    @Test
    fun `le decalage visuel retranche ce que le changement de rang a absorbe`() {
        // Une ligne pleine parcourue : la permutation a déjà déplacé la ligne
        // d'autant, il ne reste rien à décaler.
        val state = dragFrom(0).advance(deltaY = rowHeight, lastIndex = 5)

        assertEquals(1, state.toIndex)
        assertEquals(0f, state.translationFor(1L), 0.01f)
    }

    @Test
    fun `le decalage visuel suit le doigt entre deux rangs`() {
        val state = dragFrom(0).advance(deltaY = 60f, lastIndex = 5)

        // Rang déjà avancé de 1 (soit 100 px), doigt à 60 : la ligne doit
        // remonter de 40 pour rester sous lui.
        assertEquals(1, state.toIndex)
        assertEquals(-40f, state.translationFor(1L), 0.01f)
    }

    @Test
    fun `les autres lignes ne sont pas decalees`() {
        val state = dragFrom(0).advance(deltaY = 60f, lastIndex = 5)

        assertEquals(0f, state.translationFor(99L), 0.01f)
    }

    @Test
    fun `moved descend un element sans changer la taille`() {
        val moved = listOf("a", "b", "c", "d").moved(from = 0, to = 2)

        assertEquals(listOf("b", "c", "a", "d"), moved)
    }

    @Test
    fun `moved remonte un element`() {
        val moved = listOf("a", "b", "c", "d").moved(from = 3, to = 1)

        assertEquals(listOf("a", "d", "b", "c"), moved)
    }

    @Test
    fun `moved ne modifie pas la liste d'origine`() {
        val original = listOf("a", "b", "c")

        original.moved(from = 0, to = 2)

        assertEquals(listOf("a", "b", "c"), original)
    }
}
