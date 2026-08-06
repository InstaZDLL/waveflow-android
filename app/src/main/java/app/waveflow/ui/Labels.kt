package app.waveflow.ui

/** Libellés de comptage, partagés par les écrans de navigation et de playlists. */

fun trackCountLabel(count: Int): String = if (count <= 1) "$count titre" else "$count titres"

fun albumCountLabel(count: Int): String = if (count <= 1) "$count album" else "$count albums"

fun playlistCountLabel(count: Int): String =
    if (count <= 1) "$count playlist" else "$count playlists"
