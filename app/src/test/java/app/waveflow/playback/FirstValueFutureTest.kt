package app.waveflow.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'attente du réglage par le service, sous la forme que Media3 attend.
 *
 * C'est ce que `PlaybackService.renduDemande` rend à la session : ces tests
 * éprouvent donc l'attente au démarrage à froid au niveau du service, là où
 * `PlaybackServiceQualityTest` ne le peut pas — le fichier de préférences y est
 * déjà lu quand la file arrive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirstValueFutureTest {

    @Test
    fun `une valeur deja la est rendue sans attendre`() = runTest {
        val futur = MutableStateFlow<String?>("lue").firstValueAsFuture(backgroundScope)

        assertTrue(futur.isDone)
        assertEquals("lue", futur.get())
    }

    @Test
    fun `une valeur arrivee ensuite termine le futur`() = runTest {
        // Le démarrage à froid : la file arrive avant la lecture du réglage.
        val etat = MutableStateFlow<String?>(null)
        val futur = etat.firstValueAsFuture(backgroundScope)
        runCurrent()
        assertFalse("rien n'est encore lu", futur.isDone)

        etat.value = "lue"
        runCurrent()

        assertEquals("lue", futur.get())
    }

    @Test
    fun `une portee annulee pendant l'attente termine le futur`() = runTest {
        // Le service détruit pendant qu'une file attend le réglage. Sans cela
        // la réponse que Media3 attend de la session ne viendrait jamais.
        val portee = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        val futur = MutableStateFlow<String?>(null).firstValueAsFuture(portee)
        runCurrent()

        portee.cancel()
        runCurrent()

        assertTrue("le futur ne doit pas rester en suspens", futur.isDone)
    }

    @Test
    fun `une portee deja annulee termine aussi le futur`() = runTest {
        // Le service sollicité après sa destruction : la coroutine ne démarre
        // même pas, et c'est ce cas-là qu'un simple `set` à la fin manquait.
        val portee = CoroutineScope(Job().apply { cancel() } + StandardTestDispatcher(testScheduler))

        val futur = MutableStateFlow<String?>(null).firstValueAsFuture(portee)
        runCurrent()

        assertTrue("le futur ne doit pas rester en suspens", futur.isDone)
    }
}
