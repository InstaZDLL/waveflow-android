package app.waveflow.prototype

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.ui.theme.oklchToColor

/*
 * Les règles des pièces de PocketParts.kt qui ne dessinent rien : elles se
 * calculent, et s'éprouvent sur la JVM sans composer d'écran.
 */

/**
 * La largeur d'une tuile de carrousel dans une largeur [available] : deux
 * tuiles et le bord d'une troisième, bornée entre [min] et [max] — une
 * tablette montre plus de tuiles, pas des tuiles géantes.
 */
fun carouselTileWidth(available: Dp, gutter: Dp, min: Dp = 132.dp, max: Dp = 200.dp): Dp {
    val visibles = CAROUSEL_VISIBLE
    val largeur = (available - gutter - TILE_SPACING * 2) / visibles
    return largeur.coerceIn(min, max)
}

/** Deux tuiles pleines et un peu plus du tiers de la troisième. */
private const val CAROUSEL_VISIBLE = 2.35f

/**
 * Les initiales d'un artiste : la première lettre de ses deux premiers mots,
 * en capitales. « ? » pour un artiste inconnu, plutôt que « AI ».
 */
fun initialsOf(name: String): String {
    if (name.isBlank() || name == UNKNOWN_ARTIST) return "?"
    return name.split(' ', '-', '_')
        .filter { mot -> mot.firstOrNull()?.isLetterOrDigit() == true }
        .take(2)
        .joinToString("") { mot -> mot.first().uppercase() }
        .ifEmpty { name.first().uppercase() }
}

/** Ce que l'application affiche pour un artiste sans tag (`orUnknownArtist`). */
private const val UNKNOWN_ARTIST = "Artiste inconnu"

/**
 * La teinte du disque d'un artiste : une teinte tirée de son nom — la même à
 * chaque fois —, à une clarté et une vivacité fixes, pour que l'encre y tienne
 * dans les deux apparences.
 */
fun artistTint(name: String, dark: Boolean): Color {
    val teinte = ((name.hashCode() % HUE_TURN) + HUE_TURN) % HUE_TURN
    return oklchToColor(
        if (dark) TINT_DARK_L else TINT_LIGHT_L,
        if (dark) TINT_DARK_C else TINT_LIGHT_C,
        teinte.toFloat(),
    )
}

private const val HUE_TURN = 360
private const val TINT_DARK_L = 0.36f
private const val TINT_DARK_C = 0.06f
private const val TINT_LIGHT_L = 0.88f
private const val TINT_LIGHT_C = 0.05f

/**
 * Étend [amount] de part et d'autre au-delà de la place reçue : une rangée
 * qui défile dans une grille déjà bordée de sa gouttière va jusqu'au bord de
 * l'écran, au lieu d'être coupée net au bord de la gouttière.
 */
fun Modifier.bleed(amount: Dp): Modifier = layout { mesurable, contraintes ->
    val extra = amount.roundToPx() * 2
    val largeur = contraintes.maxWidth + extra
    val place = mesurable.measure(contraintes.copy(minWidth = largeur, maxWidth = largeur))
    layout(contraintes.maxWidth, place.height) { place.place(-extra / 2, 0) }
}

/**
 * Le total d'un ensemble de pistes, pour la ligne d'un album : « 42 min »,
 * « 1 h 12 min ». Les secondes n'y disent rien.
 */
fun formatTotal(durationMs: Long): String {
    val minutes = ((durationMs + MS_PER_MINUTE / 2) / MS_PER_MINUTE).coerceAtLeast(if (durationMs > 0) 1 else 0)
    val heures = minutes / MINUTES_PER_HOUR
    val reste = minutes % MINUTES_PER_HOUR
    return when {
        heures == 0L -> "$minutes min"
        reste == 0L -> "$heures h"
        else -> "$heures h $reste min"
    }
}

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
