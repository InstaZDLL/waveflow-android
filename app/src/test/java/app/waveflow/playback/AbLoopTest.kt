package app.waveflow.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** La pose des deux bornes, sans lecteur ni service. */
class AbLoopTest {

    private val loop = AbLoop()

    @Test
    fun `le premier appui pose A, le second arme la boucle`() {
        loop.mark(PISTE, 10_000L)

        assertEquals(AbLoopState.Started(PISTE, 10_000L), loop.state.value)

        loop.mark(PISTE, 25_000L)

        assertEquals(AbLoopState.Armed(PISTE, 10_000L, 25_000L), loop.state.value)
    }

    @Test
    fun `le troisieme appui efface`() {
        // Un seul bouton pour les trois temps : c'est ce qu'on attend de
        // quelque chose qu'on presse en écoutant, sans quitter la musique.
        loop.mark(PISTE, 10_000L)
        loop.mark(PISTE, 25_000L)

        loop.mark(PISTE, 30_000L)

        assertEquals(AbLoopState.Off, loop.state.value)
    }

    @Test
    fun `marquer B avant A remet les bornes dans l'ordre`() {
        // On écoute, on marque, on revient en arrière et on marque de nouveau :
        // l'utilisateur a désigné un intervalle, son sens de parcours n'est pas
        // son propos. Refuser le second appui l'obligerait à tout recommencer.
        loop.mark(PISTE, 25_000L)
        loop.mark(PISTE, 10_000L)

        assertEquals(AbLoopState.Armed(PISTE, 10_000L, 25_000L), loop.state.value)
    }

    @Test
    fun `deux fois le meme instant ne delimite rien et rouvre la pose`() {
        // La lecture y rebondirait sans avancer. Rouvrir la pose vaut mieux que
        // d'armer une boucle qui fige le morceau sur place.
        loop.mark(PISTE, 10_000L)
        loop.mark(PISTE, 10_000L)

        assertEquals(AbLoopState.Started(PISTE, 10_000L), loop.state.value)
    }

    @Test
    fun `marquer sur une autre piste recommence la pose`() {
        // Les deux bornes doivent appartenir au même morceau. Qui marque
        // ailleurs désigne un nouveau passage, il n'achève pas l'ancien.
        loop.mark(PISTE, 10_000L)

        loop.mark(AUTRE_PISTE, 4_000L)

        assertEquals(AbLoopState.Started(AUTRE_PISTE, 4_000L), loop.state.value)
    }

    @Test
    fun `changer de piste efface une boucle armee`() {
        // A et B désignent des instants d'un morceau donné. Sans cet effacement,
        // deux bornes prises dans un solo de guitare s'appliqueraient au morceau
        // d'après, qui n'a rien demandé.
        loop.mark(PISTE, 10_000L)
        loop.mark(PISTE, 25_000L)

        loop.clearIfOtherTrack(AUTRE_PISTE)

        assertEquals(AbLoopState.Off, loop.state.value)
    }

    @Test
    fun `rester sur la meme piste laisse la boucle intacte`() {
        // Media3 rapporte aussi une transition en rebouclant sur le même
        // morceau : l'effacer là ferait tomber la boucle au premier tour.
        loop.mark(PISTE, 10_000L)
        loop.mark(PISTE, 25_000L)
        val armee = loop.state.value

        loop.clearIfOtherTrack(PISTE)

        assertEquals(armee, loop.state.value)
    }

    @Test
    fun `la lecture qui s'arrete efface la boucle`() {
        // Une boucle sans morceau ne désigne rien.
        loop.mark(PISTE, 10_000L)

        loop.clearIfOtherTrack(null)

        assertEquals(AbLoopState.Off, loop.state.value)
    }

    @Test
    fun `une position negative est ramenee a zero`() {
        // `currentPosition` peut rendre une valeur négative tant que le lecteur
        // n'est pas prêt ; une borne avant le début rendrait la boucle
        // inatteignable.
        loop.mark(PISTE, -500L)

        assertEquals(AbLoopState.Started(PISTE, 0L), loop.state.value)
    }

    private companion object {
        const val PISTE = "piste-1"
        const val AUTRE_PISTE = "piste-2"
    }
}
