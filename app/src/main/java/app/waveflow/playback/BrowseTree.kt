package app.waveflow.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.waveflow.model.Library
import app.waveflow.model.Playlist
import app.waveflow.model.PlaylistEntry
import app.waveflow.model.Song

/**
 * Ce qu'un hôte extérieur — Android Auto, Assistant — voit de la bibliothèque.
 *
 * L'arbre ne sert que la bibliothèque de l'appareil. Le catalogue du serveur en
 * est absent à dessein : il se demande par le réseau, page par page, et une
 * branche qui met deux secondes à répondre ou qui se vide hors couverture est
 * pire, au volant, qu'une branche qui n'existe pas. Le local, lui, est déjà en
 * mémoire quand la question arrive.
 *
 * Les identifiants de navigation portent le préfixe [BROWSE_PREFIX], que rien
 * d'autre n'emploie : une feuille garde le `mediaId` que [toMediaItem] lui
 * donne, si bien qu'un nœud et une piste ne peuvent pas se confondre.
 */
class BrowseTree(private val snapshot: () -> BrowseSnapshot) {

    /**
     * La racine, seule réponse possible tant que la bibliothèque n'est pas lue.
     *
     * Android Auto la demande avant tout le reste et n'attend pas : rendre ici
     * un nœud vide au motif que le chargement n'est pas fini afficherait une
     * voiture sans musique jusqu'au prochain balayage.
     */
    fun root(): MediaItem = browsableNode(ROOT_ID, "Bibliothèque")

    /**
     * Les enfants de [parentId], ou une liste vide si le nœud n'existe pas.
     *
     * Une liste vide est aussi ce que rend un nœud réel mais sans contenu — un
     * album dont les fichiers ont disparu, par exemple. L'hôte ne distingue pas
     * les deux, et n'a pas à le faire : dans les deux cas il n'y a rien à
     * montrer.
     */
    fun children(parentId: String): List<MediaItem> {
        val state = snapshot()

        return when {
            parentId == ROOT_ID -> rootSections(state)
            parentId == ALBUMS_ID -> state.library.albums.map(::albumNode)
            parentId == ARTISTS_ID -> state.library.artists.map(::artistNode)
            parentId == PLAYLISTS_ID -> state.playlists.map(::playlistNode)
            parentId == SONGS_ID -> state.library.songs.map { it.toBrowsableLeaf() }
            parentId.startsWith(ALBUM_PREFIX) -> state.songsOfAlbum(parentId.idAfter(ALBUM_PREFIX))
            parentId.startsWith(ARTIST_PREFIX) -> state.songsOfArtist(parentId.idAfter(ARTIST_PREFIX))
            parentId.startsWith(PLAYLIST_PREFIX) ->
                state.songsOfPlaylist(parentId.idAfter(PLAYLIST_PREFIX))
            else -> emptyList()
        }
    }

    /** Le nœud ou la feuille que désigne [mediaId], `null` s'il n'existe pas. */
    fun item(mediaId: String): MediaItem? {
        if (mediaId == ROOT_ID) return root()

        val state = snapshot()
        return rootSections(state).firstOrNull { it.mediaId == mediaId }
            ?: state.library.albums.firstOrNull { albumId(it.id) == mediaId }?.let(::albumNode)
            ?: state.library.artists.firstOrNull { artistId(it.id) == mediaId }?.let(::artistNode)
            ?: state.playlists.firstOrNull { playlistId(it.id) == mediaId }?.let(::playlistNode)
            ?: state.songOf(mediaId)?.toBrowsableLeaf()
    }

    /**
     * Ce qu'il faut mettre dans le lecteur quand l'hôte demande [mediaId].
     *
     * Les [MediaItem] rendus par [children] n'ont **pas** d'URI : l'hôte les
     * affiche, il ne les ouvre pas. C'est ici que la piste retrouve la sienne,
     * par [toMediaItem] — le même chemin que l'application elle-même, donc les
     * mêmes clés de cache et le même résolveur.
     *
     * Un nœud est jouable comme ses feuilles : demander un album, c'est
     * demander ses pistes dans l'ordre.
     */
    fun resolve(mediaId: String): List<MediaItem> {
        val state = snapshot()

        state.songOf(mediaId)?.let { return listOf(it.toMediaItem()) }

        return children(mediaId).mapNotNull { state.songOf(it.mediaId)?.toMediaItem() }
    }

    private fun rootSections(state: BrowseSnapshot): List<MediaItem> = buildList {
        add(browsableNode(ALBUMS_ID, "Albums"))
        add(browsableNode(ARTISTS_ID, "Artistes"))
        // Une section de playlists vide n'apprend rien et occupe une place que
        // la voiture n'a pas.
        if (state.playlists.isNotEmpty()) add(browsableNode(PLAYLISTS_ID, "Playlists"))
        add(browsableNode(SONGS_ID, "Toutes les pistes"))
    }

    private fun albumNode(album: app.waveflow.model.Album): MediaItem = browsableNode(
        mediaId = albumId(album.id),
        title = album.title,
        subtitle = album.displayArtist,
        artworkUri = album.artworkUri,
        mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
    )

    private fun artistNode(artist: app.waveflow.model.Artist): MediaItem = browsableNode(
        mediaId = artistId(artist.id),
        title = artist.name,
        artworkUri = artist.artworkUri,
        mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
    )

    private fun playlistNode(playlist: Playlist): MediaItem = browsableNode(
        mediaId = playlistId(playlist.id),
        title = playlist.name,
        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
    )

    private fun browsableNode(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artworkUri: android.net.Uri? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artworkUri)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType)
                .build(),
        )
        .build()

    /**
     * Une piste telle que l'hôte la liste : sans URI.
     *
     * Elle porte le même `mediaId` que l'élément jouable — c'est ce qui permet
     * à [resolve] de la retrouver quand l'hôte la redemande pour la lire.
     */
    private fun Song.toBrowsableLeaf(): MediaItem = MediaItem.Builder()
        .setMediaId(toMediaItem().mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

    private fun BrowseSnapshot.songsOfAlbum(id: Long?): List<MediaItem> =
        library.songs.filter { it.albumId == id }.map { it.toBrowsableLeaf() }

    private fun BrowseSnapshot.songsOfArtist(id: Long?): List<MediaItem> =
        library.songs.filter { it.artistId == id }.map { it.toBrowsableLeaf() }

    /**
     * Les pistes d'une playlist, dans l'ordre voulu.
     *
     * Une entrée dont le fichier a disparu de l'appareil est ignorée : la
     * playlist garde la mémoire d'un morceau que le MediaStore ne connaît plus.
     */
    private fun BrowseSnapshot.songsOfPlaylist(id: Long?): List<MediaItem> = playlistEntries
        .filter { it.playlistId == id }
        .sortedBy(PlaylistEntry::position)
        .mapNotNull { library.songsById[it.songId] }
        .map { it.toBrowsableLeaf() }

    /** Le morceau que désigne ce `mediaId` de feuille, `null` si c'en est un autre. */
    private fun BrowseSnapshot.songOf(mediaId: String): Song? =
        library.songs.firstOrNull { it.toMediaItem().mediaId == mediaId }

    private fun String.idAfter(prefix: String): Long? = removePrefix(prefix).toLongOrNull()

    private fun albumId(id: Long) = "$ALBUM_PREFIX$id"

    private fun artistId(id: Long) = "$ARTIST_PREFIX$id"

    private fun playlistId(id: Long) = "$PLAYLIST_PREFIX$id"

    companion object {
        /**
         * Préfixe des nœuds de navigation.
         *
         * Distinct de ceux des pistes (`local:`, `remote:`) pour qu'aucun nœud
         * ne puisse être pris pour une piste, ni l'inverse.
         */
        const val BROWSE_PREFIX = "browse:"

        const val ROOT_ID = "${BROWSE_PREFIX}root"

        private const val ALBUMS_ID = "${BROWSE_PREFIX}albums"
        private const val ARTISTS_ID = "${BROWSE_PREFIX}artists"
        private const val PLAYLISTS_ID = "${BROWSE_PREFIX}playlists"
        private const val SONGS_ID = "${BROWSE_PREFIX}songs"

        private const val ALBUM_PREFIX = "${BROWSE_PREFIX}album/"
        private const val ARTIST_PREFIX = "${BROWSE_PREFIX}artist/"
        private const val PLAYLIST_PREFIX = "${BROWSE_PREFIX}playlist/"
    }
}

/**
 * L'état de la bibliothèque à l'instant où l'hôte pose sa question.
 *
 * Un instantané plutôt que des flux : [BrowseTree] répond de façon synchrone, et
 * la question vient d'un processus extérieur qui n'attendra pas.
 */
data class BrowseSnapshot(
    val library: Library = Library(),
    val playlists: List<Playlist> = emptyList(),
    val playlistEntries: List<PlaylistEntry> = emptyList(),
)
