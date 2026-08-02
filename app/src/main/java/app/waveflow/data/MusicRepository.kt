package app.waveflow.data

import app.waveflow.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Accès à la bibliothèque musicale, vu par le reste de l'application.
 *
 * L'abstraction est volontairement minimale : l'UI ne sait pas d'où viennent
 * les morceaux (MediaStore aujourd'hui, cache local ou serveur WaveFlow
 * demain), et les ViewModels peuvent être testés avec une fausse
 * implémentation.
 */
interface MusicRepository {

    /**
     * Flux des morceaux disponibles, ré-émis quand la source change.
     *
     * Le flux peut échouer (ex. [SecurityException] si la permission audio a
     * été révoquée) : l'appelant est responsable de la gestion d'erreur.
     */
    fun observeSongs(): Flow<List<Song>>
}
