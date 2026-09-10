package app.waveflow.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waveflow.model.PlaybackSpeed
import app.waveflow.model.orUnknownArtist
import app.waveflow.playback.AbLoopState
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.RepeatMode
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.formatDuration

/**
 * Lecteur plein écran.
 *
 * Le fond reprend la couleur dominante de la pochette (voir
 * [rememberArtworkAccent]), fondue vers le fond du thème : c'est ce qui donne
 * l'impression que l'écran « habite » l'album en cours.
 */
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onMoveQueueItem: (from: Int, to: Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onStartSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSleepTimerRemainingMs: () -> Long?,
    onSetPlaybackSpeed: (Float) -> Unit,
    onMarkAbLoop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local et non remonté : voir la file est une façon de regarder le lecteur,
    // pas un état de l'application. Refermer le lecteur la referme.
    var queueShown by rememberSaveable { mutableStateOf(false) }

    // Même raison : la feuille est un geste en cours, pas un état à conserver.
    var sleepSheetShown by rememberSaveable { mutableStateOf(false) }
    var speedSheetShown by rememberSaveable { mutableStateOf(false) }

    // La file peut se vider pendant l'animation de fermeture : on continue
    // d'afficher le dernier morceau connu le temps que l'écran redescende,
    // plutôt que de le faire disparaître d'un coup.
    var lastKnownTrack by remember { mutableStateOf(state.track) }
    LaunchedEffect(state.track) {
        state.track?.let { lastKnownTrack = it }
    }

    val track = state.track ?: lastKnownTrack ?: return
    val accent = rememberArtworkAccent(track.artworkUri)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(artworkGradient(accent))
            // Le lecteur recouvre la bibliothèque : on absorbe les taps pour
            // qu'ils n'atteignent pas la liste en dessous.
            .pointerInput(Unit) { detectTapGestures {} },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp),
        ) {
            PlayerHeader(
                track = track,
                onCollapse = onCollapse,
                queueShown = queueShown,
                upNextCount = state.upNextCount,
                onToggleQueue = { queueShown = !queueShown },
                sleepTimerActive = state.sleepTimerActive,
                onOpenSleepTimer = { sleepSheetShown = true },
                playbackSpeed = state.playbackSpeed,
                onOpenPlaybackSpeed = { speedSheetShown = true },
            )

            if (queueShown) {
                // La file prend la place de la pochette et non celle de tout
                // l'écran : on garde sous les yeux ce qui joue et de quoi
                // l'arrêter pendant qu'on remanie la suite.
                QueueScreen(
                    state = state,
                    onPlayAt = onPlayQueueItem,
                    onMove = onMoveQueueItem,
                    onRemove = onRemoveQueueItem,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))

                Artwork(
                    artworkUri = track.artworkUri,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(elevation = 24.dp, shape = RoundedCornerShape(16.dp)),
                )

                Spacer(Modifier.weight(1f))
            }

            TrackTitle(track = track)

            Spacer(Modifier.height(16.dp))

            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                trackKey = track.mediaId,
                onSeek = onSeek,
                abLoop = state.abLoop,
                onMarkAbLoop = onMarkAbLoop,
            )

            Spacer(Modifier.height(8.dp))

            PlayerControls(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )

            Spacer(Modifier.height(32.dp))
        }

        if (sleepSheetShown) {
            SleepTimerSheet(
                remainingMs = onSleepTimerRemainingMs,
                onPick = { duree ->
                    onStartSleepTimer(duree)
                    sleepSheetShown = false
                },
                onCancelTimer = {
                    onCancelSleepTimer()
                    sleepSheetShown = false
                },
                onDismiss = { sleepSheetShown = false },
            )
        }

        if (speedSheetShown) {
            PlaybackSpeedSheet(
                speed = state.playbackSpeed,
                onPick = { vitesse ->
                    onSetPlaybackSpeed(vitesse)
                    speedSheetShown = false
                },
                onDismiss = { speedSheetShown = false },
            )
        }
    }
}

@Composable
private fun PlayerHeader(
    track: PlayingTrack,
    onCollapse: () -> Unit,
    queueShown: Boolean,
    upNextCount: Int,
    onToggleQueue: () -> Unit,
    sleepTimerActive: Boolean,
    onOpenSleepTimer: () -> Unit,
    playbackSpeed: Float,
    onOpenPlaybackSpeed: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Réduire le lecteur",
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (queueShown) "FILE D'ATTENTE" else "EN LECTURE",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = track.album ?: track.source.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        PlaybackSpeedButton(speed = playbackSpeed, onClick = onOpenPlaybackSpeed)

        IconButton(onClick = onOpenSleepTimer) {
            Icon(
                imageVector = Icons.Filled.Bedtime,
                // L'état, pas le décompte : celui-ci ne se rafraîchit qu'au
                // rythme des tics de position, donc plus du tout en pause, et
                // annoncer « arrêt dans 30 minutes » un quart d'heure après
                // vaudrait moins que de ne rien annoncer. Le chiffre à jour est
                // dans la feuille, qui, elle, tique.
                contentDescription = if (sleepTimerActive) {
                    "Minuterie de veille active"
                } else {
                    "Minuterie de veille"
                },
                tint = if (sleepTimerActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        // Le contrepoids du bouton de gauche devient utile : il ouvre la file.
        IconButton(onClick = onToggleQueue) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = if (queueShown) {
                    "Revenir à la pochette"
                } else {
                    // Le nombre est dans la description plutôt qu'affiché : au
                    // volant comme au lecteur d'écran, « trois morceaux
                    // ensuite » vaut mieux qu'une pastille.
                    "Voir la file — $upNextCount ${if (upNextCount > 1) "morceaux" else "morceau"} ensuite"
                },
                tint = if (queueShown) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun TrackTitle(track: PlayingTrack) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = track.artist.orUnknownArtist(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    trackKey: String,
    onSeek: (Long) -> Unit,
    abLoop: AbLoopState,
    onMarkAbLoop: () -> Unit,
) {
    // Pendant un glissement, la position affichée suit le doigt et non le
    // lecteur ; remise à zéro dès qu'on change de morceau.
    var scrubProgress by remember(trackKey) { mutableStateOf<Float?>(null) }

    val hasDuration = durationMs > 0L
    val playedProgress = if (hasDuration) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val displayedProgress = scrubProgress ?: playedProgress
    val displayedPositionMs = if (scrubProgress != null) {
        (displayedProgress * durationMs).toLong()
    } else {
        positionMs
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = displayedProgress,
            onValueChange = { scrubProgress = it },
            onValueChangeFinished = {
                scrubProgress?.let { onSeek((it * durationMs).toLong()) }
                scrubProgress = null
            },
            enabled = hasDuration,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(displayedPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Entre les deux durées, et non dans l'en-tête : A et B sont des
            // positions, et se posent en regardant celle qui défile. L'en-tête
            // est par ailleurs plein.
            AbLoopButton(abLoop = abLoop, onClick = onMarkAbLoop)

            Text(
                text = if (hasDuration) formatDuration(durationMs) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleControl(
            icon = Icons.Filled.Shuffle,
            contentDescription =
            if (shuffleEnabled) "Désactiver la lecture aléatoire" else "Activer la lecture aléatoire",
            active = shuffleEnabled,
            onClick = onToggleShuffle,
        )

        IconButton(onClick = onSkipPrevious) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Morceau précédent",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }

        // Le bouton reste en place et garde sa taille pendant l'attente : sa
        // disparition ferait sauter toute la rangée de commandes.
        FilledIconButton(
            onClick = onTogglePlayPause,
            enabled = !isBuffering,
            shape = CircleShape,
            modifier = Modifier.size(72.dp),
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = "Chargement du morceau" },
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lecture",
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        IconButton(onClick = onSkipNext) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Morceau suivant",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }

        ToggleControl(
            icon = if (repeatMode == RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = when (repeatMode) {
                RepeatMode.Off -> "Activer la répétition de la file"
                RepeatMode.All -> "Répéter uniquement ce morceau"
                RepeatMode.One -> "Désactiver la répétition"
            },
            active = repeatMode != RepeatMode.Off,
            onClick = onCycleRepeat,
        )
    }
}

/**
 * La vitesse de lecture : le bouton **est** son affichage.
 *
 * Le chiffre plutôt qu'une icône, contrairement à ses voisins. Une vitesse
 * active et invisible est un défaut qu'on cherche longtemps — « pourquoi cette
 * voix est-elle pressée ? » — et aucune icône ne dit ×1,5. Elle tient dans la
 * même empreinte que les autres boutons de la rangée : `IconButton` et non
 * `TextButton`, dont la largeur minimale creuserait un trou dans l'alignement.
 *
 * La teinte reprend la grammaire du reste de l'en-tête : accentuée quand le
 * réglage s'écarte de l'ordinaire, éteinte sinon.
 */
@Composable
private fun PlaybackSpeedButton(speed: Float, onClick: () -> Unit) {
    val libelle = PlaybackSpeed.format(speed)
    val ordinaire = speed == PlaybackSpeed.NORMALE

    IconButton(
        onClick = onClick,
        // Le texte seul se lirait « fois un virgule cinq » sans qu'on sache de
        // quoi ; la description dit la grandeur, et prend le pas sur lui.
        modifier = Modifier.semantics { contentDescription = "Vitesse de lecture : $libelle" },
    ) {
        Text(
            text = libelle,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            color = if (ordinaire) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

/**
 * La boucle A-B : un seul bouton pour les trois temps.
 *
 * D'abord A, puis B, puis efface — c'est ce qu'on attend de quelque chose qu'on
 * presse en écoutant, sans quitter la musique des yeux. Le libellé dit où en est
 * la pose plutôt que ce que fera le prochain appui : « A-… » se lit comme une
 * phrase laissée en suspens, ce qu'est précisément une boucle dont B manque.
 *
 * Une boucle active et muette serait le même défaut que la vitesse invisible —
 * on chercherait longtemps pourquoi le morceau se répète. D'où la teinte, et une
 * description qui donne les deux bornes.
 */
@Composable
private fun AbLoopButton(abLoop: AbLoopState, onClick: () -> Unit) {
    val libelle = when (abLoop) {
        is AbLoopState.Started -> "A-…"
        else -> "A-B"
    }

    val description = when (abLoop) {
        AbLoopState.Off -> "Boucle A-B : poser le début"
        is AbLoopState.Started -> "Boucle A-B : poser la fin"
        is AbLoopState.Armed ->
            "Boucle active de ${formatDuration(abLoop.startMs)} " +
                "à ${formatDuration(abLoop.endMs)} — appuyer pour l'effacer"
    }

    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = libelle,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = if (abLoop == AbLoopState.Off) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

/** Bouton secondaire dont la teinte signale l'état actif. */
@Composable
private fun ToggleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint: Color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}
