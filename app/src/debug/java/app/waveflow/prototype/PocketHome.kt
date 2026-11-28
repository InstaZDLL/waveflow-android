package app.waveflow.prototype

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.waveflow.model.Album
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.model.orUnknownArtist
import app.waveflow.playback.PlayingTrack
import app.waveflow.suggestion.SorteDeSuggestion
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.components.WaveMark
import app.waveflow.ui.home.HomeUiState
import app.waveflow.ui.home.SuggestionBand
import app.waveflow.ui.home.greetingFor
import app.waveflow.ui.home.nextGreetingChange
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.player.rememberGlidingProgress
import app.waveflow.ui.trackCountLabel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * L'accueil de Pocket : dire tout de suite quoi écouter, même avec trois
 * albums (`docs/design/sonic-editorial.md`).
 *
 * De haut en bas :
 * - le salut, la seule ligne éditoriale de l'application, souligné de
 *   l'onde — la signature qu'on reconnaît d'un regard —, et l'entrée des
 *   réglages ;
 * - **ce qui joue** (« En écoute », sa progression, un appui ouvre le
 *   lecteur), ou, rien ne jouant, **la reprise** de la dernière écoute ; sans
 *   historique, de quoi commencer — les deux ne se confondent jamais ;
 * - les étagères de la bibliothèque, chacune avec ce qu'elle contient ;
 * - la suggestion du jour (#104), en vedette, sa pochette en tête ;
 * - les ajouts récents, puis les albums écoutés récemment.
 *
 * Chaque bloc ne paraît que s'il a de vraies données, et une bibliothèque vide
 * le dit.
 *
 * @param playlistCount le nombre de playlists, `null` tant qu'il n'est pas lu.
 * @param playbackShown le panneau de lecture d'une fenêtre étendue montre déjà
 *   le morceau : l'accueil ne le répète pas à côté.
 */
@Composable
fun PocketHome(
    state: HomeUiState,
    library: Library,
    player: PlayerUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    playlistCount: Int? = null,
    playbackShown: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // Sur une tablette, l'accueil garde une colonne de lecture : une reprise
        // étirée sur 1 280 dp laisse un grand vide entre la pochette et le disque.
        val largeur = minOf(maxWidth, HOME_MAX_WIDTH)
        val gouttiere = if (largeur < NARROW_WIDTH) 20.dp else 24.dp
        val mise = HomeLayout(
            gouttiere = gouttiere,
            tuile = carouselTileWidth(largeur, gouttiere),
            playlistCount = playlistCount,
            playbackShown = playbackShown,
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .widthIn(max = HOME_MAX_WIDTH)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            item(key = "salut") {
                Greeting(
                    songCount = state.songCount,
                    albumCount = state.albumCount,
                    onSettings = actions.onSettings,
                    modifier = Modifier.padding(start = gouttiere, end = gouttiere - 12.dp),
                )
            }
            when {
                state.isLoading -> item(key = "attente") {
                    EmptyState("Lecture de la bibliothèque…", "Un instant : l'appareil compte ses morceaux.")
                }
                library.errorMessage != null -> item(key = "erreur") {
                    EmptyState(title = "La bibliothèque n'a pas pu être lue.", detail = library.errorMessage.orEmpty())
                }
                library.isEmpty -> item(key = "vide") {
                    EmptyState(
                        title = "Aucun morceau sur l'appareil.",
                        detail = "Copiez de la musique dans le dossier Musique du téléphone : WaveFlow la trouvera.",
                    )
                }
                else -> homeContent(state, library, player, actions, mise)
            }
        }
    }
}

/**
 * Ce que l'accueil reçoit en plus de son état : ses mesures, prises une fois
 * sur la largeur, le nombre de playlists ([playlistCount], nul tant qu'il n'est
 * pas lu) et si le panneau de lecture montre déjà ce qui joue
 * ([playbackShown]).
 */
private class HomeLayout(
    val gouttiere: Dp,
    val tuile: Dp,
    val playlistCount: Int?,
    val playbackShown: Boolean,
)

private fun LazyListScope.homeContent(
    state: HomeUiState,
    library: Library,
    player: PlayerUiState,
    actions: HomeActions,
    mise: HomeLayout,
) {
    val gouttiere = mise.gouttiere
    if (!mise.playbackShown) {
        item(key = "lecture") {
            ListeningModule(state, library, player, actions, Modifier.padding(horizontal = gouttiere))
        }
    }
    item(key = "etageres") {
        Shelves(library, state, mise.playlistCount, gouttiere, actions.onOpenCategory)
    }
    state.suggestion?.let { bande ->
        item(key = "suggestion") {
            Suggestion(
                band = bande,
                playing = player.isPlaying && player.track?.localSongId == bande.song.id,
                // Déjà dans le lecteur, en lecture ou en pause : le disque met en
                // pause ou reprend, sans relancer la bibliothèque depuis le début
                // du morceau.
                onPlay = if (player.track?.localSongId == bande.song.id) {
                    actions.onTogglePlayPause
                } else {
                    { actions.onPlay(library.songs, bande.song) }
                },
                onAnother = actions.onAnotherSuggestion,
                modifier = Modifier.padding(horizontal = gouttiere),
            )
        }
    }
    if (state.recentlyAdded.isNotEmpty()) {
        item(key = "ajouts") {
            AlbumShelf(
                title = "Ajouts récents",
                albums = state.recentlyAdded,
                mise = mise,
                onOpen = actions.onOpenAlbum,
                onSeeAll = { actions.onOpenCategory(LibraryCategory.Albums) },
            )
        }
    }
    // La reprise, quand elle est à l'écran, montre déjà son album : il ne
    // revient pas dans la rangée. Un morceau chargé ou le panneau la cachent :
    // l'album y reprend sa place.
    val repriseAffichee = player.track == null && !mise.playbackShown
    val ecoutes = recentAlbums(state.recentlyPlayed, library)
        .filterNot { repriseAffichee && it.id == state.resumeAlbum?.id }
    if (ecoutes.isNotEmpty()) {
        item(key = "ecoutes") {
            AlbumShelf(
                title = "Écoutés récemment",
                albums = ecoutes,
                mise = mise,
                onOpen = actions.onOpenAlbum,
                onSeeAll = null,
            )
        }
    }
}

/**
 * Le module de lecture : ce qui joue, ou ce qu'on peut reprendre, ou de quoi
 * commencer. **En écoute** dit le morceau que le lecteur tient — en lecture ou
 * en pause — et un appui ouvre le lecteur ; **Reprendre** dit la dernière
 * écoute de l'historique quand rien n'est chargé, et la relance dans son album.
 */
@Composable
private fun ListeningModule(
    state: HomeUiState,
    library: Library,
    player: PlayerUiState,
    actions: HomeActions,
    modifier: Modifier,
) {
    val enCours = player.track
    val reprise = state.resume
    when {
        enCours != null -> ListeningCard(
            kicker = "EN ÉCOUTE",
            title = enCours.title,
            subtitle = listOfNotNull(enCours.artist.orUnknownArtist(), enCours.album).joinToString(" · "),
            artworkUri = enCours.artworkUri,
            playing = player.isPlaying,
            buffering = player.isBuffering,
            onPlay = actions.onTogglePlayPause,
            onOpen = actions.onExpandPlayer,
            openLabel = "Ouvrir le lecteur",
            progress = { NowProgress(player, enCours) },
            modifier = modifier,
        )
        reprise != null -> ListeningCard(
            kicker = "REPRENDRE",
            title = reprise.title,
            subtitle = listOfNotNull(reprise.displayArtist, state.resumeAlbum?.title).joinToString(" · "),
            artworkUri = reprise.artworkUri,
            playing = false,
            buffering = false,
            onPlay = {
                // L'album se relit sur le morceau tel que la bibliothèque le
                // connaît maintenant : un rechargement peut l'avoir déplacé.
                val actuel = library.songsById[reprise.id]
                if (actuel != null) actions.onPlay(library.songsOfAlbum(actuel.albumId), actuel)
            },
            onOpen = state.resumeAlbum?.let { album -> { actions.onOpenAlbum(album.id) } },
            openLabel = "Ouvrir l'album",
            progress = null,
            modifier = modifier,
        )
        else -> StartCard(onShuffle = { actions.onShuffle(library.songs) }, modifier = modifier)
    }
}

/** La progression du morceau qui joue, au pied de sa carte : le bord du dock, rapporté ici. */
@Composable
private fun NowProgress(player: PlayerUiState, track: PlayingTrack) {
    val avance by rememberGlidingProgress(player.positionMs, player.durationMs, track.mediaId, player.isPlaying)
    CompactSillage(progress = { avance }, modifier = Modifier.fillMaxWidth().height(6.dp))
}

/**
 * Les albums des écoutes [songs], dans l'ordre où on les a écoutés, chacun
 * une fois. Ceux qui ne sont plus dans la bibliothèque sont écartés.
 */
fun recentAlbums(songs: List<Song>, library: Library, limit: Int = RECENT_ALBUMS): List<Album> =
    songs.asSequence()
        .map { it.albumId }
        .distinct()
        .mapNotNull(library::album)
        .take(limit)
        .toList()

private const val RECENT_ALBUMS = 10

/**
 * La raison d'une suggestion, en une ligne courte. « Jamais écouté » n'a pas
 * besoin de la date longue que le bureau écrit (« Dans votre bibliothèque depuis
 * le 28 novembre 2026, toujours pas écouté ») : « ajouté le 28 nov. » suffit,
 * à côté de sa sorte. Les autres raisons sont déjà courtes, et restent celles
 * du bureau.
 */
internal fun shortReason(band: SuggestionBand, zone: ZoneId = ZoneId.systemDefault()): String? =
    when {
        band.kind == SorteDeSuggestion.JamaisEcoutee && band.song.addedAtMs > 0L ->
            // Insécables : « 28 nov. » ne se coupe pas en fin de ligne.
            "ajouté le " + SHORT_DATE.format(Instant.ofEpochMilli(band.song.addedAtMs).atZone(zone)).replace(' ', NBSP)
        band.kind == SorteDeSuggestion.JamaisEcoutee -> null
        else -> band.reason
    }

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
private const val NBSP = '\u00A0'

/**
 * Le salut, en Playfair italique — la seule ligne éditoriale de
 * l'application —, souligné de l'onde de WaveFlow, et ce que contient
 * l'appareil. Il change de lui-même à l'heure du palier suivant, comme celui
 * de l'accueil de production (`nextGreetingChange`).
 *
 * @param onSettings l'entrée des réglages ; nulle quand le rail la porte.
 */
@Composable
private fun Greeting(songCount: Int, albumCount: Int, onSettings: (() -> Unit)?, modifier: Modifier = Modifier) {
    var maintenant by remember { mutableStateOf(LocalDateTime.now()) }
    // Relu au retour au premier plan : l'heure a pu changer — un fuseau, un
    // réglage — pendant que l'écran dormait.
    val cycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(cycle) {
        cycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { maintenant = LocalDateTime.now() }
    }
    LaunchedEffect(maintenant) {
        delay(Duration.between(maintenant, nextGreetingChange(maintenant)).toMillis().coerceAtLeast(1L))
        maintenant = LocalDateTime.now()
    }
    Row(verticalAlignment = Alignment.Top, modifier = modifier.padding(top = 20.dp, bottom = 16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                text = greetingFor(maintenant.hour) + ".",
                style = PocketEditorial,
                color = Pocket.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            WaveMark(modifier = Modifier.size(GREETING_MARK_WIDTH, GREETING_MARK_HEIGHT), color = Pocket.colors.accent)
            if (songCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${trackCountLabel(songCount)} · ${albumCountLabel(albumCount)} sur l'appareil",
                    style = MaterialTheme.typography.bodySmall,
                    color = Pocket.colors.ink3,
                )
            }
        }
        onSettings?.let { SettingsButton(it) }
    }
}

/**
 * La carte de lecture : la pochette sur sa nappe — l'une des trois atmosphères
 * de l'application —, le titre, le disque. Le disque joue ou met en pause ; le
 * reste de la carte ouvre ([onOpen]) le lecteur ou l'album.
 */
@Composable
private fun ListeningCard(
    kicker: String,
    title: String,
    subtitle: String,
    artworkUri: Uri?,
    playing: Boolean,
    buffering: Boolean,
    onPlay: () -> Unit,
    onOpen: (() -> Unit)?,
    openLabel: String,
    progress: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = Pocket.colors
    val nappe = rememberAtmosphere(artworkUri)
    Surface(shape = PocketShapes.surface, color = colors.surface, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .drawBehind {
                    // La nappe naît derrière la pochette et s'éteint avant le
                    // disque de lecture.
                    drawRect(
                        Brush.radialGradient(
                            0f to nappe.copy(alpha = POOL_ALPHA),
                            1f to Color.Transparent,
                            center = Offset(size.height / 2f, size.height / 2f),
                            radius = size.width * CARD_POOL_RADIUS,
                        ),
                    )
                }
                .clickable(enabled = onOpen != null, onClickLabel = openLabel) { onOpen?.invoke() },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                // À police agrandie, la pochette cède de la place au titre, qui se
                // coupait sinon au milieu d'un mot.
                val cote = if (LocalDensity.current.fontScale >
                    LARGE_FONT_SCALE
                ) {
                    RESUME_COVER_LARGE_FONT
                } else {
                    RESUME_COVER
                }
                Artwork(artworkUri, shape = PocketShapes.tile, modifier = Modifier.size(cote))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(kicker, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                PlayButton(playing = playing, buffering = buffering, onClick = onPlay, size = 48.dp)
            }
            progress?.invoke()
        }
    }
}

/** Sans historique : de quoi commencer, jamais une écoute inventée. */
@Composable
private fun StartCard(onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    // Sur la page et non sur une surface : la pastille tonale y garde son fond.
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Votre musique est là.", style = MaterialTheme.typography.titleLarge, color = Pocket.colors.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Rien n'a encore été écouté : lancez tout au hasard, ou partez d'une étagère ci-dessous.",
            style = MaterialTheme.typography.bodyMedium,
            color = Pocket.colors.ink2,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryPill(text = "Au hasard", icon = Icons.Rounded.Shuffle, onClick = onShuffle)
    }
}

/**
 * Les étagères : les catégories de la bibliothèque, chacune montrée par ce
 * qu'elle contient — trois pochettes en éventail, les disques de trois
 * artistes — et comptée. Pas des pastilles génériques, pas des cartes de
 * statistiques : un coup d'œil sur ce qu'on a.
 */
@Composable
private fun Shelves(
    library: Library,
    state: HomeUiState,
    playlistCount: Int?,
    gouttiere: Dp,
    onOpen: (LibraryCategory) -> Unit,
) {
    val etageres = remember(library, state.recentlyAdded, playlistCount) {
        listOf(
            Shelf(
                LibraryCategory.Albums,
                albumCountLabel(library.albums.size),
                state.recentlyAdded.map {
                    it.artworkUri
                },
            ),
            Shelf(
                LibraryCategory.Artistes,
                "${library.artists.size} ${if (library.artists.size > 1) "artistes" else "artiste"}",
                emptyList(),
                library.artists.take(SHELF_PREVIEW).map { it.name to it.artworkUri },
            ),
            Shelf(
                LibraryCategory.Titres,
                trackCountLabel(library.songs.size),
                library.songs.map {
                    it.artworkUri
                }.distinct(),
            ),
            Shelf(LibraryCategory.Playlists, playlistCount?.let { "$it ${if (it > 1) "playlists" else "playlist"}" }),
        )
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = gouttiere, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(etageres, key = {
            it.category.name
        }) { etagere -> ShelfItem(etagere, onClick = { onOpen(etagere.category) }) }
    }
}

/** Une étagère : sa catégorie, son compte, et ce qui la montre. */
private class Shelf(
    val category: LibraryCategory,
    val count: String?,
    covers: List<Uri?> = emptyList(),
    val portraits: List<Pair<String, Uri?>> = emptyList(),
) {
    val covers: List<Uri?> = covers.take(SHELF_PREVIEW)
}

@Composable
private fun ShelfItem(shelf: Shelf, onClick: () -> Unit) {
    val colors = Pocket.colors
    Surface(
        onClick = onClick,
        shape = PocketShapes.surface,
        color = colors.surface,
        modifier = Modifier.semantics { this.role = Role.Button },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            ShelfPreview(shelf)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    shelf.category.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink,
                    maxLines = 1,
                )
                shelf.count?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = colors.ink3, maxLines = 1)
                }
            }
        }
    }
}

/** Trois pochettes ou trois disques qui se chevauchent ; l'icône de la catégorie quand il n'y a rien à montrer. */
@Composable
private fun ShelfPreview(shelf: Shelf) {
    val colors = Pocket.colors
    val nombre = maxOf(shelf.covers.size, shelf.portraits.size)
    if (nombre == 0) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(SHELF_THUMB).clip(PocketShapes.thumbnail).background(colors.raised),
        ) {
            Icon(shelf.category.icon, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(18.dp))
        }
        return
    }
    Box(Modifier.width(SHELF_THUMB + SHELF_OVERLAP * (nombre - 1)).height(SHELF_THUMB)) {
        shelf.covers.forEachIndexed { i, uri ->
            Artwork(
                uri,
                shape = PocketShapes.thumbnail,
                modifier = Modifier
                    .offset(x = SHELF_OVERLAP * i)
                    .size(SHELF_THUMB)
                    .border(1.5.dp, colors.surface, PocketShapes.thumbnail),
            )
        }
        shelf.portraits.forEachIndexed { i, (nom, uri) ->
            ArtistPortrait(
                name = nom,
                artworkUri = uri,
                size = SHELF_THUMB,
                modifier = Modifier.offset(x = SHELF_OVERLAP * i).border(1.5.dp, colors.surface, PocketShapes.action),
            )
        }
    }
}

/**
 * La suggestion du jour, en vedette sans être une affiche : sa pochette en
 * tête, ombrée de sa lumière ; la sorte et une raison courte ; le titre et
 * l'artiste ; le disque pour l'écouter ; « Autre » pour en tirer une autre.
 */
@Composable
private fun Suggestion(
    band: SuggestionBand,
    playing: Boolean,
    onPlay: () -> Unit,
    onAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Pocket.colors
    val nappe = rememberAtmosphere(band.song.artworkUri)
    Column(modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SUGGESTION DU JOUR",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onAnother) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Autre suggestion", tint = colors.ink2)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                band.song.artworkUri,
                shape = PocketShapes.tile,
                modifier = Modifier
                    .size(SUGGESTION_COVER)
                    .shadow(16.dp, PocketShapes.tile, ambientColor = nappe, spotColor = nappe)
                    .clip(PocketShapes.tile),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(band.kindLabel, shortReason(band)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    band.song.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    band.song.displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink2,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            PlayButton(playing = playing, buffering = false, onClick = onPlay, size = 44.dp)
        }
    }
}

@Composable
private fun AlbumShelf(
    title: String,
    albums: List<Album>,
    mise: HomeLayout,
    onOpen: (Long) -> Unit,
    onSeeAll: (() -> Unit)?,
) {
    Column(Modifier.padding(top = 12.dp)) {
        SectionTitle(
            text = title,
            modifier = Modifier
                .padding(horizontal = mise.gouttiere)
                .semantics { heading() },
            action = onSeeAll?.let { voir ->
                { PocketPill(text = "Tout voir", compact = true, onClick = voir, description = "Voir tous les albums") }
            },
        )
        TileCarousel(items = albums, key = { it.id }, gutter = mise.gouttiere, tileWidth = mise.tuile) { album, m ->
            AlbumTile(
                title = album.title,
                subtitle = album.displayArtist,
                artworkUri = album.artworkUri,
                onClick = { onOpen(album.id) },
                modifier = m,
            )
        }
    }
}

private val RESUME_COVER = 96.dp
private val RESUME_COVER_LARGE_FONT = 64.dp
private const val LARGE_FONT_SCALE = 1.3f
private val SUGGESTION_COVER = 88.dp
private val SHELF_THUMB = 32.dp
private val SHELF_OVERLAP = 14.dp
private const val SHELF_PREVIEW = 3
private val GREETING_MARK_WIDTH = 36.dp
private val GREETING_MARK_HEIGHT = 9.dp

/** La colonne de l'accueil, au plus. */
private val HOME_MAX_WIDTH = 840.dp
private const val CARD_POOL_RADIUS = 0.75f

/** Sous cette largeur, la gouttière passe à 20 dp. */
val NARROW_WIDTH = 400.dp
