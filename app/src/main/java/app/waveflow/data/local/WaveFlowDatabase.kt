package app.waveflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base locale de l'application.
 *
 * Ne contient que ce que le MediaStore ne sait pas stocker. Les morceaux, eux,
 * ne sont pas dupliqués ici : les playlists ne retiennent que leurs
 * identifiants.
 */
@Database(
    entities = [PlaylistEntity::class, PlaylistSongEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WaveFlowDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    companion object {
        private const val NAME = "waveflow.db"

        fun build(context: Context): WaveFlowDatabase =
            Room.databaseBuilder(context.applicationContext, WaveFlowDatabase::class.java, NAME)
                .build()
    }
}
