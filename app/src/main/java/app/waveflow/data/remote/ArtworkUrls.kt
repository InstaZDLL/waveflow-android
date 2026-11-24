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
 * - l'image derrière l'adresse ne peut plus changer : le serveur la sert en
 *   `private, max-age=31536000, immutable`, et Coil la relit ensuite de son
 *   cache disque sans la moindre requête, jusqu'à un an ou jusqu'à ce qu'il
 *   l'en évince (`ArtworkCacheTest`).
 *   Remplacer une jaquette change le hachage, donc l'adresse, donc la clé de
 *   cache. Sur l'identifiant, le serveur répond `private, no-cache` : chaque
 *   chargement hors du cache mémoire coûterait un aller-retour de
 *   revalidation, conclu par un 304 tant que la jaquette n'a pas changé ;
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
