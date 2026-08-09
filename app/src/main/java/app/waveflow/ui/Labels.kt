package app.waveflow.ui

/** Libellé de comptage de morceaux, partagé par les écrans de navigation et de playlists. */
fun trackCountLabel(count: Int): String = if (count <= 1) "$count titre" else "$count titres"

/** Libellé de comptage d'albums, affiché sous un artiste. */
fun albumCountLabel(count: Int): String = if (count <= 1) "$count album" else "$count albums"
