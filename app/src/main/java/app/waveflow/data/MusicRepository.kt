package app.waveflow.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Source de vérité pour la bibliothèque locale : interroge le MediaStore
 * (fichiers audio du téléphone) et renvoie une liste de [Song].
 *
 * Aucune écriture pour l'instant — lecture seule des fichiers déjà indexés
 * par Android. La synchronisation avec le serveur WaveFlow viendra en couche
 * séparée, sans toucher à cette classe.
 */
class MusicRepository(private val context: Context) {

    // URI de base des pochettes d'album (chemin historique mais toujours
    // fonctionnel sur les versions ciblées ; Coil affiche un placeholder si
    // rien ne résout).
    private val albumArtBase: Uri = Uri.parse("content://media/external/audio/albumart")

    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )

        // Uniquement les vrais morceaux de musique (pas les sonneries/notifs).
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = mutableListOf<Song>()

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: cursor.getString(displayNameCol)
                    ?: "Sans titre"

                songs += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = title,
                    artist = cursor.getString(artistCol),
                    album = cursor.getString(albumCol),
                    durationMs = cursor.getLong(durationCol),
                    artworkUri = ContentUris.withAppendedId(albumArtBase, albumId),
                )
            }
        }

        songs
    }
}
