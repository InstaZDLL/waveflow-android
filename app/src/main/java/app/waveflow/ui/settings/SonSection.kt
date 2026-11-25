package app.waveflow.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.waveflow.model.ModeDeGain
import app.waveflow.model.Prereglage
import app.waveflow.model.ReglagesEgaliseur
import app.waveflow.model.ReglagesReplayGain
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Un geste sur la section *Son*, que l'écran remonte sans l'interpréter.
 *
 * Un seul rappel pour toute la section plutôt qu'un par réglage : l'écran n'a
 * qu'une porte à faire traverser, et c'est le ViewModel qui sait ce que chaque
 * geste change. Voir [SettingsViewModel.regler].
 */
sealed interface GesteSon {
    data class ReplayGainActif(val actif: Boolean) : GesteSon
    data class Mode(val mode: ModeDeGain) : GesteSon
    data class Preampli(val db: Float) : GesteSon
    data class GainParDefaut(val db: Float) : GesteSon
    data class EviterSaturation(val actif: Boolean) : GesteSon
    data class EgaliseurActif(val actif: Boolean) : GesteSon
    data class Choisir(val prereglage: Prereglage) : GesteSon
    data class Bande(val index: Int, val db: Float) : GesteSon
    data object Reinitialiser : GesteSon
}

/**
 * Le ReplayGain et l'égaliseur, dans les Réglages et nulle part ailleurs (A1 de
 * `docs/audio-avance.md`).
 *
 * Les sous-réglages ne paraissent que leur interrupteur ouvert : choisis
 * l'interrupteur fermé, ils ne feraient rien, et un réglage sans effet se lit
 * comme une panne.
 */
@Composable
fun SonSection(
    replayGain: ReglagesReplayGain,
    egaliseur: ReglagesEgaliseur,
    onGeste: (GesteSon) -> Unit,
) {
    Column {
        LigneInterrupteur(
            titre = "Normaliser le volume",
            resume = "ReplayGain : un volume égal d'un titre à l'autre, d'après les tags ou le serveur",
            actif = replayGain.actif,
            onChange = { onGeste(GesteSon.ReplayGainActif(it)) },
        )
        if (replayGain.actif) {
            ChoixDuMode(courant = replayGain.mode, onChange = { onGeste(GesteSon.Mode(it)) })
            LigneCurseur(
                titre = "Pré-amplification",
                resume = "Ajoutée au gain de chaque titre : la référence ReplayGain est plus basse " +
                    "que la plupart des systèmes",
                valeur = replayGain.preampliDb,
                borne = ReglagesReplayGain.AJUSTEMENT_MAX_DB,
                pas = ReglagesReplayGain.PAS_DB,
                onChange = { onGeste(GesteSon.Preampli(it)) },
            )
            LigneCurseur(
                titre = "Gain par défaut",
                resume = "Appliqué aux titres sans ReplayGain",
                valeur = replayGain.gainParDefautDb,
                borne = ReglagesReplayGain.AJUSTEMENT_MAX_DB,
                pas = ReglagesReplayGain.PAS_DB,
                onChange = { onGeste(GesteSon.GainParDefaut(it)) },
            )
            LigneInterrupteur(
                titre = "Éviter la saturation",
                resume = "Limiter le gain à la marge que laisse le pic du titre",
                actif = replayGain.eviterSaturation,
                onChange = { onGeste(GesteSon.EviterSaturation(it)) },
            )
        }

        LigneInterrupteur(
            titre = "Égaliseur",
            resume = "Six bandes, ±12 dB",
            actif = egaliseur.actif,
            onChange = { onGeste(GesteSon.EgaliseurActif(it)) },
        )
        if (egaliseur.actif) {
            ChoixDuPrereglage(courant = egaliseur.prereglage, onChange = { onGeste(GesteSon.Choisir(it)) })
            egaliseur.bandesDb.forEachIndexed { index, db ->
                LigneCurseur(
                    titre = nomDeBande(ReglagesEgaliseur.FREQUENCES_HZ[index]),
                    resume = null,
                    valeur = db,
                    borne = ReglagesEgaliseur.GAIN_MAX_DB,
                    pas = PAS_EGALISEUR_DB,
                    onChange = { onGeste(GesteSon.Bande(index, it)) },
                )
            }
            TextButton(
                onClick = { onGeste(GesteSon.Reinitialiser) },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { Text("Réinitialiser") }
        }
    }
}

/**
 * Une ligne à interrupteur, toute la ligne portant le geste.
 *
 * `toggleable` avec `Role.Switch` et un `Switch` sans rappel, comme le choix du
 * thème avec ses boutons radio : la cible fait toute la largeur, et le lecteur
 * d'écran annonce l'état avec le titre.
 */
@Composable
private fun LigneInterrupteur(titre: String, resume: String?, actif: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = actif, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Textes(titre, resume, Modifier.weight(1f).padding(end = 16.dp))
        Switch(checked = actif, onCheckedChange = null)
    }
}

/** Le mode du ReplayGain, trois boutons radio dans un groupe annoncé « 1 sur 3 ». */
@Composable
private fun ChoixDuMode(courant: ModeDeGain, onChange: (ModeDeGain) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Text(
            text = "Gain à appliquer",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp),
        )
        ModeDeGain.entries.forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = mode == courant, role = Role.RadioButton, onClick = { onChange(mode) })
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            ) {
                RadioButton(selected = mode == courant, onClick = null)
                Textes(mode.label, mode.resume, Modifier.padding(start = 16.dp))
            }
        }
    }
}

/**
 * Un réglage en dB : le titre et la valeur au-dessus, le curseur dessous, sur
 * toute la largeur.
 *
 * Pendant le geste, le curseur suit le doigt sur un état local ; la valeur part
 * au relâchement (E7). Une valeur venue d'ailleurs — un préréglage, la
 * réinitialisation — remplace l'état local, qui en dépend par sa clé.
 */
@Composable
private fun LigneCurseur(
    titre: String,
    resume: String?,
    valeur: Float,
    borne: Float,
    pas: Float,
    onChange: (Float) -> Unit,
) {
    val etat = remember(valeur) {
        // Les crans intérieurs : de −borne à +borne par pas, sans les deux bouts.
        SliderState(valeur, (2 * borne / pas).roundToInt() - 1, -borne..borne)
    }
    val affichee = formatDb(etat.value)
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Textes(titre, resume, Modifier.weight(1f).padding(end = 16.dp))
            Text(text = affichee, style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            state = etat,
            onValueChange = { etat.value = it },
            onValueChangeFinished = { if (etat.value != valeur) onChange(etat.value) },
            modifier = Modifier.semantics {
                contentDescription = titre
                stateDescription = affichee
            },
        )
    }
}

/**
 * Le préréglage courant, et la liste des autres dans un menu.
 *
 * Un menu plutôt que vingt lignes : la liste se parcourt une fois, et
 * repousserait les curseurs hors de l'écran à chaque visite.
 */
@Composable
private fun ChoixDuPrereglage(courant: Prereglage?, onChange: (Prereglage) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.DropdownList, onClick = { ouvert = true })
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Textes("Préréglage", courant?.label ?: PERSONNALISE, Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            Prereglage.entries.forEach { prereglage ->
                DropdownMenuItem(
                    text = { Text(prereglage.label) },
                    onClick = {
                        ouvert = false
                        onChange(prereglage)
                    },
                )
            }
        }
    }
}

@Composable
private fun Textes(titre: String, resume: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = titre, style = MaterialTheme.typography.bodyLarge)
        resume?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Un gain tel qu'il s'écrit : « 0 dB », « +1,5 dB », « −3,0 dB ».
 *
 * À la main, comme la vitesse : la virgule de l'interface française ne doit pas
 * dépendre de la locale de l'appareil. Le signe moins typographique, que le
 * lecteur d'écran lit « moins ».
 */
internal fun formatDb(db: Float): String {
    val dixiemes = (db * DIXIEMES).roundToInt()
    if (dixiemes == 0) return "0 dB"
    val signe = if (dixiemes > 0) "+" else "−"
    val absolu = abs(dixiemes)
    return "$signe${absolu / DIXIEMES},${absolu % DIXIEMES} dB"
}

/** « 60 Hz », « 1 kHz », « 2,4 kHz », « 15 kHz ». */
internal fun nomDeBande(frequenceHz: Float): String {
    val hz = frequenceHz.roundToInt()
    if (hz < MILLE) return "$hz Hz"
    val centaines = (hz + CINQUANTE) / CENT
    val entier = centaines / DIXIEMES
    val reste = centaines % DIXIEMES
    return if (reste == 0) "$entier kHz" else "$entier,$reste kHz"
}

private const val PERSONNALISE = "Personnalisé"
private const val PAS_EGALISEUR_DB = 0.5f
private const val DIXIEMES = 10
private const val MILLE = 1000
private const val CENT = 100
private const val CINQUANTE = 50
