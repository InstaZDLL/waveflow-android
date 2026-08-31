package app.waveflow.model

import android.net.Uri

/**
 * Un album, tel que déduit des morceaux de la bibliothèque.
 *
 * @property id identifiant MediaStore de l'album.
 * @property artist artiste principal — celui du premier morceau ; les albums
 *   de compilation afficheront donc l'artiste de leur première piste.
 * @property trackCount nombre de morceaux présents sur l'appareil, pas le
 *   nombre de pistes de l'album original.
 */
data class Album(
    val id: Long,
    val title: String,
    val artist: String?,
    val artworkUri: Uri?,
    val trackCount: Int,
    val durationMs: Long,
    /**
     * La plus récente des dates d'ajout de ses pistes.
     *
     * La plus récente et non la plus ancienne : ce que l'accueil appelle un
     * ajout récent, c'est ce que l'utilisateur vient de mettre sur son
     * appareil. Un album copié d'un coup a de toute façon des dates voisines ;
     * un vieil album qu'on complète remonte, ce qui est encore ce qu'on
     * attend d'une liste des derniers arrivés.
     */
    val addedAtMs: Long = 0L,
) {
    val displayArtist: String
        get() = artist.orUnknownArtist()
}
