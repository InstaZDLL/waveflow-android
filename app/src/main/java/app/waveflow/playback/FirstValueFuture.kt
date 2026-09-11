package app.waveflow.playback

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * La première valeur non nulle de cet état, sous la forme d'un futur que Media3
 * sait attendre.
 *
 * Immédiat quand la valeur est déjà là ; sinon une coroutine de [scope]
 * l'attend. La lecture de `value` qui précède n'est qu'un raccourci, sans écart :
 * si la valeur arrive entre elle et l'abonnement, `first` sur un `StateFlow` la
 * rend aussitôt.
 *
 * **Le futur se termine toujours**, y compris quand [scope] est annulé avant que
 * la valeur n'arrive — un service détruit pendant son démarrage à froid, ou
 * sollicité après sa destruction. Annuler la coroutine ne règle pas le futur de
 * lui-même : il resterait en suspens, et avec lui la réponse que Media3 attend
 * de la session.
 */
internal fun <T : Any> StateFlow<T?>.firstValueAsFuture(scope: CoroutineScope): ListenableFuture<T> {
    value?.let { return Futures.immediateFuture(it) }

    val futur = SettableFuture.create<T>()
    scope.launch { futur.set(filterNotNull().first()) }
        .invokeOnCompletion { cause -> if (cause != null) futur.setException(cause) }
    return futur
}
