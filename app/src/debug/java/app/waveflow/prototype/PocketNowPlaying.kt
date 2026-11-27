package app.waveflow.prototype

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.PlaybackSpeed
import app.waveflow.model.orUnknownArtist
import app.waveflow.playback.AbLoopState
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.RepeatMode
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.components.SheetHeader
import app.waveflow.ui.components.SheetOption
import app.waveflow.ui.components.WaveFlowSheet
import app.waveflow.ui.player.LyricsPane
import app.waveflow.ui.player.LyricsUiState
import app.waveflow.ui.player.PlaybackSpeedSheet
import app.waveflow.ui.player.PlayerUiState
import app.waveflow.ui.player.QueueScreen
import app.waveflow.ui.player.SleepTimerSheet
import app.waveflow.ui.player.loopMarks
import app.waveflow.ui.player.loopOfTrack
import app.waveflow.ui.theme.WaveFlowTheme
import kotlinx.coroutines.launch

/** Tout ce que le lecteur de Pocket sait faire : les commandes du `PlayerViewModel` de production. */
data class NowPlayingActions(
    val onCollapse: () -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onSkipNext: () -> Unit,
    val onSkipPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onPlayQueueItem: (Int) -> Unit,
    val onMoveQueueItem: (Int, Int) -> Unit,
    val onRemoveQueueItem: (Int) -> Unit,
    val onStartSleepTimer: (Long) -> Unit,
    val onCancelSleepTimer: () -> Unit,
    val onSleepTimerRemainingMs: () -> Long?,
    val onSetPlaybackSpeed: (Float) -> Unit,
    val onMarkAbLoop: () -> Unit,
    val onToggleLyrics: () -> Unit,
    val onRetryLyrics: () -> Unit,
    val onLyricsOnScreen: (Boolean) -> Unit,
)

/** Les feuilles du lecteur : une à la fois. */
private enum class Feuille { Plus, File, Vitesse, Minuterie }

/**
 * Now Playing — l'écran qui définit WaveFlow sur un téléphone
 * (`docs/design/sonic-editorial.md`).
 *
 * De haut en bas : une barre légère (réduire, ce qui joue, « Plus ») ; la
 * pochette, qui prend toute la hauteur qui reste ; le titre collé à elle et ses
 * pastilles d'état ; le sillage ; les commandes ; le dock au pouce (paroles,
 * file). Derrière, la nappe de la pochette — sa lumière, jamais l'accent : le
 * disque de lecture reste vert, quelle que soit la pochette.
 *
 * La pochette n'a pas de taille fixe ([fitCover]) : sur un petit écran ou à
 * police agrandie, l'écran passe en disposition compacte ; en paysage large,
 * en deux volets.
 */
@Composable
fun PocketNowPlaying(
    state: PlayerUiState,
    lyrics: LyricsUiState,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    // La file peut se vider pendant que l'écran redescend : on garde le
    // dernier morceau connu plutôt que de le faire disparaître d'un coup.
    var dernier by remember { mutableStateOf(state.track) }
    LaunchedEffect(state.track) { state.track?.let { dernier = it } }
    val track = state.track ?: dernier ?: return

    var feuille by rememberSaveable { mutableStateOf<Feuille?>(null) }
    val colors = Pocket.colors
    val nappe = rememberAtmosphere(track.artworkUri)

    Box(
        modifier = modifier
            .fillMaxSize()
            .atmosphere(nappe, colors.canvas)
            // Le lecteur recouvre la bibliothèque : ses taps ne la traversent pas.
            .pointerInput(Unit) { detectTapGestures {} },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            TopBar(track = track, onCollapse = actions.onCollapse, onMore = { feuille = Feuille.Plus })
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val gouttiere = if (maxWidth < NARROW) 20.dp else 24.dp
                val corps = Modifier.padding(horizontal = gouttiere)
                val details: @Composable (Boolean, @Composable () -> Unit) -> Unit = { mesure, header ->
                    Details(
                        // La copie qui ne sert qu'à mesurer ne joue pas : son
                        // sillage ne coule pas, et la hauteur ne dépend pas de
                        // la lecture.
                        state = if (mesure) state.copy(isPlaying = false, isBuffering = false) else state,
                        track = track,
                        lyricsShown = lyrics.shown,
                        actions = actions,
                        header = header,
                        onOpen = { feuille = it },
                    )
                }
                val cover: @Composable (Modifier) -> Unit = { m ->
                    CoverStage(
                        artworkUri = track.artworkUri,
                        playing = state.isPlaying,
                        glow = nappe,
                        onTap = actions.onToggleLyrics,
                        onNext = actions.onSkipNext,
                        onPrevious = actions.onSkipPrevious,
                        modifier = m,
                    )
                }
                val paroles: @Composable (Modifier) -> Unit = { m ->
                    Paroles(state, lyrics, track, nappe, actions, m)
                }

                if (usesTwoPanes(maxWidth, maxHeight)) {
                    TwoPanes(maxHeight, lyrics.shown, corps, cover, paroles) {
                        details(false) { TitleBlock(track) }
                    }
                } else {
                    OnePane(lyrics.shown, gouttiere, cover, paroles) { compact, mesure ->
                        details(mesure) {
                            if (compact) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    cover(Modifier.size(COMPACT_COVER))
                                    Spacer(Modifier.width(16.dp))
                                    TitleBlock(track, modifier = Modifier.weight(1f))
                                }
                            } else {
                                TitleBlock(track)
                            }
                        }
                    }
                }
            }
        }

        Feuilles(feuille, state, track, actions, onChange = { feuille = it })
    }
}

/**
 * Une colonne : la pochette prend ce que [content] (le titre, la progression,
 * les commandes, le dock) lui laisse ; en disposition compacte, la vignette
 * passe à côté du titre et la colonne défile au besoin.
 */
@Composable
private fun OnePane(
    lyricsShown: Boolean,
    gouttiere: Dp,
    cover: @Composable (Modifier) -> Unit,
    paroles: @Composable (Modifier) -> Unit,
    content: @Composable (compact: Boolean, measuring: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val corps = Modifier.padding(horizontal = gouttiere)
    SubcomposeLayout(Modifier.fillMaxSize()) { contraintes ->
        val w = contraintes.maxWidth
        val h = contraintes.maxHeight
        val ecart = COVER_GAP.roundToPx()
        // Sur une tablette, le titre et les commandes ne s'étirent pas sur
        // toute la largeur : leur colonne est bornée, et centrée.
        val largeur = minOf(w, (DETAILS_MAX + gouttiere * 2).roundToPx())
        val x0 = (w - largeur) / 2
        val libre = Constraints(maxWidth = largeur, maxHeight = h)
        // Une copie sans sémantique, pour mesurer seulement : la disposition
        // compacte ne doit pas laisser au lecteur d'écran une seconde rangée de
        // commandes, jamais placée.
        val hauteurBloc = subcompose("mesure") { Box(corps.clearAndSetSemantics {}) { content(false, true) } }
            .map { it.measure(libre) }
            .maxOfOrNull { it.height } ?: 0
        val details = { subcompose("details") { Box(corps) { content(false, false) } }.map { it.measure(libre) } }

        if (lyricsShown) {
            val zone = (h - hauteurBloc - ecart).coerceAtLeast(0)
            val texte = subcompose("paroles") { paroles(corps.fillMaxSize()) }
                .map { it.measure(Constraints.fixed(largeur, zone)) }
            val bloc = details()
            return@SubcomposeLayout layout(w, h) {
                texte.forEach { it.place(x0, 0) }
                bloc.forEach { it.place(x0, h - hauteurBloc) }
            }
        }

        val ajustement = with(density) {
            fitCover(
                width = largeur.toDp() - gouttiere * 2,
                height = h.toDp(),
                details = hauteurBloc.toDp(),
                gap = COVER_GAP,
            )
        }
        when (ajustement) {
            is CoverFit.Full -> {
                val cote = ajustement.side.roundToPx()
                val pochette = subcompose("pochette") {
                    cover(Modifier)
                }.map { it.measure(Constraints.fixed(cote, cote)) }
                val bloc = details()
                val total = cote + ecart + hauteurBloc
                val haut = ((h - total) / 2).coerceAtLeast(0)
                layout(w, h) {
                    pochette.forEach { it.place((w - cote) / 2, haut) }
                    bloc.forEach { it.place(x0, haut + cote + ecart) }
                }
            }
            CoverFit.Compact -> {
                val compacte = subcompose("compacte") {
                    // Aligné au pied, à portée du pouce ; s'il déborde
                    // encore (police doublée sur un petit écran), il défile.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Box(corps.verticalScroll(rememberScrollState())) { content(true, false) }
                    }
                }.map { it.measure(Constraints.fixed(largeur, h)) }
                layout(w, h) { compacte.forEach { it.place(x0, 0) } }
            }
        }
    }
}

/** En paysage large : la pochette à gauche, le reste à droite, centrés. */
@Composable
private fun TwoPanes(
    hauteur: Dp,
    lyricsShown: Boolean,
    corps: Modifier,
    cover: @Composable (Modifier) -> Unit,
    paroles: @Composable (Modifier) -> Unit,
    details: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = corps.fillMaxSize(),
    ) {
        val cote = minOf(hauteur - 32.dp, COVER_CAP)
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            // Carrée, et bornée par son volet autant que par la hauteur.
            if (lyricsShown) paroles(Modifier.fillMaxSize()) else cover(Modifier.widthIn(max = cote).aspectRatio(1f))
        }
        Spacer(Modifier.width(32.dp))
        Box(Modifier.weight(1f).widthIn(max = 480.dp)) { details() }
    }
}

@Composable
private fun TopBar(track: PlayingTrack, onCollapse: () -> Unit, onMore: () -> Unit) {
    val colors = Pocket.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Réduire le lecteur", tint = colors.ink2)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "EN LECTURE",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 1,
            )
            Text(
                text = track.album ?: track.source.label,
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Plus d'options de lecture", tint = colors.ink2)
        }
    }
}

/**
 * La grande pochette. Elle recule à 92 % en pause et revient à la lecture —
 * le seul mouvement de taille de l'écran, qui dit l'état d'un regard. Un
 * appui montre les paroles ; un balayage horizontal change de morceau,
 * passé [SWIPE_THRESHOLD], et la pochette suit le doigt, amortie.
 *
 * Son ombre est teintée de la nappe : la seule ombre de la direction.
 */
@Composable
private fun CoverStage(
    artworkUri: Uri?,
    playing: Boolean,
    glow: Color,
    onTap: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = WaveFlowTheme.motion
    val echelle by animateFloatAsState(if (playing) 1f else PAUSED_SCALE, motion.shape(), label = "recul")
    val decalage = remember { Animatable(0f) }
    val portee = rememberCoroutineScope()
    val seuil = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val suivant by rememberUpdatedState(onNext)
    val precedent by rememberUpdatedState(onPrevious)

    Artwork(
        artworkUri = artworkUri,
        shape = PocketShapes.cover,
        modifier = modifier
            .graphicsLayer {
                scaleX = echelle
                scaleY = echelle
                translationX = if (motion.reduced) 0f else decalage.value * SWIPE_FOLLOW
            }
            .shadow(elevation = 28.dp, shape = PocketShapes.cover, ambientColor = glow, spotColor = glow)
            .clip(PocketShapes.cover)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val fait = decalage.value
                        when {
                            fait <= -seuil -> suivant()
                            fait >= seuil -> precedent()
                        }
                        portee.launch { decalage.animateTo(0f, motion.shape()) }
                    },
                    onDragCancel = { portee.launch { decalage.animateTo(0f, motion.shape()) } },
                ) { change, glisse ->
                    change.consume()
                    portee.launch { decalage.snapTo(decalage.value + glisse) }
                }
            }
            .clickable(onClickLabel = "Afficher les paroles", role = Role.Button, onClick = onTap)
            .semantics {
                contentDescription = "Pochette"
                customActions = listOf(
                    CustomAccessibilityAction("Morceau suivant") { suivant().let { true } },
                    CustomAccessibilityAction("Morceau précédent") { precedent().let { true } },
                )
            },
    )
}

/** Le titre, l'artiste et l'album, collés à la pochette. */
@Composable
private fun TitleBlock(
    track: PlayingTrack,
    modifier: Modifier = Modifier,
) {
    val colors = Pocket.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = listOfNotNull(track.artist.orUnknownArtist(), track.album).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Les pastilles d'état : **seulement** quand un réglage s'écarte de
 * l'ordinaire. Une vitesse active et invisible est un défaut qu'on cherche
 * longtemps ; une minuterie, une boucle aussi.
 *
 * Elles vivent entre les deux durées, sur une rangée de hauteur constante :
 * une pastille qui paraît ne déplace ni la pochette ni les commandes. La
 * boucle y est chez elle — A et B sont des positions, et se posent en
 * regardant celle qui défile.
 */
@Composable
private fun StatusPills(state: PlayerUiState, trackKey: String, onOpen: (Feuille) -> Unit) {
    val vitesse = state.playbackSpeed != PlaybackSpeed.NORMALE
    val boucle = loopOfTrack(state.abLoop, trackKey)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (vitesse) {
            val libelle = PlaybackSpeed.format(state.playbackSpeed)
            PocketPill(
                text = libelle,
                icon = Icons.Rounded.Speed,
                compact = true,
                onClick = { onOpen(Feuille.Vitesse) },
                description = "Vitesse de lecture : $libelle",
            )
        }
        if (state.sleepTimerActive) {
            PocketPill(
                text = "Veille",
                icon = Icons.Rounded.Bedtime,
                compact = true,
                onClick = { onOpen(Feuille.Minuterie) },
                description = "Minuterie de veille active",
            )
        }
        if (boucle != AbLoopState.Off) {
            PocketPill(
                text = if (boucle is AbLoopState.Started) "A–" else "A–B",
                icon = Icons.Rounded.Loop,
                compact = true,
                onClick = { onOpen(Feuille.Plus) },
                description = if (boucle is AbLoopState.Started) "Boucle : A posé" else "Boucle A-B active",
            )
        }
    }
}

/** Le titre, la progression, les commandes et le dock. */
@Composable
private fun Details(
    state: PlayerUiState,
    track: PlayingTrack,
    lyricsShown: Boolean,
    actions: NowPlayingActions,
    header: @Composable () -> Unit,
    onOpen: (Feuille) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        header()
        Spacer(Modifier.height(8.dp))
        Sillage(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            trackKey = track.mediaId,
            playing = state.isPlaying,
            onSeek = actions.onSeek,
            loop = loopMarks(state.abLoop, track.mediaId, state.durationMs),
            center = { StatusPills(state = state, trackKey = track.mediaId, onOpen = onOpen) },
        )
        Spacer(Modifier.height(4.dp))
        Transport(state, actions)
        Spacer(Modifier.height(12.dp))
        Dock(state = state, lyricsShown = lyricsShown, onToggleLyrics = actions.onToggleLyrics, onOpenQueue = {
            onOpen(Feuille.File)
        })
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Transport(state: PlayerUiState, actions: NowPlayingActions) {
    val colors = Pocket.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        // Plafonnée avant d'être étirée : dans l'autre ordre, la largeur
        // pleine fixe le minimum et le plafond n'y peut plus rien.
        modifier = Modifier
            .widthIn(max = TRANSPORT_MAX)
            .fillMaxWidth(),
    ) {
        ToggleIcon(
            icon = Icons.Rounded.Shuffle,
            label = "Lecture aléatoire",
            active = state.shuffleEnabled,
            onClick = actions.onToggleShuffle,
        )
        IconButton(onClick = actions.onSkipPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "Morceau précédent",
                tint = colors.ink,
                modifier = Modifier.size(36.dp),
            )
        }
        PlayButton(
            playing = state.isPlaying,
            buffering = state.isBuffering,
            onClick = actions.onTogglePlayPause,
            size = PLAY_SIZE,
        )
        IconButton(onClick = actions.onSkipNext, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Morceau suivant",
                tint = colors.ink,
                modifier = Modifier.size(36.dp),
            )
        }
        ToggleIcon(
            icon = if (state.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            label = when (state.repeatMode) {
                RepeatMode.Off -> "Répétition"
                RepeatMode.All -> "Répétition de la file"
                RepeatMode.One -> "Répétition de ce morceau"
            },
            active = state.repeatMode != RepeatMode.Off,
            onClick = actions.onCycleRepeat,
        )
    }
}

/** Le dock au pouce : les deux actions secondaires qu'on cherche en écoutant. */
@Composable
private fun Dock(state: PlayerUiState, lyricsShown: Boolean, onToggleLyrics: () -> Unit, onOpenQueue: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        PocketPill(
            text = "Paroles",
            icon = Icons.Rounded.Lyrics,
            selected = lyricsShown,
            onClick = onToggleLyrics,
            description = if (lyricsShown) "Masquer les paroles" else "Afficher les paroles",
        )
        val suite = state.upNextCount
        PocketPill(
            text = if (suite > 0) "File · $suite" else "File",
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            onClick = onOpenQueue,
            description = "Voir la file — $suite ${if (suite > 1) "morceaux" else "morceau"} ensuite",
        )
    }
}

@Composable
private fun Paroles(
    state: PlayerUiState,
    lyrics: LyricsUiState,
    track: PlayingTrack,
    glow: Color,
    actions: NowPlayingActions,
    modifier: Modifier,
) {
    // Les paroles ne se cherchent que tant que leur zone est à l'écran (P6).
    val signaler by rememberUpdatedState(actions.onLyricsOnScreen)
    DisposableEffect(Unit) {
        signaler(true)
        onDispose { signaler(false) }
    }
    LyricsPane(
        state = lyrics,
        trackKey = track.mediaId,
        source = track.source,
        positionMs = state.positionMs,
        playing = state.isPlaying,
        playbackSpeed = state.playbackSpeed,
        glow = glow,
        onSeek = actions.onSeek,
        onHide = actions.onToggleLyrics,
        onRetry = actions.onRetryLyrics,
        modifier = modifier,
    )
}

/** Les feuilles du bas : « Plus », la file, la vitesse, la minuterie. */
@Composable
private fun Feuilles(
    feuille: Feuille?,
    state: PlayerUiState,
    track: PlayingTrack,
    actions: NowPlayingActions,
    onChange: (Feuille?) -> Unit,
) {
    val fermer = { onChange(null) }
    when (feuille) {
        null -> Unit
        Feuille.Plus -> WaveFlowSheet(onDismissRequest = fermer) {
            SheetHeader(title = "Lecture", supporting = null)
            Spacer(Modifier.height(8.dp))
            SheetOption(
                text = "Vitesse",
                supporting = PlaybackSpeed.format(state.playbackSpeed),
                leading = Icons.Rounded.Speed,
                interaction = Modifier.clickable(role = Role.Button) { onChange(Feuille.Vitesse) },
            )
            SheetOption(
                text = "Minuterie de veille",
                supporting = if (state.sleepTimerActive) "Active" else "Arrêter la lecture plus tard",
                leading = Icons.Rounded.Bedtime,
                interaction = Modifier.clickable(role = Role.Button) { onChange(Feuille.Minuterie) },
            )
            val boucle = loopOfTrack(state.abLoop, track.mediaId)
            SheetOption(
                text = "Boucle A-B",
                supporting = when (boucle) {
                    AbLoopState.Off -> "Poser A à la position actuelle"
                    is AbLoopState.Started -> "Poser B à la position actuelle"
                    is AbLoopState.Armed -> "Effacer la boucle"
                },
                leading = Icons.Rounded.Loop,
                interaction = Modifier.clickable(role = Role.Button) {
                    actions.onMarkAbLoop()
                    fermer()
                },
            )
            Spacer(Modifier.height(24.dp))
        }
        Feuille.File -> WaveFlowSheet(onDismissRequest = fermer) {
            val suite = state.upNextCount
            SheetHeader(
                title = "File d'attente",
                supporting = if (suite > 0) "$suite ${if (suite > 1) "morceaux" else "morceau"} ensuite" else null,
            )
            QueueScreen(
                state = state,
                onPlayAt = actions.onPlayQueueItem,
                onMove = actions.onMoveQueueItem,
                onRemove = actions.onRemoveQueueItem,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(QUEUE_SHEET_HEIGHT),
            )
        }
        Feuille.Vitesse -> PlaybackSpeedSheet(
            speed = state.playbackSpeed,
            onPick = { vitesse ->
                actions.onSetPlaybackSpeed(vitesse)
                fermer()
            },
            onDismiss = fermer,
        )
        Feuille.Minuterie -> SleepTimerSheet(
            remainingMs = actions.onSleepTimerRemainingMs,
            onPick = { duree ->
                actions.onStartSleepTimer(duree)
                fermer()
            },
            onCancelTimer = {
                actions.onCancelSleepTimer()
                fermer()
            },
            onDismiss = fermer,
        )
    }
}

/** La pochette en pause : elle recule, sans disparaître. */
private const val PAUSED_SCALE = 0.92f

/** Le balayage qui change de morceau. */
private val SWIPE_THRESHOLD = 96.dp

/** La part du geste que la pochette suit : elle résiste, elle ne glisse pas. */
private const val SWIPE_FOLLOW = 0.35f

private val COVER_GAP = 24.dp
private val PLAY_SIZE = 72.dp
private val TRANSPORT_MAX = 420.dp

/** La colonne du titre et des commandes, au plus : une tablette ne l'étire pas. */
private val DETAILS_MAX = 560.dp
private val NARROW = 400.dp
private const val QUEUE_SHEET_HEIGHT = 0.8f
