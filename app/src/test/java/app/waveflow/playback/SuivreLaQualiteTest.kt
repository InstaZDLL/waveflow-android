package app.waveflow.playback

import app.waveflow.model.AppPreferences
import app.waveflow.model.StreamNetwork
import app.waveflow.model.StreamQuality
import app.waveflow.model.StreamRendering
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ce qui décide de réécrire la file, et avec quel rendu : Q4 et Q5 de
 * `docs/qualite-automatique.md`. La réécriture elle-même est éprouvée sur un
 * vrai lecteur, dans [PistesAVenirTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SuivreLaQualiteTest {

    private val automatique = AppPreferences(
        automaticQuality = true,
        unmeteredQuality = StreamQuality.Original,
        meteredQuality = StreamQuality.Economie,
    )

    private val preferences = MutableStateFlow(automatique)
    private val reseau = MutableStateFlow(StreamNetwork.Unmetered)
    private val rendu = MutableStateFlow<StreamRendering?>(null)
    private val reecritures = mutableListOf<StreamRendering>()

    private fun suivre(test: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val suivi = launch { suivreLaQualite(preferences, reseau, rendu) { reecritures += it } }
        runCurrent()
        test()
        suivi.cancel()
    }

    /**
     * Pose une valeur et la laisse traverser : un `StateFlow` ne garde que la
     * dernière, et deux valeurs posées d'affilée n'en feraient voir qu'une.
     */
    private fun <T> TestScope.poser(flux: MutableStateFlow<T>, valeur: T) {
        flux.value = valeur
        runCurrent()
    }

    @Test
    fun `une file prend le rendu du reseau du moment`() = suivre {
        assertEquals(StreamQuality.Original.rendering, rendu.value)

        poser(reseau, StreamNetwork.Metered)

        assertEquals(StreamQuality.Economie.rendering, rendu.value)
    }

    @Test
    fun `le premier reseau ne reecrit rien`() = suivre {
        // Q4 : la file présente l'a déjà pris en entrant.
        assertEquals(emptyList<StreamRendering>(), reecritures)
    }

    @Test
    fun `un changement de reseau reecrit les pistes a venir dans le rendu du nouveau`() = suivre {
        poser(reseau, StreamNetwork.Metered)
        poser(reseau, StreamNetwork.Unmetered)

        assertEquals(listOf(StreamQuality.Economie.rendering, StreamQuality.Original.rendering), reecritures)
    }

    @Test
    fun `sous un profil fixe le reseau ne change rien`() = suivre {
        poser(preferences, automatique.copy(automaticQuality = false, streamQuality = StreamQuality.Haute))

        poser(reseau, StreamNetwork.Metered)

        assertEquals(StreamQuality.Haute.rendering, rendu.value)
        assertEquals(emptyList<StreamRendering>(), reecritures)
    }

    @Test
    fun `changer le reglage ne reecrit pas la file en cours`() = suivre {
        // Q5 : seul le réseau l'atteint ; l'écran dit que le réglage vaut pour
        // les pistes lancées ensuite.
        poser(preferences, automatique.copy(unmeteredQuality = StreamQuality.Haute))
        poser(preferences, automatique.copy(automaticQuality = false))
        poser(preferences, automatique)

        assertEquals(StreamQuality.Original.rendering, rendu.value)
        assertEquals(emptyList<StreamRendering>(), reecritures)
    }
}
