package app.waveflow.ui.player

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.waveflow.model.PlaybackSpeed
import app.waveflow.model.orUnknownArtist
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.RepeatMode
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.components.WaveFlowSymbol
import app.waveflow.ui.theme.WaveFlowTheme

/**
 * Lecteur plein écran.
 *
 * La pochette éclaire la page : sa lumière, en nappes derrière tout
 * ([playerLight]), et son accent, qui remplace celui du thème sur la
 * progression, le W et les commandes ([PlayerAccent]). C'est ce qui donne
 * l'impression que l'écran « habite » l'album en cours — et seulement ici : le
 * reste de l'application garde l'accent fixe (décision du 25/11).
 *
 * La lumière, la respiration du W et l'ondulation tournent sur l'horloge
 * d'images, jamais sur les tics de position : ceux-ci s'arrêtent en pause.
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
    val light = rememberPlayerLight(track.artworkUri)
    val material = WaveFlowTheme.material
    val ground = MaterialTheme.colorScheme.background

    PlayerAccent(light) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .playerLight(light, material, ground)
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
                    playing = state.isPlaying,
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

                    // La seule ombre que la direction permet : sous une
                    // pochette, et teintée de sa lumière plutôt que grise.
                    Artwork(
                        artworkUri = track.artworkUri,
                        shape = WaveFlowTheme.shapes.cover,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shadow(
                                elevation = 24.dp,
                                shape = WaveFlowTheme.shapes.cover,
                                ambientColor = light.glow,
                                spotColor = light.glow,
                            ),
                    )

                    Spacer(Modifier.weight(1f))
                }

                TrackTitle(track = track)

                Spacer(Modifier.height(12.dp))

                PlayerSeekBar(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    trackKey = track.mediaId,
                    playing = state.isPlaying,
                    abLoop = state.abLoop,
                    onSeek = onSeek,
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
}

@Composable
private fun PlayerHeader(
    track: PlayingTrack,
    playing: Boolean,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Le symbole avant l'étiquette, comme sur le rail du bureau :
                // il respire pendant la lecture et retombe en pause. Décoratif,
                // l'étiquette dit ce qu'il marque. Sa taille est fixe : grossi
                // avec la police, il prenait à police doublée la place de
                // l'étiquette, qui se tronquait — c'est elle qui se lit.
                WaveFlowSymbol(playing = playing, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (queueShown) "FILE D'ATTENTE" else "EN LECTURE",
                    style = MaterialTheme.typography.labelSmall,
                    color = WaveFlowTheme.colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

/**
 * Le titre en voix d'affichage — serré, deux lignes au plus —, l'artiste en
 * seconde encre dessous : la page d'un album plutôt qu'une fiche.
 */
@Composable
private fun TrackTitle(track: PlayingTrack) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
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
        // disparition ferait sauter toute la rangée de commandes. Plein, dans
        // l'accent : c'est l'action principale, la seule que l'accent remplit.
        FilledIconButton(
            onClick = onTogglePlayPause,
            enabled = !isBuffering,
            shape = WaveFlowTheme.shapes.pill,
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
 * Bouton secondaire dont la teinte signale l'état actif : l'accent quand il
 * est allumé, la seconde encre au repos — jamais l'accent sous le doigt d'un
 * bouton éteint.
 */
@Composable
private fun ToggleControl(
    icon: ImageVector,
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
