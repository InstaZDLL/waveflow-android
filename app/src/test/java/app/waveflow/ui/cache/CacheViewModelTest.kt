package app.waveflow.ui.cache

import app.waveflow.playback.PlaybackCache
import app.waveflow.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Le cache vu depuis les réglages.
 *
 * Ce qui compte ici n'est pas le vidage — c'est le cache qui le fait, et
 * `RemoteMediaCacheTest` le prouve sur la vraie chaîne Media3 — mais ce que
 * l'écran affiche quand il échoue, et qu'il ne se referme pas dessus.
 */
@RunWith(RobolectricTestRunner::class)
class CacheViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class CacheFactice(
        override val maxBytes: Long = 1_000L,
        var occupe: Long = 0L,
        var echoueAuVidage: Throwable? = null,
        var echoueALaMesure: Throwable? = null,
    ) : PlaybackCache {

        var vidages = 0
            private set

        var mesures = 0
            private set

        /** Posée par un test, elle retient la mesure le temps voulu. */
        var barriere: CompletableDeferred<Unit>? = null

        override suspend fun usedBytes(): Long {
            mesures++
            // La taille est relevée à l'appel, comme le ferait un vrai accès
            // disque. C'est ce qui rend observable une mesure devenue périmée :
            // elle rapporte ce qu'elle a vu, pas ce que le cache contient au
            // moment où elle revient.
            val instantane = occupe
            // La barrière ne retient que la mesure suivante, pas toutes.
            val porte = barriere
            barriere = null
            porte?.await()
            echoueALaMesure?.let { throw it }
            return instantane
        }

        override suspend fun clear() {
            vidages++
            echoueAuVidage?.let { throw it }
            occupe = 0L
        }
    }

    @Test
    fun `avant toute mesure la taille reste inconnue`() {
        // Et non pas zéro : l'écran doit pouvoir annoncer l'attente.
        val vm = CacheViewModel(CacheFactice(occupe = 800L))

        assertNull(vm.state.value.usedBytes)
        assertEquals(1_000L, vm.state.value.maxBytes)
    }

    @Test
    fun `la mesure publie la place occupee`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = CacheViewModel(CacheFactice(occupe = 800L))

        vm.refresh()

        assertEquals(800L, vm.state.value.usedBytes)
    }

    @Test
    fun `vider republie une taille a jour`() = runTest(mainDispatcherRule.dispatcher) {
        val cache = CacheFactice(occupe = 800L)
        val vm = CacheViewModel(cache)
        vm.refresh()

        vm.clear()

        assertEquals(1, cache.vidages)
        assertEquals(0L, vm.state.value.usedBytes)
        assertFalse(vm.state.value.isClearing)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `un vidage qui echoue se dit sans faire tomber l'ecran`() {
        // Un fichier verrouillé, un index abîmé : l'écran doit le signaler et
        // rester utilisable, pas propager l'exception.
        val cache = CacheFactice(occupe = 800L, echoueAuVidage = IllegalStateException("index"))
        val vm = CacheViewModel(cache)

        vm.clear()

        assertNotNull(vm.state.value.errorMessage)
        assertFalse("l'écran doit se rendre à nouveau utilisable", vm.state.value.isClearing)
        // La mesure qui suit dit ce qui reste vraiment.
        assertEquals(800L, vm.state.value.usedBytes)
    }

    @Test
    fun `une mesure illisible garde la derniere valeur connue`() {
        // Afficher zéro ferait croire à un cache vide alors qu'il est plein.
        val cache = CacheFactice(occupe = 800L)
        val vm = CacheViewModel(cache)
        vm.refresh()

        cache.echoueALaMesure = IllegalStateException("index")
        vm.refresh()

        assertEquals(800L, vm.state.value.usedBytes)
    }

    @Test
    fun `un etat qui bouge pendant la mesure ne la fait pas repartir`() {
        // `MutableStateFlow.update` rejoue sa lambda quand l'état a changé
        // entre-temps. La mesure suspend le temps d'un accès disque : la placer
        // dans cette lambda la ferait repartir sur le disque pour rien.
        //
        // On rend la course déterministe — la mesure attend, l'état change,
        // puis on la libère.
        val cache = CacheFactice(occupe = 800L, echoueAuVidage = IllegalStateException("index"))
        val vm = CacheViewModel(cache)
        // Un échec d'abord, pour avoir un message à retirer ensuite : sans
        // changement réel, l'état resterait égal et le CAS ne rejouerait rien.
        vm.clear()
        assertNotNull(vm.state.value.errorMessage)

        val barriere = CompletableDeferred<Unit>()
        cache.barriere = barriere
        val avant = cache.mesures

        vm.refresh()
        vm.dismissError()
        barriere.complete(Unit)

        assertEquals("la mesure ne doit pas être rejouée", avant + 1, cache.mesures)
        assertEquals(800L, vm.state.value.usedBytes)
    }

    @Test
    fun `une mesure commencee avant le vidage ne le contredit pas`() {
        // L'écran se rouvre — donc une mesure part — et l'utilisateur vide dans
        // la foulée. La mesure a relevé 800 avant le vidage ; si elle publie
        // après lui, l'écran réaffiche une place que le vidage a libérée.
        //
        // La course est rendue déterministe : la mesure attend, le vidage
        // s'intercale, puis on la libère.
        val cache = CacheFactice(occupe = 800L)
        val vm = CacheViewModel(cache)

        val barriere = CompletableDeferred<Unit>()
        cache.barriere = barriere

        vm.refresh()
        vm.clear()
        barriere.complete(Unit)

        assertEquals(1, cache.vidages)
        assertEquals("le vidage doit avoir le dernier mot", 0L, vm.state.value.usedBytes)
        assertFalse(vm.state.value.isClearing)
    }

    @Test
    fun `le message d'erreur se referme`() {
        val vm = CacheViewModel(CacheFactice(echoueAuVidage = IllegalStateException("index")))
        vm.clear()
        assertNotNull(vm.state.value.errorMessage)

        vm.dismissError()

        assertNull(vm.state.value.errorMessage)
    }
}
