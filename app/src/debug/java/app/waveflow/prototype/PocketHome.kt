package app.waveflow.prototype

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.waveflow.model.Album
import app.waveflow.model.Library
import app.waveflow.model.Song
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.home.HomeUiState
import app.waveflow.ui.home.greetingFor
import app.waveflow.ui.home.nextGreetingChange
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.trackCountLabel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

/** Ce que l'accueil sait faire : tout passe par le lecteur et la bibliothèque réels. */
data class HomeActions(
    val onPlay: (queue: List<Song>, song: Song) -> Unit,
    val onShuffle: (List<Song>) -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onOpenAlbum: (Long) -> Unit,
    val onOpenCategory: (LibraryCategory) -> Unit,
    val onAnotherSuggestion: () -> Unit,
)

/**
 * L'accueil de Pocket : dire tout de suite quoi écouter, même avec trois
 * albums (`docs/design/sonic-editorial.md`).
 *
 * De haut en bas : le salut, la seule ligne éditoriale de l'application ; la
 * reprise de la dernière écoute, sur la nappe de sa pochette — ou, sans
 * historique, de quoi commencer ; la suggestion du jour (#104) ; les accès
 * rapides vers la bibliothèque ; les ajouts récents ; les albums écoutés
 * récemment. Chaque bloc ne paraît que s'il a de vraies données : rien n'est
 * inventé, et une bibliothèque vide le dit.
 */
@Composable
fun PocketHome(
    state: HomeUiState,
    library: Library,
    player: PlayerUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // Sur une tablette, l'accueil garde une colonne de lecture : une reprise
        // étirée sur 1 280 dp laisse un grand vide entre la pochette et le disque.
        val largeur = minOf(maxWidth, HOME_MAX_WIDTH)
        val gouttiere = if (largeur < NARROW_WIDTH) 20.dp else 24.dp
        val tuile = carouselTileWidth(largeur, gouttiere)
        // La reprise montre déjà son album : il ne revient pas dans la rangée.
        val ecoutes = remember(state.resumeAlbum, state.recentlyPlayed, library) {
            recentAlbums(state.recentlyPlayed, library).filterNot { it.id == state.resumeAlbum?.id }
        }

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
                    modifier = Modifier.padding(horizontal = gouttiere),
                )
            }

            when {
                state.isLoading -> item(key = "attente") {
                    EmptyState("Lecture de la bibliothèque…", "Un instant : l'appareil compte ses morceaux.")
                }
                library.isEmpty -> item(key = "vide") {
                    EmptyState(
                        title = "Aucun morceau sur l'appareil.",
                        detail = "Copiez de la musique dans le dossier Musique du téléphone : WaveFlow la trouvera.",
                    )
                }
                else -> {
                    item(key = "reprise") {
                        val reprise = state.resume
                        if (reprise != null) {
                            ResumeCard(
                                song = reprise,
                                album = state.resumeAlbum,
                                playing = player.isPlaying && player.track?.localSongId == reprise.id,
                                onPlay = {
                                    if (player.track?.localSongId == reprise.id) {
                                        actions.onTogglePlayPause()
                                    } else {
                                        val file =
                                            state.resumeAlbum?.let { library.songsOfAlbum(it.id) } ?: library.songs
                                        actions.onPlay(file, reprise)
                                    }
                                },
                                onOpenAlbum = { state.resumeAlbum?.let { actions.onOpenAlbum(it.id) } },
                                modifier = Modifier.padding(horizontal = gouttiere),
                            )
                        } else {
                            StartCard(
                                onShuffle = { actions.onShuffle(library.songs) },
                                modifier = Modifier.padding(horizontal = gouttiere),
                            )
                        }
                    }

                    item(key = "acces") {
                        QuickAccess(gouttiere = gouttiere, onOpen = actions.onOpenCategory)
                    }

                    state.suggestion?.let { bande ->
                        item(key = "suggestion") {
                            Column(Modifier.padding(horizontal = gouttiere)) {
                                SectionTitle(
                                    text = "Suggestion du jour",
                                    modifier = Modifier.semantics { heading() },
                                    action = {
                                        PocketPill(
                                            text = "Autre",
                                            icon = Icons.Rounded.Refresh,
                                            compact = true,
                                            onClick = actions.onAnotherSuggestion,
                                            description = "Autre suggestion",
                                        )
                                    },
                                )
                                Text(
                                    text = listOfNotNull(bande.kindLabel, bande.reason).joinToString(" — "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Pocket.colors.ink3,
                                )
                            }
                            TrackRow(
                                title = bande.song.title,
                                subtitle = bande.song.displayArtist,
                                durationMs = bande.song.durationMs,
                                playing = player.track?.localSongId == bande.song.id,
                                artworkUri = bande.song.artworkUri,
                                onClick = { actions.onPlay(library.songs, bande.song) },
                                horizontalPadding = gouttiere,
                            )
                        }
                    }

                    if (state.recentlyAdded.isNotEmpty()) {
                        item(key = "ajouts") {
                            AlbumShelf(
                                title = "Ajouts récents",
                                albums = state.recentlyAdded,
                                gouttiere = gouttiere,
                                tuile = tuile,
                                onOpen = actions.onOpenAlbum,
                                onSeeAll = { actions.onOpenCategory(LibraryCategory.Albums) },
                            )
                        }
                    }

                    if (ecoutes.isNotEmpty()) {
                        item(key = "ecoutes") {
                            AlbumShelf(
                                title = "Écoutés récemment",
                                albums = ecoutes,
                                gouttiere = gouttiere,
                                tuile = tuile,
                                onOpen = actions.onOpenAlbum,
                                onSeeAll = null,
                            )
                        }
                    }
                }
            }
        }
    }
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
 * Le salut, en Playfair italique — la seule ligne éditoriale de
 * l'application —, et ce que contient l'appareil, en une ligne discrète. Il
 * change de lui-même à l'heure du palier suivant, comme celui de l'accueil
 * de production (`nextGreetingChange`).
 */
@Composable
private fun Greeting(songCount: Int, albumCount: Int, modifier: Modifier = Modifier) {
    var maintenant by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(maintenant) {
        delay(Duration.between(maintenant, nextGreetingChange(maintenant)).toMillis().coerceAtLeast(1L))
        maintenant = LocalDateTime.now()
    }
    Column(modifier.padding(top = 24.dp, bottom = 16.dp)) {
        Text(
            text = greetingFor(maintenant.hour) + ".",
            style = PocketEditorial,
            color = Pocket.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (songCount > 0) {
            Text(
                text = "${trackCountLabel(songCount)} · ${albumCountLabel(albumCount)} sur l'appareil",
                style = MaterialTheme.typography.bodySmall,
                color = Pocket.colors.ink3,
            )
        }
    }
}

/**
 * La reprise : la dernière écoute réelle, sa pochette sur sa nappe — l'une
 * des trois atmosphères de l'application. Le disque relance ; le reste de la
 * carte ouvre l'album.
 */
@Composable
private fun ResumeCard(
    song: Song,
    album: Album?,
    playing: Boolean,
    onPlay: () -> Unit,
    onOpenAlbum: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Pocket.colors
    val nappe = rememberAtmosphere(song.artworkUri)
    Surface(
        shape = PocketShapes.surface,
        color = colors.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
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
                .clickable(enabled = album != null, onClickLabel = "Ouvrir l'album", onClick = onOpenAlbum)
                .padding(12.dp),
        ) {
            // À police agrandie, la pochette cède de la place au titre, qui se
            // coupait sinon au milieu d'un mot.
            val cote = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) RESUME_COVER_LARGE_FONT else RESUME_COVER
            Artwork(song.artworkUri, shape = PocketShapes.tile, modifier = Modifier.size(cote))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("REPRENDRE", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                Spacer(Modifier.height(2.dp))
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(song.displayArtist, album?.title).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            PlayButton(playing = playing, buffering = false, onClick = onPlay, size = 48.dp)
        }
    }
}

/** Sans historique : de quoi commencer, jamais une écoute inventée. */
@Composable
private fun StartCard(onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    // Sur la page et non sur une surface : la pastille tonale y garde son fond.
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Votre musique est là.", style = MaterialTheme.typography.titleLarge, color = Pocket.colors.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Rien n'a encore été écouté : lancez tout au hasard, ou partez d'une catégorie ci-dessous.",
            style = MaterialTheme.typography.bodyMedium,
            color = Pocket.colors.ink2,
        )
        Spacer(Modifier.height(12.dp))
        // Une seule action : les catégories sont juste en dessous, dans les
        // accès rapides — deux boutons « Albums » à trois lignes d'écart.
        PrimaryPill(text = "Au hasard", icon = Icons.Rounded.Shuffle, onClick = onShuffle)
    }
}

/** Les accès rapides : les catégories de la bibliothèque, en pastilles — pas en cartes de statistiques. */
@Composable
private fun QuickAccess(gouttiere: androidx.compose.ui.unit.Dp, onOpen: (LibraryCategory) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = gouttiere, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(LibraryCategory.entries, key = { it.name }) { categorie ->
            PocketPill(text = categorie.label, icon = categorie.icon, onClick = { onOpen(categorie) })
        }
    }
}

@Composable
private fun AlbumShelf(
    title: String,
    albums: List<Album>,
    gouttiere: androidx.compose.ui.unit.Dp,
    tuile: androidx.compose.ui.unit.Dp,
    onOpen: (Long) -> Unit,
    onSeeAll: (() -> Unit)?,
) {
    Column(Modifier.padding(top = 8.dp)) {
        SectionTitle(
            text = title,
            modifier = Modifier
                .padding(horizontal = gouttiere)
                .semantics { heading() },
            action = onSeeAll?.let { voir ->
                { PocketPill(text = "Tout voir", compact = true, onClick = voir, description = "Voir tous les albums") }
            },
        )
        TileCarousel(items = albums, key = { it.id }, gutter = gouttiere, tileWidth = tuile) { album, m ->
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

/** Les catégories de la bibliothèque, dans l'ordre du sélecteur. */
enum class LibraryCategory(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Albums("Albums", Icons.Rounded.Album),
    Artistes("Artistes", Icons.Rounded.Person),
    Titres("Titres", Icons.Rounded.LibraryMusic),
    Playlists("Playlists", Icons.AutoMirrored.Rounded.QueueMusic),
}

private val RESUME_COVER = 104.dp
private val RESUME_COVER_LARGE_FONT = 72.dp
private const val LARGE_FONT_SCALE = 1.3f

/** La colonne de l'accueil, au plus. */
private val HOME_MAX_WIDTH = 840.dp
private const val CARD_POOL_RADIUS = 0.75f

/** Sous cette largeur, la gouttière passe à 20 dp. */
val NARROW_WIDTH = 400.dp
