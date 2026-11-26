package app.waveflow.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.data.lyrics.LyricsResult
import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine
import app.waveflow.playback.TrackSource
import app.waveflow.ui.formatDuration
import app.waveflow.ui.theme.WaveFlowMaterial
import app.waveflow.ui.theme.WaveFlowTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/**
 * Les paroles, à la place de la pochette (`docs/paroles.md`, P11 à P19).
 *
 * Synchronisées, elles suivent la position et chaque ligne s'y déplace ;
 * simples, elles se lisent comme un texte ; absentes, l'écran dit pourquoi. Un
 * appui hors d'une ligne synchronisée ramène la pochette.
 *
 * @param trackKey le morceau affiché : des paroles cherchées pour un autre ne
 *   se dessinent pas (P8).
 * @param positionMs la position publiée par le lecteur, aux tics de lecture ;
 *   en pause, celle du dernier déplacement.
 * @param glow la lumière de la pochette, sous la ligne chantée en clair.
 */
@Composable
fun LyricsPane(
    state: LyricsUiState,
    trackKey: String,
    source: TrackSource,
    positionMs: Long,
    playing: Boolean,
    playbackSpeed: Float,
    glow: Color,
    onSeek: (Long) -> Unit,
    onHide: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val masquer by rememberUpdatedState(onHide)
    val result = state.result.takeIf { state.mediaId == trackKey }

    Box(
        modifier = modifier
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Revenir à la pochette") {
                        masquer()
                        true
                    },
                )
            }
            // Ce que les lignes ne prennent pas ramène la pochette : les marges,
            // le blanc à côté d'une ligne courte, des paroles simples.
            .pointerInput(Unit) { detectTapGestures { masquer() } },
    ) {
        when (result) {
            null -> LyricsWaiting()
            LyricsResult.None -> LyricsMessage(
                title = "Pas de paroles pour ce morceau.",
                detail = when (source) {
                    TrackSource.Local -> "Ni dans ses tags, ni dans un fichier .lrc à côté de lui."
                    TrackSource.Remote -> "Le serveur n'en connaît pas."
                },
            )
            LyricsResult.Unavailable -> LyricsMessage(
                title = "Paroles indisponibles.",
                detail = "Le serveur n'a pas répondu.",
                onRetry = onRetry,
            )
            is LyricsResult.Found -> when (val lyrics = result.lyrics) {
                is Lyrics.Synced -> SyncedLyrics(
                    lines = lyrics.lines,
                    trackKey = trackKey,
                    positionMs = positionMs,
                    playing = playing,
                    playbackSpeed = playbackSpeed,
                    glow = glow,
                    onSeek = onSeek,
                )
                // Un autre morceau repart de sa première ligne, même si son
                // texte arrive sans passer par l'attente.
                is Lyrics.Plain -> key(trackKey) { PlainLyrics(lyrics.lines) }
            }
        }
    }
}

/**
 * Des paroles synchronisées : la ligne chantée vers le tiers haut (P15), les
 * autres estompées avec leur distance (P18), un appui sur une ligne s'y
 * déplace (P14).
 */
@Composable
private fun SyncedLyrics(
    lines: List<SyncedLine>,
    trackKey: String,
    positionMs: Long,
    playing: Boolean,
    playbackSpeed: Float,
    glow: Color,
    onSeek: (Long) -> Unit,
) {
    val reduced = WaveFlowTheme.motion.reduced
    val position = rememberLyricsPosition(positionMs, playing, playbackSpeed, trackKey)
    // Lue sur la position de chaque image, la ligne ne recompose qu'en changeant.
    // Liée à l'état de position du morceau : un autre morceau aux paroles
    // égales en apporte un nouveau, que l'ancien calcul ne lirait pas.
    val chantee by remember(lines, position) { derivedStateOf { currentLineIndex(lines, position.longValue) } }
    val liste = rememberLazyListState()
    var parcours by remember(trackKey) { mutableStateOf(false) }
    val defilements = remember { Defilements() }

    SuivreLeParcours(liste, defilements, onBrowse = { parcours = it })

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val hauteur = maxHeight
        SuivreLaLigne(
            liste,
            defilements,
            cible = chantee.coerceAtLeast(0),
            suivre = !parcours,
            reduced = reduced,
            trackKey = trackKey,
        )

        LazyColumn(
            state = liste,
            // La ligne posée en tête de liste tombe au tiers haut ; la dernière
            // peut y monter elle aussi.
            // Le retrait de côté rend au texte l'alignement que [debordant] lui
            // ôte : seule la lumière de la ligne chantée s'étend dans la marge.
            contentPadding = PaddingValues(
                start = GLOW_SPREAD,
                end = GLOW_SPREAD,
                top = hauteur / 3,
                bottom = hauteur * 2 / 3,
            ),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(defilements)
                .debordant(GLOW_SPREAD)
                .fondu(),
        ) {
            itemsIndexed(lines) { index, ligne ->
                LyricLine(
                    line = ligne,
                    sung = index == chantee,
                    distance = if (chantee < 0) index + 1 else abs(index - chantee),
                    browsing = parcours,
                    glow = glow,
                    onClick = {
                        parcours = false
                        onSeek(ligne.startMs)
                    },
                )
            }
        }

        if (parcours) {
            Button(
                onClick = { parcours = false },
                shape = WaveFlowTheme.shapes.pill,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            ) {
                Text("Revenir à la ligne")
            }
        }
    }
}

/**
 * La position que les paroles suivent (P13) : celle du lecteur, prolongée sur
 * l'horloge d'images pendant la lecture, sans dépasser d'un tic. En pause, elle
 * est celle du lecteur, et donc celle du dernier déplacement.
 *
 * L'horloge est « infinie » au sens des tests Compose : elle ne retient pas
 * l'attente d'inactivité, comme l'onde de la barre de position.
 */
@Composable
private fun rememberLyricsPosition(positionMs: Long, playing: Boolean, speed: Float, trackKey: String): LongState {
    val position = remember(trackKey) { mutableLongStateOf(positionMs) }
    LaunchedEffect(trackKey, positionMs, playing, speed) {
        position.longValue = positionMs
        if (!playing) return@LaunchedEffect
        var debut = -1L
        while (true) {
            withInfiniteAnimationFrameMillis { maintenant ->
                if (debut < 0) debut = maintenant
                position.longValue = extrapolatedPositionMs(positionMs, maintenant - debut, speed)
            }
        }
    }
    return position
}

/**
 * Ce qui fait défiler les paroles, pour distinguer l'utilisateur du suivi.
 *
 * Les défilements de [SuivreLaLigne] se comptent tant qu'ils tournent
 * ([programmes]) ; aucun ne traverse le défilement imbriqué. Ceux de
 * l'utilisateur se voient de deux façons :
 * - par le défilement imbriqué, que traversent le doigt, l'élan, la molette, le
 *   clavier et le lecteur d'écran (Compose 1.13) — `PageUp` et `PageDown` font
 *   défiler d'un coup, sans qu'un défilement reste en cours le temps d'être
 *   vu : chaque pas avance [pas] ;
 * - par un défilement en cours qu'aucun programme ne mène : un doigt posé qui
 *   ne bouge plus tient le parcours ouvert, et l'attente ne court qu'une fois
 *   levé.
 */
@Stable
private class Defilements : NestedScrollConnection {
    var programmes by mutableIntStateOf(0)
        private set
    var pas by mutableIntStateOf(0)
        private set

    suspend fun programme(defilement: suspend () -> Unit) {
        programmes++
        try {
            defilement()
        } finally {
            programmes--
        }
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed != Offset.Zero) pas++
        return Offset.Zero
    }
}

/**
 * Un défilement de l'utilisateur, quelle que soit son entrée ([Defilements]),
 * suspend le suivi ; il reprend de lui-même cinq secondes après la fin du
 * dernier (P15). Un nouveau défilement relance l'attente : `collectLatest`
 * annule la précédente. Hors d'un parcours, l'attente aboutit sans rien
 * changer.
 */
@Composable
private fun SuivreLeParcours(liste: LazyListState, defilements: Defilements, onBrowse: (Boolean) -> Unit) {
    val signaler by rememberUpdatedState(onBrowse)
    LaunchedEffect(liste, defilements) {
        var pasVus = defilements.pas
        snapshotFlow { (liste.isScrollInProgress && defilements.programmes == 0) to defilements.pas }
            .collectLatest { (enCours, pas) ->
                val aDefile = pas != pasVus
                pasVus = pas
                if (enCours || aDefile) signaler(true)
                if (!enCours) {
                    delay(BROWSE_RESUME_MS)
                    signaler(false)
                }
            }
    }
}

/**
 * Amène la ligne [cible] à sa place, au tiers haut. La première fois d'un
 * coup — on ouvre les paroles là où l'on en est —, ensuite en glissant sur la
 * courbe de la direction ; d'un coup toujours sous mouvement réduit.
 */
@Composable
private fun SuivreLaLigne(
    liste: LazyListState,
    defilements: Defilements,
    cible: Int,
    suivre: Boolean,
    reduced: Boolean,
    trackKey: String,
) {
    var place by remember(trackKey) { mutableStateOf(false) }
    val glissement = WaveFlowTheme.motion.shape<Float>()
    LaunchedEffect(cible, suivre, trackKey) {
        if (!suivre) return@LaunchedEffect
        val visible = liste.layoutInfo.visibleItemsInfo.firstOrNull { it.index == cible }
        defilements.programme {
            if (!place || reduced || visible == null) {
                liste.scrollToItem(cible)
            } else {
                liste.animateScrollBy(visible.offset.toFloat(), glissement)
            }
        }
        place = true
    }
}

/** Une ligne synchronisée, son ton réglé par sa distance à la ligne chantée (P18). */
@Composable
private fun LyricLine(
    line: SyncedLine,
    sung: Boolean,
    distance: Int,
    browsing: Boolean,
    glow: Color,
    onClick: () -> Unit,
) {
    val material = WaveFlowTheme.material
    val motion = WaveFlowTheme.motion
    val alpha by animateFloatAsState(lineAlpha(material, sung, distance, browsing), motion.tone(), label = "ligne")
    val echelle by animateFloatAsState(if (sung) SUNG_SCALE else 1f, motion.shape(), label = "echelle")
    val flou = if (motion.reduced || browsing) 0.dp else lineBlur(material, sung, distance)
    // En Liquid, la lumière de la pochette sous la ligne chantée.
    val eclairee = sung && material == WaveFlowMaterial.Liquid

    Text(
        // Une ligne vide est un pont : une note la tient, pour que la pause se
        // voie quand elle est la ligne chantée.
        text = line.text.ifEmpty { "♪" },
        style = MaterialTheme.typography.titleLarge,
        color = if (sung) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(
                onClickLabel = "Aller à ${formatDuration(line.startMs)}",
                role = Role.Button,
                onClick = onClick,
            )
            // Au lecteur d'écran, la ligne chantée est la ligne choisie.
            .semantics { selected = sung }
            .then(if (eclairee) Modifier.lumiereSous(glow) else Modifier)
            .padding(vertical = LINE_GAP)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = echelle
                scaleY = echelle
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .then(if (flou > 0.dp) Modifier.blur(flou, BlurredEdgeTreatment.Unbounded) else Modifier),
    )
}

/**
 * La lumière de la pochette sous une ligne : une ellipse de sa couleur, à la
 * mesure de la ligne, qui s'éteint avant ses bords. Une ombre de texte floutée
 * débordait de la ligne et s'y faisait couper net, en bandeau gris.
 */
private fun Modifier.lumiereSous(glow: Color): Modifier = drawBehind {
    // La ligne et un peu plus de chaque côté : la liste déborde d'autant dans
    // la marge de la page ([debordant]), si bien que rien ne s'y coupe.
    val rayonX = size.width / 2f + GLOW_SPREAD.toPx()
    val rayonY = size.height
    val centre = Offset(size.width / 2f, size.height / 2f)
    scale(scaleX = 1f, scaleY = rayonY / rayonX, pivot = centre) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to glow.copy(alpha = SUNG_GLOW_ALPHA),
                1f to Color.Transparent,
                center = centre,
                radius = rayonX,
            ),
            radius = rayonX,
            center = centre,
        )
    }
}

/** Des paroles simples : un texte qui défile, sans ligne chantée (P16). */
@Composable
private fun PlainLyrics(lines: List<String>) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 24.dp),
        modifier = Modifier
            .fillMaxSize()
            .fondu(),
    ) {
        itemsIndexed(lines) { _, ligne ->
            if (ligne.isBlank()) {
                Spacer(Modifier.height(LINE_GAP * 3))
            } else {
                Text(
                    text = ligne,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = LINE_GAP),
                )
            }
        }
    }
}

/** La recherche court : rien n'est affirmé, l'accent tourne (P17). */
@Composable
private fun LyricsWaiting() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Recherche des paroles…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LyricsMessage(title: String, detail: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Réessayer") }
        }
    }
}

/**
 * Les bords haut et bas s'effacent dans la page plutôt que de couper une ligne
 * net : un masque qui ne garde que ce que le dégradé couvre.
 */
private fun Modifier.fondu(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val bord = (EDGE_FADE.toPx() / size.height).coerceIn(0f, MAX_FADE)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                bord to Color.Black,
                1f - bord to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * L'opacité d'une ligne (P18), selon sa distance à la ligne chantée : en Fluid,
 * 80 % pour les voisines, 65 % deux lignes plus loin, 50 % au-delà ; en Liquid,
 * 90 % puis 72 %. Pendant un parcours, toutes à 75 % ; la ligne chantée
 * toujours pleine.
 */
internal fun lineAlpha(material: WaveFlowMaterial, sung: Boolean, distance: Int, browsing: Boolean): Float {
    val parDistance = if (material == WaveFlowMaterial.Liquid) LIQUID_ALPHAS else FLUID_ALPHAS
    return when {
        sung -> 1f
        browsing -> BROWSING_ALPHA
        else -> parDistance[distance.coerceIn(1, parDistance.lastIndex)]
    }
}

/**
 * Le flou d'une ligne (P18) : en Fluid seulement, croissant avec la distance —
 * l'équivalent des ombres de texte du bureau (0,6, 1,4 et 2,4 px). Jamais en
 * Liquid, qui ne floute pas une colonne de texte.
 */
internal fun lineBlur(material: WaveFlowMaterial, sung: Boolean, distance: Int): Dp =
    if (sung || material == WaveFlowMaterial.Liquid) 0.dp else FLUID_BLURS[distance.coerceIn(1, FLUID_BLURS.lastIndex)]

/** Par distance à la ligne chantée : elle-même, ses voisines, deux plus loin, au-delà. */
private val FLUID_ALPHAS = floatArrayOf(1f, 0.8f, 0.65f, 0.5f)
private val LIQUID_ALPHAS = floatArrayOf(1f, 0.9f, 0.72f, 0.72f)
private val FLUID_BLURS = listOf(0.dp, 0.4.dp, 1.dp, 1.8.dp)
private const val BROWSING_ALPHA = 0.75f

/** Le fondu des bords ne mange jamais plus de la moitié de la hauteur. */
private const val MAX_FADE = 0.5f

/** La ligne chantée grandit sans changer de mise en page : l'échelle, pas la police. */
private const val SUNG_SCALE = 1.06f
private const val SUNG_GLOW_ALPHA = 0.35f
private val GLOW_SPREAD = 24.dp
private val LINE_GAP = 6.dp
private val EDGE_FADE = 32.dp
private const val BROWSE_RESUME_MS = 5_000L

/**
 * Étend la mise en page de [marge] de chaque côté, au-delà de la place que le
 * parent donne : la liste des paroles occupe la marge de la page, où la
 * lumière de la ligne chantée s'éteint au lieu d'être coupée.
 */
private fun Modifier.debordant(marge: Dp): Modifier = layout { mesurable, contraintes ->
    val m = marge.roundToPx()
    val place = mesurable.measure(
        contraintes.copy(minWidth = contraintes.minWidth + 2 * m, maxWidth = contraintes.maxWidth + 2 * m),
    )
    layout(contraintes.maxWidth, place.height) { place.place(-m, 0) }
}
