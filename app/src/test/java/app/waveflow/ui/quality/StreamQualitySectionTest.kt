package app.waveflow.ui.quality

import app.waveflow.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ce que la section propose, et ce qu'elle dit sous les choix. */
class StreamQualitySectionTest {

    @Test
    fun `sans rien savoir du serveur, aucun choix n'est ferme`() {
        // Une coupure réseau n'apprend rien de la capacité du serveur :
        // griser les profils sur un relevé manqué fermerait ce qu'il sert.
        val etat = StreamQualityUiState(transcodingAvailable = null)

        StreamQuality.entries.forEach { assertTrue(it.name, etat.isSelectable(it)) }
    }

    @Test
    fun `un serveur sans ffmpeg ne laisse que l'original`() {
        val etat = StreamQualityUiState(transcodingAvailable = false)

        assertTrue(etat.isSelectable(StreamQuality.Original))
        assertFalse(etat.isSelectable(StreamQuality.Haute))
        assertFalse(etat.isSelectable(StreamQuality.Economie))
    }

    @Test
    fun `par defaut, la note dit que la file en cours n'est pas touchee`() {
        // Changer d'avis en pleine écoute sans que rien ne bouge se lirait
        // sinon comme une panne.
        assertEquals(
            "S'applique aux pistes lancées ensuite, pas à la file en cours.",
            note(StreamQualityUiState(transcodingAvailable = true)),
        )
    }

    @Test
    fun `un serveur sans ffmpeg le dit, sans alarme quand le choix est servi`() {
        val etat = StreamQualityUiState(current = StreamQuality.Original, transcodingAvailable = false)

        assertFalse(etat.isCurrentUnavailable)
        assertEquals(
            "Ce serveur ne sait pas transcoder : seule la qualité d'origine est disponible.",
            note(etat),
        )
    }

    @Test
    fun `un choix que ce serveur ne sert pas est signale`() {
        // La préférence survit à un changement de serveur : on peut arriver
        // sur un serveur sans ffmpeg avec Économie déjà choisie.
        val etat = StreamQualityUiState(current = StreamQuality.Economie, transcodingAvailable = false)

        assertTrue(etat.isCurrentUnavailable)
        assertTrue(note(etat), note(etat).endsWith("Revenez à la qualité d'origine."))
    }
}
