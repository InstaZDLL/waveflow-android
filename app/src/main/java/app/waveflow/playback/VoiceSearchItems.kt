package app.waveflow.playback

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.session.SessionCommand
import app.waveflow.model.VoiceFocus
import app.waveflow.model.VoiceRequest

/*
 * La forme sous laquelle une demande vocale circule entre l'hôte, l'activité et
 * la session. Les règles sont dans `docs/recherche-vocale.md`.
 */

/**
 * La commande de session par laquelle le téléphone relaie une demande vocale :
 * la phrase sous `SearchManager.QUERY`, les compléments de `MediaStore` à côté
 * (voir [toExtras]). Ici et non dans `BrowseCallback.kt` : ce fichier-là sert
 * aussi des tests sans Android, que l'initialisation d'un `Bundle` ferait
 * tomber.
 */
internal val PLAY_FROM_SEARCH = SessionCommand("app.waveflow.PLAY_FROM_SEARCH", Bundle.EMPTY)

/**
 * La demande vocale que porte cet élément, `null` s'il n'en porte pas (R3).
 *
 * Media3 fabrique cet élément pour `onPlayFromSearch` et `onPrepareFromSearch` :
 * un `mediaId` vide — `MediaItem.DEFAULT_MEDIA_ID`, pas `null` —, aucune URI,
 * la phrase dans `requestMetadata.searchQuery` et les compléments de l'hôte
 * dans `requestMetadata.extras`.
 */
internal fun MediaItem.voiceRequestOrNull(): VoiceRequest? {
    if (localConfiguration != null || requestMetadata.mediaUri != null || mediaId.isNotEmpty()) return null
    val query = requestMetadata.searchQuery ?: return null
    return voiceRequestOf(query, requestMetadata.extras)
}

/**
 * La demande que porte une intention `MEDIA_PLAY_FROM_SEARCH` du téléphone,
 * `null` si c'en est une autre, ou si elle ne doit pas être jouée (R10).
 *
 * Seuls les compléments que la recherche lit en sont tirés : le reste de
 * l'intention vient d'une autre application, et n'a rien à faire dans la file.
 *
 * Deux intentions sont écartées :
 * - celle que le système rejoue quand l'activité est rouverte depuis les
 *   récents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) : l'ancienne demande
 *   remplacerait la file en cours ;
 * - celle dont les compléments ne se lisent pas. L'activité est exportée :
 *   une autre application peut y glisser un `Parcelable` ou un `Serializable`
 *   inconnu, et jusqu'à l'API 32 la première lecture désérialise tout le
 *   `Bundle` — l'exception ferait tomber l'activité.
 */
@Suppress("SwallowedException")
fun voiceRequestOf(intent: Intent?): VoiceRequest? {
    if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return null
    if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    return try {
        voiceRequestOf(intent.extras ?: Bundle())
    } catch (illisible: RuntimeException) {
        // `BadParcelableException`, ou la `RuntimeException` nue que lève
        // `Parcel.readSerializable` : rien de plus précis ne les couvre toutes.
        null
    }
}

/**
 * L'élément à remettre à la session pour cette demande (R10).
 *
 * C'est la forme que Media3 donne lui-même à `onPlayFromSearch` : la session
 * résout ainsi la voiture et le téléphone par le même chemin.
 */
internal fun VoiceRequest.toSearchItem(): MediaItem = MediaItem.Builder()
    .setRequestMetadata(
        MediaItem.RequestMetadata.Builder()
            .setSearchQuery(query)
            .setExtras(toExtras().apply { remove(SearchManager.QUERY) })
            .build(),
    )
    .build()

/**
 * La demande à plat, telle que la commande [PLAY_FROM_SEARCH] la transporte :
 * la phrase sous `SearchManager.QUERY`, comme dans l'intention de l'Assistant,
 * et les compléments de `MediaStore`.
 */
internal fun VoiceRequest.toExtras(): Bundle = Bundle().apply {
    putString(SearchManager.QUERY, query)
    focus.contentType()?.let { putString(MediaStore.EXTRA_MEDIA_FOCUS, it) }
    album?.let { putString(MediaStore.EXTRA_MEDIA_ALBUM, it) }
    artist?.let { putString(MediaStore.EXTRA_MEDIA_ARTIST, it) }
    title?.let { putString(MediaStore.EXTRA_MEDIA_TITLE, it) }
}

/** L'inverse de [toExtras]. */
internal fun voiceRequestOf(extras: Bundle): VoiceRequest =
    voiceRequestOf(extras.getString(SearchManager.QUERY).orEmpty(), extras)

/** La phrase et les compléments de `MediaStore`, détachés du `Bundle`. */
internal fun voiceRequestOf(query: String, extras: Bundle?): VoiceRequest = VoiceRequest(
    query = query,
    focus = when (extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)) {
        MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> VoiceFocus.Album
        MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> VoiceFocus.Artist
        MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> VoiceFocus.Song
        else -> VoiceFocus.Unspecified
    },
    album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM),
    artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST),
    title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE),
)

private fun VoiceFocus.contentType(): String? = when (this) {
    VoiceFocus.Album -> MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE
    VoiceFocus.Artist -> MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE
    VoiceFocus.Song -> MediaStore.Audio.Media.ENTRY_CONTENT_TYPE
    VoiceFocus.Unspecified -> null
}
