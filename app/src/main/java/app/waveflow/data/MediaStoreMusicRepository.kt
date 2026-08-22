package app.waveflow.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.net.toUri
import app.waveflow.model.Song
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Implémentation locale de [MusicRepository] : interroge le MediaStore
 * (fichiers audio indexés par Android) en lecture seule.
 *
 * Un [ContentObserver] surveille la collection audio, si bien qu'un morceau
 * ajouté ou supprimé pendant que l'app tourne provoque une ré-émission
 * automatique — pas besoin d'un « pull to refresh ».
 */
class MediaStoreMusicRepository(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MusicRepository {

    override fun observeSongs(): Flow<List<Song>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(COLLECTION, /* notifyForDescendants = */ true, observer)
        // Première émission : l'état courant de la bibliothèque.
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }
        // Une rafale de modifications MediaStore ne déclenche qu'une requête.
        .conflate()
        .map { querySongs() }
        .flowOn(ioDispatcher)

    private fun querySongs(): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )

        // Uniquement les vrais morceaux de musique (pas les sonneries/notifs).
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = mutableListOf<Song>()

        contentResolver.query(COLLECTION, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
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
                    uri = ContentUris.withAppendedId(COLLECTION, id),
                    title = title,
                    artist = cursor.getString(artistCol),
                    artistId = cursor.getLong(artistIdCol),
                    album = cursor.getString(albumCol),
                    albumId = albumId,
                    durationMs = cursor.getLong(durationCol),
                    artworkUri = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId),
                )
            }
        }

        return songs
    }

    private companion object {
        val COLLECTION: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // URI de base des pochettes d'album (chemin historique mais toujours
        // fonctionnel sur les versions ciblées ; Coil affiche un placeholder si
        // rien ne résout).
        val ALBUM_ART_BASE: Uri = "content://media/external/audio/albumart".toUri()
    }
}
