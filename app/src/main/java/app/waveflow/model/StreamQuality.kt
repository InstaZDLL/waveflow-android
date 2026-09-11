package app.waveflow.model

/**
 * Ce qu'on demande au serveur pour une piste : le fichier tel quel, ou une
 * version transcodée à un débit donné.
 *
 * @property format `raw`, `mp3` ou `opus` — les trois que le serveur sait servir.
 * @property bitrate en kbit/s. `null` pour l'original, qui n'a pas de débit à
 *   choisir : le serveur refuse d'ailleurs un débit accompagnant `raw`.
 */
data class StreamRendering(val format: String, val bitrate: Int?) {

    /** Le fichier tel qu'il est sur le serveur, sans transcodage. */
    val isOriginal: Boolean get() = format == FORMAT_ORIGINAL

    companion object {
        /** Le défaut du serveur : aucun transcodage. */
        const val FORMAT_ORIGINAL = "raw"

        val ORIGINAL = StreamRendering(FORMAT_ORIGINAL, bitrate = null)
    }
}

/**
 * La qualité de lecture des pistes du serveur.
 *
 * Des profils et non un codec et un débit à régler : on sait ce qu'on veut
 * épargner — un forfait, une liaison lente —, rarement ce que valent 96 kbit/s
 * d'Opus face à 192 de MP3. Le résumé de chaque profil parle donc de ce qu'il
 * coûte, pas de la manière dont il est obtenu.
 *
 * Opus pour les deux profils transcodés : à débit égal, c'est le plus fidèle des
 * deux formats que le serveur sait produire, et Android le décode de lui-même
 * sur toutes les versions que l'application accepte.
 *
 * [Original] est le défaut. Ne toucher à rien ne doit rien changer à ce qu'on
 * entendait avant que le réglage n'existe.
 *
 * **Le nom de chaque entrée est ce qui s'écrit sur le disque.** En renommer une
 * ferait retomber sur le défaut quiconque l'avait choisie.
 */
enum class StreamQuality(
    val label: String,
    val summary: String,
    val rendering: StreamRendering,
) {
    Original(
        label = "Qualité d'origine",
        summary = "Le fichier tel qu'il est sur le serveur. Le plus fidèle, et le plus lourd.",
        rendering = StreamRendering.ORIGINAL,
    ),
    Haute(
        label = "Haute qualité",
        summary = "Environ 70 Mo par heure d'écoute, très proche de l'original.",
        rendering = StreamRendering(format = "opus", bitrate = 160),
    ),
    Economie(
        label = "Économie",
        summary = "Environ 45 Mo par heure d'écoute, pour un forfait serré.",
        rendering = StreamRendering(format = "opus", bitrate = 96),
    ),
}
