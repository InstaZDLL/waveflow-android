package app.waveflow.playback

import androidx.media3.common.PlaybackException
import app.waveflow.data.remote.ServerException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La lecture d'un échec Media3.
 *
 * Ce que l'utilisateur lira dépend entièrement de cette traduction : accuser la
 * liaison quand c'est le fichier, ou l'inverse, envoie chercher la panne au
 * mauvais endroit.
 *
 * Robolectric parce que construire une [PlaybackException] horodate l'erreur
 * par `SystemClock` : sans lui, c'est le constructeur qui tombe, avant même
 * qu'on ait pu poser la question.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackFailureTest {

    @Test
    fun `une connexion qui echoue accuse la liaison`() {
        // Le cas observé : serveur coupé, le ticket ne peut pas être obtenu et
        // la connexion tombe avant la moindre lecture.
        val failure = exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

        assertEquals(PlaybackFailure.Unreachable, failure.toPlaybackFailure())
    }

    @Test
    fun `un delai depasse accuse la liaison`() {
        val failure = exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)

        assertEquals(PlaybackFailure.Unreachable, failure.toPlaybackFailure())
    }

    @Test
    fun `un statut HTTP hostile accuse la liaison`() {
        // Une session fermée ailleurs : le serveur répond, mais refuse.
        val failure = exception(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)

        assertEquals(PlaybackFailure.Unreachable, failure.toPlaybackFailure())
    }

    @Test
    fun `un fichier introuvable n'accuse pas le serveur`() {
        // Le piège de la famille de codes : `FILE_NOT_FOUND` est bien une
        // erreur d'entrée-sortie, mais un morceau local effacé depuis le scan
        // n'a rien à voir avec la liaison. Prendre la famille entière ferait
        // dire « serveur injoignable » à qui n'a même pas de serveur.
        val failure = exception(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)

        assertEquals(PlaybackFailure.Unplayable, failure.toPlaybackFailure())
    }

    @Test
    fun `un ticket impossible a obtenir accuse la liaison`() {
        // La chaîne relevée sur appareil, serveur coupé : le résolveur emballe
        // l'échec dans un IOException, que Media3 ne peut que classer
        // `UNSPECIFIED`. Sur son seul code, ce cas est indiscernable d'un
        // fichier illisible — et disait « ce morceau n'a pas pu être lu » à qui
        // avait simplement perdu son serveur.
        val failure = exception(
            errorCode = PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            cause = java.io.IOException(
                "Diffusion indisponible pour la piste 6add3eb2",
                ServerException.Unreachable("Failed to connect to /10.0.2.2:4533"),
            ),
        )

        assertEquals(PlaybackFailure.Unreachable, failure.toPlaybackFailure())
    }

    @Test
    fun `une cause circulaire ne fige pas la lecture`() {
        // `generateSequence` sur `cause` boucle sans fin sur un cycle. Java
        // refuse qu'une exception se désigne elle-même, mais rien n'empêche
        // deux exceptions de se désigner l'une l'autre — et la traduction se
        // fait sur le fil principal, où une boucle infinie fige l'écran.
        val premiere = java.io.IOException("aller")
        val seconde = java.io.IOException("retour")
        premiere.initCause(seconde)
        seconde.initCause(premiere)

        assertEquals(
            PlaybackFailure.Unplayable,
            exception(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, premiere).toPlaybackFailure(),
        )
    }

    @Test
    fun `un format que rien ne decode tient a la piste`() {
        val failure = exception(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)

        assertEquals(PlaybackFailure.Unplayable, failure.toPlaybackFailure())
    }

    @Test
    fun `un conteneur illisible tient a la piste`() {
        val failure = exception(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)

        assertEquals(PlaybackFailure.Unplayable, failure.toPlaybackFailure())
    }

    private fun exception(errorCode: Int, cause: Throwable? = null) =
        PlaybackException(/* message = */ null, cause, errorCode)
}
