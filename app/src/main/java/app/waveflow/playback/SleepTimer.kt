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
import java.util.concurrent.atomic.AtomicInteger

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
    private val _expirations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expirations: SharedFlow<Unit> = _expirations.asSharedFlow()

    /**
     * Sérialise la transition d'expiration avec les réglages de l'utilisateur.
     *
     * Comparer le numéro puis agir ne suffit pas : c'est leur **écartement** qui
     * laisse passer. Un réarmement glissé entre les deux verrait son échéance
     * effacée par la minuterie qu'il vient de remplacer, et la lecture
     * s'arrêterait alors qu'on demandait une heure de plus.
     *
     * L'émission tient dans le verrou parce que le tampon la rend
     * non suspendante : `tryEmit` accepte toujours, la capacité couvrant
     * l'expiration unique qu'une minuterie peut produire.
     */
    private val verrou = Any()

    private var job: Job? = null

    /**
     * Numéro de la minuterie courante.
     *
     * `Job.cancel()` ne suffit pas : entre le réveil du `delay` et les lignes
     * qui suivent, la coroutine est déjà repartie et l'annulation ne la
     * rattrape plus. Elle effacerait alors l'échéance qu'un réarmement vient de
     * poser, perdrait la référence du nouveau `Job` — devenu inannulable — et
     * mettrait la lecture en pause alors qu'on vient de demander une heure de
     * plus. Chaque minuterie porte donc son numéro et ne touche à l'état que si
     * c'est encore le sien.
     *
     * Atomique parce que le numéro s'incrémente depuis le fil qui règle la
     * minuterie et se lit depuis celui où le `delay` s'achève.
     */
    private val generation = AtomicInteger(0)

    /**
     * Arme la minuterie pour [durationMs], en remplaçant celle qui courait.
     *
     * Une durée nulle ou négative ne décrit aucune attente : elle annule, plutôt
     * que d'arrêter la lecture sur-le-champ — ce que personne ne demande en
     * réglant une minuterie.
     */
    fun start(durationMs: Long) {
        synchronized(verrou) {
            val mien = eteindre()
            if (durationMs <= 0L) return

            _endsAtMs.value = nowMs() + durationMs
            job = scope.launch {
                delay(durationMs)
                expirer(mien)
            }
        }
    }

    /**
     * Constate l'échéance, si cette minuterie est encore celle qui court.
     *
     * Tout tient dans le verrou : reconnaître son numéro, éteindre l'échéance et
     * prévenir. Une minuterie périmée — remplacée ou annulée pendant qu'elle
     * attendait — repart sans rien toucher.
     *
     * L'échéance est effacée **avant** que l'on prévienne : un abonné qui
     * regarde l'état en réagissant doit voir une minuterie éteinte, pas une
     * heure déjà passée.
     */
    private fun expirer(mien: Int) = synchronized(verrou) {
        if (generation.get() != mien) return@synchronized

        _endsAtMs.value = null
        _expirations.tryEmit(Unit)
    }

    /** Éteint la minuterie sans arrêter la lecture. */
    fun cancel() {
        synchronized(verrou) { eteindre() }
    }

    /**
     * Éteint ce qui court et ouvre un nouveau numéro.
     *
     * Rend ce numéro pour que [start] le confie à la minuterie qu'il arme :
     * c'est ce qui permet à celle-ci de reconnaître, en s'éveillant, si elle est
     * toujours la bonne.
     *
     * À n'appeler que sous [verrou].
     */
    private fun eteindre(): Int {
        job?.cancel()
        job = null
        _endsAtMs.value = null
        return generation.incrementAndGet()
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
