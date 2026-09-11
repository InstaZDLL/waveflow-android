package app.waveflow.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Les deux bornes entre lesquelles la lecture doit tourner.
 *
 * Comme [SleepTimer], elle ne connaît pas le lecteur : elle dit **où**, pas quoi
 * faire. Le service échantillonne la position et rembobine ; l'écran lit
 * [state] pour dire où en est la pose. La question « entre quels instants
 * tourne-t-on » s'éprouve ainsi sans démarrer ni service ni lecteur.
 *
 * Portée par l'application et non par le service : on pose une boucle pour
 * repiquer un passage, puis on éteint l'écran et on prend son instrument. Elle
 * doit survivre à l'écran, pas au processus — la lecture s'arrête avec lui de
 * toute façon.
 *
 * **Elle appartient à une piste.** A et B désignent des instants d'un morceau
 * donné ; passer au suivant les vide de leur sens, et la boucle s'efface. Sans
 * cela, deux bornes prises dans un solo de guitare s'appliqueraient au morceau
 * d'après.
 */
class AbLoop {

    private val _state = MutableStateFlow<AbLoopState>(AbLoopState.Off)
    val state: StateFlow<AbLoopState> = _state.asStateFlow()

    /**
     * Pose la borne suivante : d'abord A, puis B, puis efface.
     *
     * Un seul geste pour les trois temps — c'est ce qu'on attend d'un bouton
     * qu'on presse en écoutant, sans quitter la musique des yeux.
     *
     * @param mediaId la piste sur laquelle on pose. Marquer sur une autre que
     *   celle où A a été posé recommence : les deux bornes doivent appartenir au
     *   même morceau, et l'utilisateur qui marque ailleurs désigne un nouveau
     *   passage, il n'achève pas l'ancien.
     * @param positionMs l'instant marqué.
     */
    fun mark(mediaId: String, positionMs: Long) {
        val instant = positionMs.coerceAtLeast(0L)

        _state.update { courant ->
            when {
                courant is AbLoopState.Started && courant.mediaId == mediaId ->
                    courant.armerVers(instant)

                // Armée, un troisième appui efface. Sur une autre piste, il
                // recommence — voir plus haut.
                courant is AbLoopState.Armed && courant.mediaId == mediaId -> AbLoopState.Off

                else -> AbLoopState.Started(mediaId, instant)
            }
        }
    }

    /**
     * Achève la pose, ou la recommence si les deux bornes ne délimitent rien.
     *
     * Marquer B **avant** A n'est pas une faute : on écoute, on marque, on
     * revient en arrière et on marque de nouveau. Les deux instants sont alors
     * remis dans l'ordre plutôt que refusés — l'utilisateur a désigné un
     * intervalle, son sens de parcours n'est pas son propos.
     *
     * Deux fois le même instant, en revanche, ne délimite rien : la lecture y
     * rebondirait sans avancer. Ce second appui rouvre donc la pose au lieu de
     * l'achever.
     */
    private fun AbLoopState.Started.armerVers(instant: Long): AbLoopState = when {
        instant > startMs -> AbLoopState.Armed(mediaId, startMs, instant)
        instant < startMs -> AbLoopState.Armed(mediaId, instant, startMs)
        else -> AbLoopState.Started(mediaId, instant)
    }

    /** Efface la boucle sans toucher à la lecture en cours. */
    fun clear() {
        _state.value = AbLoopState.Off
    }

    /**
     * Efface la boucle si elle n'appartient pas à [mediaId].
     *
     * Appelé au changement de piste. `null` — plus rien ne joue — efface aussi :
     * une boucle sans morceau ne désigne rien.
     */
    fun clearIfOtherTrack(mediaId: String?) {
        _state.update { courant ->
            if (courant is AbLoopState.Off || courant.mediaId == mediaId) courant else AbLoopState.Off
        }
    }
}

/**
 * Où en est la pose des bornes.
 *
 * Trois états et non trois champs nullables : « A posé sans B » et « rien » se
 * distinguent alors d'eux-mêmes, et aucune combinaison absurde — un B sans A —
 * ne peut s'écrire.
 */
sealed interface AbLoopState {

    /** La piste à laquelle les bornes appartiennent, `null` quand il n'y en a pas. */
    val mediaId: String?

    /** Aucune borne posée. */
    data object Off : AbLoopState {
        override val mediaId: String? = null
    }

    /** A est posé, on attend B. */
    data class Started(
        override val mediaId: String,
        val startMs: Long,
    ) : AbLoopState

    /** Les deux bornes sont posées : la lecture doit tourner entre elles. */
    data class Armed(
        override val mediaId: String,
        val startMs: Long,
        val endMs: Long,
    ) : AbLoopState
}
