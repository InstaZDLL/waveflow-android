package app.waveflow.data.remote

import android.net.Uri
import androidx.core.net.toUri

/**
 * Construit les adresses de pochettes d'un serveur.
 *
 * `/api/v2/artwork/{id}` accepte aussi bien un `artwork_hash` que l'identifiant
 * de l'entité qui le porte : c'est ce dernier qui est utilisé, il évite de
 * dépendre d'un hachage qui pourrait changer au réencodage d'une jaquette.
 *
 * Le hachage sert seulement à savoir **s'il y a** une pochette. Absent, aucune
 * adresse n'est produite : demander une image dont la charge utile vient de dire
 * qu'elle n'existe pas coûterait un aller-retour par ligne de liste, pour un
 * 404 à chaque fois.
 */
class ArtworkUrls(private val serverUrl: String, private val http: ServerHttp) {

    fun forEntity(entityId: String, artworkHash: String?): Uri? {
        if (artworkHash.isNullOrBlank()) return null

        return runCatching { http.absoluteUrl(serverUrl, "/$PATH/$entityId").toUri() }
            .getOrNull()
    }

    private companion object {
        const val PATH = "api/v2/artwork"
    }
}
