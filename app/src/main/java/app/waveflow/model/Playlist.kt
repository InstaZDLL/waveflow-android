package app.waveflow.model

/** Une playlist locale. Son contenu est porté à part, par [PlaylistEntry]. */
data class Playlist(
    val id: Long,
    val name: String,
)

/**
 * Appartenance d'un morceau à une playlist.
 *
 * [songId] est l'identifiant du [Song] correspondant ; il peut ne plus rien
 * désigner si le fichier a disparu de l'appareil.
 */
data class PlaylistEntry(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)
