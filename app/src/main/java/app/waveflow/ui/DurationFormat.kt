package app.waveflow.ui

import java.util.Locale

/**
 * Formate une durée en `m:ss` (ou `h:mm:ss` au-delà d'une heure).
 *
 * [Locale.US] est imposé : ce sont des chiffres et des deux-points, pas du
 * texte à traduire, et certaines locales substituent des chiffres non arabes.
 */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
