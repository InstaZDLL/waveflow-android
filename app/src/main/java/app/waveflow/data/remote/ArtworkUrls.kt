package app.waveflow.data.remote

import android.net.Uri
import androidx.core.net.toUri

/**
 * Construit les adresses de pochettes d'un serveur.
 *
 * L'adresse est bâtie sur le **hachage** de la pochette, et non sur
 * l'identifiant de l'entité qui la porte — `/api/v2/artwork/` accepte les deux.
 * Le hachage désigne le contenu, ce qui donne deux propriétés que l'identifiant
 * n'a pas :
 *
 * - remplacer une jaquette change le hachage, donc l'adresse, donc la clé de
 *   cache. Sur l'identifiant, l'ancienne image resterait affichée aussi
 *   longtemps que le cache la garde — le serveur annonce `max-age=86400` ;
 * - un album et ses pistes portent le même hachage. Une seule entrée de cache
 *   et un seul téléchargement, là où l'identifiant en aurait produit autant que
 *   de lignes affichées.
 *
 * Une entité sans hachage n'a pas de pochette : aucune adresse n'est produite.
 * En construire une coûterait un aller-retour par ligne pour un 404 à chaque
 * fois, à chaque défilement.
 */
class ArtworkUrls(private val serverUrl: String, private val http: ServerHttp) {

    fun forHash(artworkHash: String?): Uri? {
        if (artworkHash.isNullOrBlank()) return null

        // Le hachage est un segment à part entière, et non interpolé : il vient
        // d'une réponse serveur, et un `/` qui s'y glisserait désignerait un
        // autre point d'API.
        //
        // Construite en plein rendu d'une liste : une adresse de serveur
        // invalide doit coûter une vignette, pas l'écran entier.
        return runCatching {
            http.absoluteUrl(serverUrl, "/$PATH", pathSegment = artworkHash).toUri()
        }.getOrNull()
    }

    private companion object {
        const val PATH = "api/v2/artwork"
    }
}
