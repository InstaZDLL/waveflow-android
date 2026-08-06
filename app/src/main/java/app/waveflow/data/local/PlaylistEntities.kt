package app.waveflow.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une playlist créée dans l'application.
 *
 * Les playlists sont la première donnée qui n'existe pas dans le MediaStore :
 * elles n'ont d'autre source que cette base, et c'est elle qui portera plus
 * tard la synchronisation avec le serveur WaveFlow — d'où [createdAt] et
 * [updatedAt], inutilisés par l'UI mais nécessaires à une résolution de
 * conflits.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Appartenance d'un morceau à une playlist.
 *
 * La clé primaire composite interdit les doublons : ajouter deux fois le même
 * morceau à une playlist est sans effet plutôt qu'une seconde ligne.
 *
 * [songId] référence un identifiant MediaStore. Il est stable tant que la
 * bibliothèque n'est pas ré-indexée de zéro ; un morceau introuvable est
 * simplement ignoré à l'affichage. La synchronisation serveur demandera une
 * clé plus robuste (chemin ou empreinte).
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)
