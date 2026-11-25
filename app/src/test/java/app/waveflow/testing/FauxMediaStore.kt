package app.waveflow.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import app.waveflow.model.Song
import org.robolectric.Robolectric
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Un MediaStore qui connaît quelques morceaux.
 *
 * Sous Robolectric, l'autorité `media` ne répond rien : la bibliothèque de
 * l'appareil reste vide, et avec elle l'arbre que le service présente. Ce
 * fournisseur répond à la requête de `MediaStoreMusicRepository` avec les
 * colonnes qu'elle lit, sans rien interpréter de sa sélection ni de son tri :
 * les morceaux sont rendus dans l'ordre donné, qui doit donc être celui du
 * MediaStore — par titre.
 */
class FauxMediaStore : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(COLONNES).apply {
        // Bornée : une lecture qu'on oublierait de libérer doit faire échouer
        // le test, pas le figer — ni rendre les morceaux comme si de rien
        // n'était. L'application attrape l'échec de la requête et tient la
        // bibliothèque pour vide ; [vider] le fait donc remonter au test.
        if (!retenue.await(RETENUE_MAX_S, TimeUnit.SECONDS)) {
            val message = "FauxMediaStore retenu plus de $RETENUE_MAX_S s : liberer() oublié"
            expiree = message
            error(message)
        }
        morceaux.forEach { song ->
            addRow(
                arrayOf<Any?>(
                    song.id, song.title, song.artist, song.artistId, song.album,
                    song.albumId, song.durationMs, "${song.title}.flac", song.addedAtMs / 1_000L,
                ),
            )
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private val COLONNES = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
        )

        @Volatile
        private var morceaux: List<Song> = emptyList()

        @Volatile
        private var retenue = CountDownLatch(0)

        @Volatile
        private var expiree: String? = null

        private const val RETENUE_MAX_S = 30L

        /**
         * Retient la lecture de la bibliothèque jusqu'à [liberer] : c'est le
         * démarrage à froid, où le MediaStore n'a pas encore répondu.
         */
        fun retenir() {
            retenue = CountDownLatch(1)
        }

        fun liberer() {
            retenue.countDown()
        }

        /**
         * Installe le fournisseur avec [songs]. À appeler **avant** que
         * l'application ne lise la bibliothèque — avant de créer le service.
         */
        fun installer(songs: List<Song>) {
            morceaux = songs
            Robolectric.setupContentProvider(FauxMediaStore::class.java, MediaStore.AUTHORITY)
        }

        /**
         * Rend l'autorité muette pour le test suivant, et fait échouer celui
         * qui s'achève si une lecture a expiré pendant qu'il la retenait.
         */
        fun vider() {
            morceaux = emptyList()
            liberer()
            val message = expiree
            expiree = null
            check(message == null) { message.orEmpty() }
        }
    }
}
