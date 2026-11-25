package app.waveflow.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder

/**
 * Un ordre aléatoire figé, qui range chaque piste ajoutée **juste après celle
 * qui la précède dans la file**, et non à un rang tiré au hasard.
 *
 * L'ExoPlayer remplace une piste en ajoutant la nouvelle juste derrière
 * l'ancienne, puis en retirant l'ancienne : sous cet ordre, la nouvelle prend
 * à chaque étape la place exacte de l'ancienne, et rien d'autre ne bouge (Q9).
 *
 * @param rangs les rangs des pistes dans la file, dans l'ordre de lecture.
 */
internal class OrdreEnPlace private constructor(private val rangs: IntArray) : ShuffleOrder {

    /** La place de chaque rang dans [rangs]. */
    private val places = IntArray(rangs.size).also { places ->
        rangs.forEachIndexed { place, rang -> places[rang] = place }
    }

    override fun getLength(): Int = rangs.size

    override fun getNextIndex(index: Int): Int = rangs.getOrElse(places[index] + 1) { C.INDEX_UNSET }

    override fun getPreviousIndex(index: Int): Int = rangs.getOrElse(places[index] - 1) { C.INDEX_UNSET }

    override fun getLastIndex(): Int = rangs.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = rangs.firstOrNull() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val decales = rangs.map { if (it >= insertionIndex) it + insertionCount else it }
        // En tête quand rien ne les précède : `indexOf` rend alors -1.
        val place = decales.indexOf(insertionIndex - 1) + 1
        val ajoutes = List(insertionCount) { insertionIndex + it }
        return OrdreEnPlace((decales.take(place) + ajoutes + decales.drop(place)).toIntArray())
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val retires = indexToExclusive - indexFrom
        return OrdreEnPlace(
            rangs.filter { it !in indexFrom until indexToExclusive }
                .map { if (it >= indexToExclusive) it - retires else it }
                .toIntArray(),
        )
    }

    override fun cloneAndClear(): ShuffleOrder = OrdreEnPlace(IntArray(0))

    companion object {
        /** L'ordre de lecture de [ordre], figé. */
        fun de(ordre: ShuffleOrder): OrdreEnPlace {
            val rangs = IntArray(ordre.length)
            var rang = ordre.firstIndex
            for (place in rangs.indices) {
                rangs[place] = rang
                rang = ordre.getNextIndex(rang)
            }
            return OrdreEnPlace(rangs)
        }
    }
}
