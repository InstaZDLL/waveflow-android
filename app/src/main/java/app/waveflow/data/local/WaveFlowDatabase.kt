package app.waveflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base locale de l'application.
 *
 * Ne contient que ce que le MediaStore ne sait pas stocker. Les morceaux, eux,
 * ne sont pas dupliqués ici : les playlists ne retiennent que leurs
 * identifiants.
 */
@Database(
    entities = [PlaylistEntity::class, PlaylistSongEntity::class, PlayHistoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class WaveFlowDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    abstract fun playHistoryDao(): PlayHistoryDao

    companion object {
        private const val NAME = "waveflow.db"

        /**
         * Ajoute l'historique d'écoute.
         *
         * Une migration écrite plutôt qu'une reconstruction destructive : les
         * playlists de l'utilisateur vivent dans cette base, et il n'y a aucune
         * raison de les lui reprendre pour ajouter une table à côté.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_history` (" +
                        "`media_id` TEXT NOT NULL, " +
                        "`last_played_at` INTEGER NOT NULL, " +
                        "`play_count` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`media_id`))",
                )
            }
        }

        fun build(context: Context): WaveFlowDatabase =
            Room.databaseBuilder(context.applicationContext, WaveFlowDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
