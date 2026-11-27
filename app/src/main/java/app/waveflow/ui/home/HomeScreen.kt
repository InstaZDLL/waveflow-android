package app.waveflow.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.waveflow.model.Album
import app.waveflow.model.Song
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.AccentPill
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.components.CoverTile
import app.waveflow.ui.components.Kicker
import app.waveflow.ui.components.MediaRow
import app.waveflow.ui.components.SectionHeader
import app.waveflow.ui.components.cappedWidthFraction
import app.waveflow.ui.components.glassEdge
import app.waveflow.ui.player.boundedGlowAlpha
import app.waveflow.ui.player.rememberPlayerLight
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.WaveFlowTheme
import app.waveflow.ui.trackCountLabel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * La première page : ce que l'appareil sait déjà de vos écoutes.
 *
 * Rien n'y est demandé au réseau — l'écoute passée vient de la base locale, les
 * ajouts récents du MediaStore. L'écran s'affiche donc hors ligne comme
 * connecté, ce qui est la moindre des choses pour la page d'ouverture.
 *
 * Composée comme l'accueil du bureau (`ListeningCanvas`) : un **canevas** en
 * tête — le salut, une ligne de compteurs, puis la dernière écoute en grand
 * titre, sa pilule « Reprendre » et sa pochette —, sans carte autour ; puis la
 * **bande** de la suggestion du jour (`docs/suggestion-du-jour.md`), les ajouts
 * récents en mosaïque de tuiles et les écoutes récentes en lignes.
 *
 * Les sections vides disparaissent au lieu de s'afficher creuses : une
 * bibliothèque neuve n'a pas d'écoutes à montrer, et un cadre vide se lit comme
 * une panne.
 *
 * @param onAnotherSuggestion « Autre suggestion » : un nouveau tirage (S11).
 * @param now l'heure du salut et de la date ; relue à chaque retour de
 *   l'application au premier plan.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAnotherSuggestion: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    now: () -> LocalDateTime = LocalDateTime::now,
) {
    if (state.isEmpty) {
        EmptyHome(modifier)
        return
    }
    // Relue à chaque retour au premier plan, et pas seulement à l'arrivée sur
    // la page : une application laissée en arrière-plan la veille au soir
    // dirait sinon « Bonsoir » et la date d'hier en revenant au matin.
    var maintenant by remember { mutableStateOf(now()) }
    val horloge by rememberUpdatedState(now)
    LifecycleResumeEffect(Unit) {
        maintenant = horloge()
        onPauseOrDispose {}
    }
    // Relue aussi quand le salut ou la date doivent changer alors que la page
    // reste ouverte : l'attente court jusqu'à la prochaine frontière, puis
    // repart de la nouvelle heure. Une heure relue égale à l'ancienne ne
    // relance rien.
    LaunchedEffect(maintenant) {
        delay(Duration.between(maintenant, nextGreetingChange(maintenant)).toMillis())
        val apres = horloge()
        maintenant = apres
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 32.dp),
    ) {
        item(key = "canevas") {
            HomeCanvas(
                state = state,
                now = maintenant,
                onResume = onSongClick,
                onOpenAlbum = onAlbumClick,
            )
        }

        state.suggestion?.let { bande ->
            item(key = "suggestion") {
                // « Écouter » joue comme un appui sur un morceau de l'accueil :
                // la piste, puis la bibliothèque à sa suite (S16).
                SuggestionOfTheDay(
                    band = bande,
                    onPlay = { onSongClick(bande.song) },
                    onAnother = onAnotherSuggestion,
                    modifier = Modifier.padding(start = GUTTER, end = GUTTER, top = SUGGESTION_GAP),
                )
            }
        }

        if (state.recentlyAdded.isNotEmpty()) {
            item(key = "ajouts") {
                Column {
                    Section("Ajouts récents")
                    AddedMosaic(albums = state.recentlyAdded, onAlbumClick = onAlbumClick)
                }
            }
        }

        if (state.recentlyPlayed.isNotEmpty()) {
            item(key = "ecoutes") { Section("Écouté récemment") }
            items(state.recentlyPlayed, key = { it.id }) { piste ->
                MediaRow(
                    artworkUri = piste.artworkUri,
                    title = piste.title,
                    subtitle = piste.displayArtist,
                    onClick = { onSongClick(piste) },
                )
            }
        }
    }
}

/**
 * Le salut du bureau, selon l'heure : « Bonjour » le jour, « Bonsoir » de 18 h
 * à 23 h, « Bonne nuit » ensuite.
 */
internal fun greetingFor(hour: Int): String = when (hour) {
    in MORNING until EVENING -> "Bonjour"
    in EVENING until NIGHT -> "Bonsoir"
    else -> "Bonne nuit"
}

/**
 * Le prochain instant, strictement après [now], où le salut ou la date
 * changent : 5 h, 18 h, 23 h, ou minuit.
 */
internal fun nextGreetingChange(now: LocalDateTime): LocalDateTime {
    val jour = now.toLocalDate()
    return listOf(MORNING, EVENING, NIGHT)
        .map { heure -> jour.atTime(heure, 0) }
        .plus(jour.plusDays(1).atStartOfDay())
        .first { it.isAfter(now) }
}

/** La ligne de compteurs sous le salut : la date, les morceaux, les albums. */
internal fun countersLine(now: LocalDateTime, songCount: Int, albumCount: Int): String =
    listOf(
        DateFormat.format(now),
        trackCountLabel(songCount),
        albumCountLabel(albumCount),
    ).joinToString(" · ")

private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/**
 * La tête de l'accueil, sans carte autour : l'atmosphère de la page en tient
 * lieu.
 *
 * En Fluid, la lumière de la pochette s'étend derrière le texte ; en Liquid,
 * le texte reste sur la page et la lumière se rassemble sous la pochette,
 * posée comme un objet de verre (« no blurred copy of the cover behind it »).
 * Dans les deux cas elle est bornée comme celle du lecteur, pour que les
 * encres restent lisibles par-dessus. C'est la lumière de la pochette, pas son
 * accent : hors du lecteur, l'accent reste celui du thème.
 */
@Composable
private fun HomeCanvas(
    state: HomeUiState,
    now: LocalDateTime,
    onResume: (Song) -> Unit,
    onOpenAlbum: (Album) -> Unit,
) {
    val material = WaveFlowTheme.material
    val ground = MaterialTheme.colorScheme.background
    val glow = rememberPlayerLight(state.resume?.artworkUri).glow

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (material == WaveFlowMaterial.Fluid) {
                    Modifier.canvasLight(glow, boundedGlowAlpha(glow, FLUID_AURA, ground, material))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = GUTTER, vertical = 16.dp),
    ) {
        // La ligne éditoriale de la page, la seule : Playfair italique en
        // sombre, DM Sans légère en clair.
        Text(
            text = greetingFor(now.hour),
            style = WaveFlowTheme.editorial.copy(fontSize = GREETING_SIZE.sp, lineHeight = GREETING_LINE.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!state.isLoading) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = countersLine(now, state.songCount, state.albumCount),
                style = MaterialTheme.typography.labelMedium,
                color = WaveFlowTheme.colors.ink3,
            )
        }

        state.resume?.let { piste ->
            Spacer(Modifier.height(32.dp))
            ResumeBlock(
                song = piste,
                album = state.resumeAlbum,
                onResume = { onResume(piste) },
                onOpenAlbum = onOpenAlbum,
            )
            Spacer(Modifier.height(36.dp))
            CanvasCover(song = piste, glow = glow, ground = ground)
        }
    }
}

/** Surtitre, grand titre, une ligne de méta, la pilule et le lien. */
@Composable
private fun ResumeBlock(
    song: Song,
    album: Album?,
    onResume: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
) {
    Kicker("Reprendre là où vous étiez")
    Spacer(Modifier.height(12.dp))
    Text(
        text = song.title,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(12.dp))
    // L'artiste dans la première encre, l'album dans la seconde ; l'album se
    // tait quand il porte le nom du morceau, comme sur le bureau.
    val nomAlbum = song.album?.takeIf { it.isNotBlank() && it != song.title }
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append(song.displayArtist) }
            if (nomAlbum != null) append("  ·  $nomAlbum")
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(28.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        AccentPill(text = "Reprendre", icon = Icons.Filled.PlayArrow, onClick = onResume)
        if (album != null) {
            TextButton(onClick = { onOpenAlbum(album) }) {
                Text(
                    text = "Voir l'album",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * La pochette du canevas : la plus grande chose de la page, posée sur sa
 * lumière — une ombre teintée en Fluid, une nappe sous l'objet de verre en
 * Liquid.
 */
@Composable
private fun CanvasCover(song: Song, glow: Color, ground: Color) {
    val material = WaveFlowTheme.material
    val shape = WaveFlowTheme.shapes.cover
    val nappe = if (material == WaveFlowMaterial.Liquid) {
        Modifier.coverPool(glow, boundedGlowAlpha(glow, LIQUID_POOL, ground, material))
    } else {
        Modifier
    }
    val bord = when (material) {
        WaveFlowMaterial.Fluid -> Modifier.shadow(COVER_LIFT, shape, ambientColor = glow, spotColor = glow)
        WaveFlowMaterial.Liquid -> Modifier.glassEdge(shape, material, glow, COVER_LIFT)
    }
    Artwork(
        artworkUri = song.artworkUri,
        shape = shape,
        modifier = Modifier
            .cappedWidthFraction(COVER_FRACTION, COVER_MAX)
            .aspectRatio(1f)
            .then(nappe)
            .then(bord),
    )
}

@Composable
private fun Section(title: String) {
    SectionHeader(
        title = title,
        modifier = Modifier.padding(start = GUTTER, end = GUTTER, top = SECTION_GAP, bottom = 16.dp),
    )
}

/**
 * Les derniers albums arrivés : une tuile de tête deux fois plus grande, deux
 * tuiles à côté d'elle, puis une rangée de trois — la grille du bureau
 * (`RecentlyAdded`) ramenée à la largeur d'un téléphone. Toutes les tuiles
 * ont la largeur d'un tiers, la tête celle de deux et de l'intervalle, pour
 * que les colonnes tombent juste d'une rangée à l'autre.
 *
 * Le tiers est plafonné, comme les grandes pochettes : en paysage ou sur une
 * tablette, la tuile de tête dépasserait sinon la hauteur de l'écran. La
 * mosaïque reste alors calée à gauche, à sa taille.
 */
@Composable
private fun AddedMosaic(albums: List<Album>, onAlbumClick: (Album) -> Unit) {
    BoxWithConstraints(modifier = Modifier.padding(horizontal = GUTTER)) {
        val tiers = minOf((maxWidth - MOSAIC_GAP * 2) / 3, MOSAIC_THIRD_MAX)
        val cote = albums.drop(1).take(2)
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (cote.size == 2) {
                val tete = albums.first()
                Row(horizontalArrangement = Arrangement.spacedBy(MOSAIC_GAP)) {
                    CoverTile(
                        artworkUri = tete.artworkUri,
                        title = tete.title,
                        subtitle = tete.displayArtist,
                        onClick = { onAlbumClick(tete) },
                        kicker = "Dernier ajout",
                        lead = true,
                        modifier = Modifier.width(tiers * 2 + MOSAIC_GAP),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        cote.forEach { album -> AlbumThird(album, tiers, onAlbumClick) }
                    }
                }
                ThirdsRow(albums.drop(1 + cote.size), tiers, onAlbumClick)
            } else {
                // Trop peu pour une tête : une rangée simple.
                ThirdsRow(albums, tiers, onAlbumClick)
            }
        }
    }
}

@Composable
private fun ThirdsRow(albums: List<Album>, tiers: Dp, onAlbumClick: (Album) -> Unit) {
    if (albums.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(MOSAIC_GAP)) {
        albums.take(3).forEach { album -> AlbumThird(album, tiers, onAlbumClick) }
    }
}

@Composable
private fun AlbumThird(album: Album, width: Dp, onAlbumClick: (Album) -> Unit) {
    CoverTile(
        artworkUri = album.artworkUri,
        title = album.title,
        subtitle = album.displayArtist,
        onClick = { onAlbumClick(album) },
        modifier = Modifier.width(width),
    )
}

@Composable
private fun EmptyHome(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Column(modifier = Modifier.padding(horizontal = GUTTER)) {
            Kicker("Accueil")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Rien à reprendre pour l'instant",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Écoutez quelques morceaux : ils réapparaîtront ici.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * La lumière de la pochette derrière le texte du canevas (Fluid) : une nappe
 * qui favorise la colonne de texte, comme l'aura du bureau.
 *
 * Son centre descend d'un peu moins que son rayon : elle s'éteint d'elle-même
 * avant le haut du canevas, au lieu d'être tranchée net sous la barre du haut
 * — tranchée, elle se lisait comme le bord d'une carte. Elle s'éteint aussi
 * en douceur, plus vite près du centre qu'au bord.
 */
private fun Modifier.canvasLight(glow: Color, alpha: Float): Modifier = drawBehind {
    val rayon = size.width * AURA_RADIUS
    val centre = Offset(size.width * AURA_X, rayon * AURA_DROP)
    drawCircle(
        brush = Brush.radialGradient(
            0f to glow.copy(alpha = alpha),
            AURA_KNEE to glow.copy(alpha = alpha * AURA_KNEE_ALPHA),
            1f to Color.Transparent,
            center = centre,
            radius = rayon,
        ),
        radius = rayon,
        center = centre,
    )
}

/**
 * La lumière rassemblée sous la pochette (Liquid) : une ellipse plus large
 * que l'objet, qui déborde de ses bords.
 */
private fun Modifier.coverPool(glow: Color, alpha: Float): Modifier = drawBehind {
    val centre = Offset(size.width / 2f, size.height * POOL_Y)
    val rayon = size.width * POOL_RADIUS
    scale(scaleX = 1f, scaleY = POOL_FLATTEN, pivot = centre) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to glow.copy(alpha = alpha),
                1f to Color.Transparent,
                center = centre,
                radius = rayon,
            ),
            radius = rayon,
            center = centre,
        )
    }
}

/** La marge de la page. */
private val GUTTER = 24.dp

/** L'espace entre deux sections d'une page. */
private val SECTION_GAP = 40.dp

/** Sous le canevas, la bande : le canevas porte déjà son propre bas de marge. */
private val SUGGESTION_GAP = 8.dp

private val MOSAIC_GAP = 12.dp

/** Le plus grand tiers de la mosaïque : sa tête fait alors 332 dp, la pochette du canevas 320. */
private val MOSAIC_THIRD_MAX = 160.dp

/** La pochette du canevas : un peu moins de trois quarts de la largeur. */
private const val COVER_FRACTION = 0.72f
private val COVER_MAX = 320.dp
private val COVER_LIFT = 24.dp

/** Le salut : 2 rem, comme sur le bureau. */
private const val GREETING_SIZE = 32
private const val GREETING_LINE = 38

private const val MORNING = 5
private const val EVENING = 18
private const val NIGHT = 23

/**
 * L'opacité voulue des deux lumières, avant la borne de lisibilité. L'aura de
 * Fluid reste un voile : à 0,5, une pochette claire peignait derrière le texte
 * un aplat gris qui se lisait comme une carte.
 */
private const val FLUID_AURA = 0.28f
private const val LIQUID_POOL = 0.6f

private const val AURA_X = 0.4f
private const val AURA_RADIUS = 0.95f
private const val AURA_DROP = 0.9f
private const val AURA_KNEE = 0.45f
private const val AURA_KNEE_ALPHA = 0.45f
private const val POOL_Y = 0.55f
private const val POOL_RADIUS = 0.8f
private const val POOL_FLATTEN = 0.85f
