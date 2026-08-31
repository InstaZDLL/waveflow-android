package app.waveflow.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ce qui sépare une écoute d'un survol.
 *
 * Le seuil ne doit courir que pendant la lecture. Compter le temps qui passe,
 * sans regarder si le lecteur avance, inscrit dans l'historique une piste mise
 * en pause au bout de trois secondes, ou une piste que l'hôte a seulement
 * préparée et que personne n'a lancée.
 *
 * L'horloge du compteur est celle de l'ordonnanceur de test : le temps qu'il
 * mesure est exactement celui où ses `delay` s'écoulent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListeningCounterTest {

    private val comptees = mutableListOf<String>()

    private fun TestScope.compteur() = ListeningCounter(
        scope = this,
        thresholdMs = SEUIL,
        nowMs = { testScheduler.currentTime },
        onListened = { comptees += it },
    )

    @Test
    fun `une piste jouee jusqu'au seuil est comptee`() = runTest {
        val compteur = compteur()

        compteur.trackChanged("local:1")
        compteur.playingChanged(true)
        advanceTimeBy(SEUIL + 1)

        assertEquals(listOf("local:1"), comptees)
    }

    @Test
    fun `une piste seulement preparee n'est jamais comptee`() = runTest {
        // Android Auto prépare une file sans la lancer ; l'hôte peut aussi
        // charger une piste et attendre. Rien n'a été entendu.
        val compteur = compteur()

        compteur.trackChanged("local:1")
        advanceTimeBy(SEUIL * 10)

        assertEquals(emptyList<String>(), comptees)
    }

    @Test
    fun `une pause avant le seuil suspend le compte`() = runTest {
        val compteur = compteur()

        compteur.trackChanged("local:1")
        compteur.playingChanged(true)
        advanceTimeBy(SEUIL / 2)
        compteur.playingChanged(false)
        // Le temps continue de passer, mais plus rien ne joue.
        advanceTimeBy(SEUIL * 10)

        assertEquals(emptyList<String>(), comptees)
    }

    @Test
    fun `reprendre ne redemande que le temps qui restait`() = runTest {
        val compteur = compteur()

        compteur.trackChanged("local:1")
        compteur.playingChanged(true)
        advanceTimeBy(SEUIL - 5_000)
        compteur.playingChanged(false)
        advanceTimeBy(SEUIL * 10)
        compteur.playingChanged(true)

        // Quatre secondes de plus : il en manque encore une.
        advanceTimeBy(4_000)
        assertEquals(emptyList<String>(), comptees)

        advanceTimeBy(1_001)
        assertEquals(listOf("local:1"), comptees)
    }

    @Test
    fun `changer de piste remet le compte a zero`() = runTest {
        // Sans cette remise à zéro, dix-neuf secondes sur un titre plus une
        // seconde sur le suivant compteraient le suivant.
        val compteur = compteur()

        compteur.trackChanged("local:1")
        compteur.playingChanged(true)
        advanceTimeBy(SEUIL - 1_000)
        compteur.trackChanged("local:2")
        advanceTimeBy(2_000)

        assertEquals(emptyList<String>(), comptees)
    }

    @Test
    fun `enchainer les pistes ne compte que celle ou l'on s'arrete`() = runTest {
        val compteur = compteur()
        compteur.playingChanged(true)

        // On parcourt un album : chaque titre est quitté avant le seuil.
        repeat(5) { index ->
            compteur.trackChanged("local:$index")
            advanceTimeBy(2_000)
        }
        compteur.trackChanged("local:reste")
        advanceTimeBy(SEUIL + 1)

        assertEquals(listOf("local:reste"), comptees)
    }

    @Test
    fun `revenir en arriere dans une piste ne la compte pas deux fois`() = runTest {
        val compteur = compteur()

        compteur.trackChanged("local:1")
        compteur.playingChanged(true)
        advanceTimeBy(SEUIL + 1)
        // Pause, reprise, et on laisse tourner : l'écoute est déjà comptée.
        compteur.playingChanged(false)
        compteur.playingChanged(true)
        advanceTimeBy(SEUIL * 10)

        assertEquals(listOf("local:1"), comptees)
    }

    private companion object {
        const val SEUIL = 20_000L
    }
}
