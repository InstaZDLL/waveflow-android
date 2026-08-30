package app.waveflow.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ce qu'une piste a laissé derrière elle : quand on l'a jouée, combien de fois.
 *
 * Une ligne par piste et non un journal d'événements. Un journal grandirait sans
 * fin alors que les deux questions qu'on lui pose — qu'ai-je écouté récemment,
 * qu'est-ce que j'écoute le plus — ne demandent qu'un compte et une date. La
 * table reste ainsi bornée par le nombre de morceaux distincts.
 *
 * La clé est le `mediaId`, seule identité que l'application donne indifféremment
 * à une piste de l'appareil (`local:…`) et à une piste du serveur (`remote:…`) :
 * l'historique couvre les deux sources sans avoir à les distinguer.
 */
@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_id")
    val mediaId: String,
    @ColumnInfo(name = "last_played_at")
    val lastPlayedAtMs: Long,
    @ColumnInfo(name = "play_count")
    val playCount: Int,
)
