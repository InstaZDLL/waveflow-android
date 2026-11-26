package app.waveflow.ui.navigation

import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waveflow.ui.components.WaveMark
import app.waveflow.ui.theme.LiquidGlassRim
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.WaveFlowTheme

/** Routes de navigation. Les détails portent leur identifiant dans le chemin. */
object Routes {
    const val HOME = "accueil"
    const val SEARCH = "recherche"
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"
    const val SERVER = "server"

    /**
     * Les réglages, hors de la barre du bas.
     *
     * Ils s'ouvrent depuis l'en-tête, d'où qu'on vienne : ce n'est pas une
     * section de la bibliothèque mais ce qui la gouverne.
     */
    const val SETTINGS = "reglages"

    const val ARG_ALBUM_ID = "albumId"
    const val ARG_ARTIST_ID = "artistId"
    const val ARG_PLAYLIST_ID = "playlistId"

    const val ALBUM_DETAIL = "$ALBUMS/{$ARG_ALBUM_ID}"
    const val ARTIST_DETAIL = "$ARTISTS/{$ARG_ARTIST_ID}"
    const val PLAYLIST_DETAIL = "$PLAYLISTS/{$ARG_PLAYLIST_ID}"

    /** Détails distants, sous la section Serveur : leurs clés sont des UUID. */
    private const val SERVER_ALBUMS = "$SERVER/albums"
    private const val SERVER_ARTISTS = "$SERVER/artists"

    const val SERVER_ALBUM_DETAIL = "$SERVER_ALBUMS/{$ARG_ALBUM_ID}"
    const val SERVER_ARTIST_DETAIL = "$SERVER_ARTISTS/{$ARG_ARTIST_ID}"

    /**
     * Le compte, sous les réglages et non sous le serveur.
     *
     * Il s'ouvre depuis les réglages et il y appartient : connexion, jeton,
     * cache. Le laisser sous `server/` le faisait réclamer par l'onglet Serveur
     * de la bibliothèque — l'écran Compte affichait alors les sous-onglets et
     * se disait dans la bibliothèque, où il n'est pas.
     */
    const val SERVER_ACCOUNT = "$SETTINGS/serveur"

    fun albumDetail(albumId: Long): String = "$ALBUMS/$albumId"

    fun artistDetail(artistId: Long): String = "$ARTISTS/$artistId"

    fun playlistDetail(playlistId: Long): String = "$PLAYLISTS/$playlistId"

    /**
     * Les identifiants distants sont encodés, contrairement aux locaux qui sont
     * des entiers : un `/` dans un identifiant scinderait la route, qui ne
     * correspondrait alors à aucune destination. La navigation les décode
     * d'elle-même à la lecture de l'argument.
     */
    fun serverAlbumDetail(albumId: String): String = "$SERVER_ALBUMS/${Uri.encode(albumId)}"

    fun serverArtistDetail(artistId: String): String = "$SERVER_ARTISTS/${Uri.encode(artistId)}"
}

/**
 * L'identifiant entier porté par [key], à condition que la destination courante
 * soit bien [route].
 *
 * Deux routes partagent la même clé d'argument sans partager son type :
 * [Routes.ALBUM_DETAIL] porte un identifiant MediaStore, déclaré `LongType`, et
 * [Routes.SERVER_ALBUM_DETAIL] un UUID, déclaré `StringType`. Lire le second
 * comme le premier ne lève rien : `Bundle.getLong` attrape la
 * `ClassCastException` et rend `0`, que rien ne distingue ensuite d'un
 * identifiant véritable.
 *
 * Le filtre par route est donc ce qui sépare les deux, et il doit précéder la
 * lecture plutôt que la suivre : c'est l'accès lui-même qui coûte.
 *
 * L'absence de la clé rend `null` elle aussi. `getLong` y répondrait encore
 * `0`, et rien ne distingue ce zéro-là d'un autre : autant ne pas rouvrir la
 * porte que le filtre vient de fermer.
 */
fun Bundle?.longArgOf(currentRoute: String?, route: String, key: String): Long? =
    this?.takeIf { currentRoute == route && it.containsKey(key) }?.getLong(key)

/**
 * Les trois destinations de la barre du bas.
 *
 * Trois et non cinq. Les quatre vues de la bibliothèque — titres, albums,
 * artistes, playlists — ne sont pas des sections différentes mais des manières
 * de regarder la même chose : elles passent en sous-onglets. Et le serveur
 * cesse d'être une destination pour devenir une source parmi d'autres, à
 * l'intérieur de la bibliothèque.
 *
 * La recherche, elle, en est une : chercher est une intention en soi, pas un
 * mode passager d'un écran. Elle cesse donc d'être une loupe dans l'en-tête qui
 * recouvrait la page d'un calque. Les réglages restent en revanche dans
 * l'en-tête : on n'y va pas, on y passe.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home(Routes.HOME, "Accueil", Icons.Filled.Home),
    Library(Routes.SONGS, "Bibliothèque", Icons.AutoMirrored.Filled.LibraryBooks),
    Search(Routes.SEARCH, "Recherche", Icons.Filled.Search),
    ;

    companion object {
        /**
         * La destination dont [route] est une racine de pile : l'accueil, la
         * recherche, ou l'une des vues de la bibliothèque. `null` pour toute
         * autre route — un détail, les réglages — qui ne commence aucune pile.
         */
        fun ofRoot(route: String?): TopLevelDestination? = when {
            route == Routes.HOME -> Home
            route == Routes.SEARCH -> Search
            LibraryTab.entries.any { it.route == route } -> Library
            else -> null
        }
    }
}

/**
 * Les vues de la bibliothèque, et la source qui n'en est pas une.
 *
 * `Serveur` figure dans la même rangée faute de mieux : son catalogue se
 * demande au réseau, page par page, quand les quatre autres lisent une
 * bibliothèque déjà en mémoire. Les réunir sous un vrai filtre de source
 * suppose de réconcilier ces deux façons de charger — c'est un travail à part,
 * et le catalogue devait rester atteignable d'ici là.
 */
enum class LibraryTab(val route: String, val label: String) {
    Songs(Routes.SONGS, "Titres"),
    Albums(Routes.ALBUMS, "Albums"),
    Artists(Routes.ARTISTS, "Artistes"),
    Playlists(Routes.PLAYLISTS, "Playlists"),
    Server(Routes.SERVER, "Serveur"),
    ;

    fun owns(route: String?): Boolean =
        route == this.route || route?.startsWith("${this.route}/") == true
}

/**
 * La barre du bas : le rail du bureau couché au pied de l'écran.
 *
 * L'élément ouvert porte le **liseré d'accent** du rail — un trait de 2 dp au
 * bord de la barre, au-dessus de son icône, et sous lui la lumière de
 * l'accent : un halo discret en Fluid, plus franc en Liquid, où le bord est
 * « la lumière de l'accent, pas une barre ». L'accent n'y est que là : les
 * libellés restent dans les encres, l'icône ouverte prend l'accent en Liquid
 * seulement (« the icon in the accent »).
 *
 * En Fluid la barre est posée à plat, sur la surface des panneaux. En Liquid
 * elle flotte, détachée des bords comme le rail du bureau : une plaque de
 * verre, cernée de sa lumière et portée par une ombre teintée. Les deux
 * gardent les mêmes trois destinations, dans le même ordre.
 */
@Composable
fun WaveFlowBottomBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    when (WaveFlowTheme.material) {
        WaveFlowMaterial.Fluid -> NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            BottomBarItems(selected, onSelect)
        }

        WaveFlowMaterial.Liquid -> {
            val colors = WaveFlowTheme.colors
            val shape = WaveFlowTheme.shapes.plate
            Box(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = FLOAT_INSET, vertical = FLOAT_GAP),
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    windowInsets = WindowInsets(0),
                    modifier = Modifier
                        .shadow(PLATE_LIFT, shape, ambientColor = colors.glow, spotColor = colors.glow)
                        .clip(shape)
                        .border(1.dp, LiquidGlassRim, shape),
                ) {
                    BottomBarItems(selected, onSelect)
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarItems(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val liquid = WaveFlowTheme.material == WaveFlowMaterial.Liquid
    val ink3 = WaveFlowTheme.colors.ink3
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = if (liquid) scheme.primary else scheme.onSurface,
        selectedTextColor = scheme.onSurface,
        // Le ton derrière l'icône ouverte : un ton blanc en Fluid, un voile
        // de l'accent en Liquid (12 %, celui du rail du bureau).
        indicatorColor = if (liquid) scheme.primary.copy(alpha = LIQUID_INDICATOR) else scheme.secondaryContainer,
        unselectedIconColor = ink3,
        unselectedTextColor = ink3,
    )
    val halo = if (liquid) LIQUID_HALO else FLUID_HALO
    TopLevelDestination.entries.forEach { destination ->
        val ouvert = destination == selected
        val presence by animateFloatAsState(
            targetValue = if (ouvert) 1f else 0f,
            animationSpec = WaveFlowTheme.motion.tone(),
            label = "liseré",
        )
        NavigationBarItem(
            selected = ouvert,
            onClick = { onSelect(destination) },
            icon = { Icon(destination.icon, contentDescription = null) },
            label = {
                // Sur une ligne : à police doublée, « Bibliothèque » passait à
                // la ligne au milieu du mot. Le libellé rapetisse plutôt, sans
                // jamais dépasser la taille que l'utilisateur a demandée.
                Text(
                    text = destination.label,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = LABEL_MIN_SIZE,
                        maxFontSize = LocalTextStyle.current.fontSize,
                    ),
                )
            },
            colors = colors,
            modifier = Modifier.accentEdge(scheme.primary, presence, halo),
        )
    }
}

/**
 * Le liseré de l'élément ouvert : un trait de l'accent au bord haut, et sous
 * lui une demi-ellipse de sa lumière qui s'éteint vers l'icône. [presence]
 * va de 0 à 1 : le liseré se fond d'un élément à l'autre plutôt que de sauter.
 */
internal fun Modifier.accentEdge(accent: Color, presence: Float, halo: Float): Modifier = drawWithContent {
    drawContent()
    if (presence <= 0f) return@drawWithContent
    val centre = Offset(size.width / 2f, 0f)
    val rayon = EDGE_GLOW_RADIUS.toPx()
    scale(scaleX = 1f, scaleY = EDGE_GLOW_FLATTEN, pivot = centre) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to accent.copy(alpha = halo * presence),
                1f to Color.Transparent,
                center = centre,
                radius = rayon,
            ),
            radius = rayon,
            center = centre,
        )
    }
    val demi = EDGE_LENGTH.toPx() / 2f
    val trait = EDGE_WIDTH.toPx()
    drawLine(
        color = accent.copy(alpha = presence),
        start = Offset(centre.x - demi, trait / 2f),
        end = Offset(centre.x + demi, trait / 2f),
        strokeWidth = trait,
        cap = StrokeCap.Round,
    )
}

/** Le liseré : 2 dp, la largeur de l'indicateur sous l'icône. */
private val EDGE_WIDTH = 2.dp
private val EDGE_LENGTH = 32.dp
private val EDGE_GLOW_RADIUS = 36.dp
private const val EDGE_GLOW_FLATTEN = 0.6f

/** La lumière sous le liseré : discrète en Fluid, la lumière même en Liquid. */
private const val FLUID_HALO = 0.22f
private const val LIQUID_HALO = 0.4f
private const val LIQUID_INDICATOR = 0.12f

/** Le plus petit que puisse devenir un libellé de la barre pour tenir sur une ligne. */
private val LABEL_MIN_SIZE = 10.sp

/**
 * La plaque flottante de Liquid : son retrait des bords — celui de la plaque
 * du mini-lecteur, pour que les deux tombent l'une sur l'autre —, son ombre.
 */
private val FLOAT_INSET = 8.dp
private val FLOAT_GAP = 8.dp
private val PLATE_LIFT = 16.dp

/**
 * Les sous-onglets de la bibliothèque.
 *
 * Défilants plutôt que répartis : à cinq entrées, un partage égal donnerait des
 * libellés tronqués sur un téléphone étroit ou à grande police.
 *
 * `Secondary` parce qu'ils le sont : la barre du bas dit où l'on est, ceux-ci
 * seulement comment on regarde ce qu'on y trouve.
 *
 * Des onglets en texte, l'onde sous celui qui est ouvert : c'est le deuxième
 * rôle que le document donne au motif (« the marker of the open tab »).
 *
 * Rien n'est composé hors de la bibliothèque ([libraryTabShown]).
 */
@Composable
fun LibraryTabs(
    openTab: OpenTab?,
    currentRoute: String?,
    onSelect: (LibraryTab) -> Unit,
) {
    val ouverte = libraryTabShown(openTab, currentRoute) ?: return
    val selectionne = LibraryTab.entries.indexOf(ouverte)

    SecondaryScrollableTabRow(
        selectedTabIndex = selectionne,
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        indicator = { WaveMark(Modifier.tabIndicatorOffset(selectionne, matchContentSize = true)) },
        divider = {},
    ) {
        LibraryTab.entries.forEach { onglet ->
            MarkedTab(
                selected = onglet == ouverte,
                onClick = { onSelect(onglet) },
                label = onglet.label,
            )
        }
    }
}

/**
 * La vue de la bibliothèque à marquer dans ses sous-onglets, ou `null` quand
 * ils n'ont pas lieu d'être.
 *
 * Il faut deux conditions. La pile ouverte est celle de la bibliothèque : un
 * album ouvert depuis l'accueil ou la recherche n'y est pas, et ne les montre
 * pas. Et la route ouverte en est une page : les réglages, ouverts depuis la
 * bibliothèque, vivent dans sa pile sans en être une vue.
 *
 * La vue marquée est celle de la pile, pas celle de la route : un album ouvert
 * depuis les artistes laisse « Artistes » marqué.
 */
fun libraryTabShown(openTab: OpenTab?, currentRoute: String?): LibraryTab? =
    openTab?.libraryTab?.takeIf { LibraryTab.entries.any { it.owns(currentRoute) } }

/**
 * Un onglet en texte : l'ouvert dans la première encre, les autres dans la
 * troisième. L'accent n'y est pas — l'onde dessous le porte.
 */
@Composable
fun MarkedTab(selected: Boolean, onClick: () -> Unit, label: String) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = { Text(label) },
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
        unselectedContentColor = WaveFlowTheme.colors.ink3,
    )
}
