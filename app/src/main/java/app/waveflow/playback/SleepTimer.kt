package app.waveflow.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * L'heure à laquelle la lecture doit s'arrêter d'elle-même.
 *
 * Elle ne connaît pas le lecteur : elle dit **quand**, pas **quoi faire**. Le
 * service écoute [expirations] et met en pause ; l'écran lit [endsAtMs] pour
 * savoir s'il faut allumer son icône. Ainsi la question « quand faut-il
 * s'arrêter » s'éprouve sans démarrer de service ni de lecteur.
 *
 * Portée par l'application et non par le service : on règle une minuterie puis
 * on quitte l'écran, et souvent l'application elle-même. Elle vit donc aussi
 * longtemps que le processus — ce qui suffit, puisque la lecture s'arrête avec
 * lui de toute façon.
 *
 * **L'instant de fin plutôt qu'un décompte.** Un `StateFlow` du temps restant
 * demanderait une coroutine qui l'entretient à la seconde, pour un affichage
 * que personne ne regarde la plupart du temps. L'échéance, elle, ne bouge pas
 * tant que la minuterie n'est pas retouchée : qui veut un décompte le dérive.
 *
 * @param nowMs horloge injectée, pour que les tests mesurent le temps même où
 *   leurs `delay` s'écoulent. En production, `elapsedRealtime` — elle ne recule
 *   pas quand l'horloge du téléphone est remise à l'heure, ce qui écourterait
 *   ou prolongerait une minuterie en cours.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) {

    private val _endsAtMs = MutableStateFlow<Long?>(null)

    /** L'échéance, ou `null` si aucune minuterie ne court. */
    val endsAtMs: StateFlow<Long?> = _endsAtMs.asStateFlow()

    /**
     * Émet quand l'échéance est atteinte, jamais quand elle est annulée.
     *
     * Un `StateFlow` ne suffirait pas : `endsAtMs` retombe à `null` dans les
     * deux cas, et le service ne saurait pas s'il doit mettre en pause ou s'il
     * vient d'obéir à l'utilisateur. `replay = 0` parce qu'un abonné qui
     * arrive après coup n'a rien à rattraper — l'arrêt a déjà eu lieu.
     */
    private val _expirations = MutableSharedFlow<Unit>()
    val expirations: SharedFlow<Unit> = _expirations.asSharedFlow()

    private var job: Job? = null

    /**
     * Arme la minuterie pour [durationMs], en remplaçant celle qui courait.
     *
     * Une durée nulle ou négative ne décrit aucune attente : elle annule, plutôt
     * que d'arrêter la lecture sur-le-champ — ce que personne ne demande en
     * réglant une minuterie.
     */
    fun start(durationMs: Long) {
        cancel()
        if (durationMs <= 0L) return

        _endsAtMs.value = nowMs() + durationMs
        job = scope.launch {
            delay(durationMs)
            // Remis à zéro **avant** de prévenir : un abonné qui regarde l'état
            // en réagissant doit voir une minuterie éteinte, pas une échéance
            // déjà passée.
            _endsAtMs.value = null
            job = null
            _expirations.emit(Unit)
        }
    }

    /** Éteint la minuterie sans arrêter la lecture. */
    fun cancel() {
        job?.cancel()
        job = null
        _endsAtMs.value = null
    }

    /**
     * Ce qu'il reste à attendre, ou `null` si aucune minuterie ne court.
     *
     * Jamais négatif : entre l'échéance et le réveil de la coroutine, il
     * s'écoule un instant pendant lequel un décompte afficherait un temps à
     * rebours.
     */
    fun remainingMs(): Long? = _endsAtMs.value?.let { (it - nowMs()).coerceAtLeast(0L) }
}
