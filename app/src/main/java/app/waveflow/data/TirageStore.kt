package app.waveflow.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.waveflow.suggestion.Raison
import app.waveflow.suggestion.SorteDeSuggestion
import app.waveflow.suggestion.SuggestionEpinglee
import app.waveflow.suggestion.TirageDuJour
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

/**
 * Le tirage de la suggestion du jour, tel qu'il survit au redémarrage.
 *
 * Interface pour que les tests de l'accueil n'aient pas à écrire sur disque.
 */
interface TirageStore {

    /** Le tirage enregistré, nul s'il n'y en a jamais eu ou qu'il est illisible. */
    val tirage: Flow<TirageDuJour?>

    /**
     * Change le tirage d'après ce qui est enregistré **au moment d'écrire**, dans
     * la même opération : c'est ce qui permet à [epingler] de ne rien écraser.
     * Une écriture en échec est notée et perdue, sans rien lever (S12).
     */
    suspend fun modifier(transform: (TirageDuJour?) -> TirageDuJour?)
}

/**
 * Le tirage dans un DataStore à lui, à part des préférences : ce n'est pas un
 * choix de l'utilisateur, et il change chaque jour.
 *
 * Le [dataStore] est reçu et non fabriqué ici, pour la raison que donne
 * [DataStorePreferencesStore].
 */
class DataStoreTirageStore(
    private val dataStore: DataStore<Preferences>,
) : TirageStore {

    /**
     * Comme les préférences : une lecture en échec rend « aucun tirage » et
     * reprend, trois fois, au lieu de terminer le flux tout de suite — terminé,
     * il figerait la bande, et « Autre suggestion » écrirait sans que rien ne
     * bouge. Un fichier encore illisible après elles est tenu pour durablement
     * cassé : le flux se termine sur « aucun tirage », sans boucler sur une cause
     * qui ne disparaîtra pas. Il est relu quand l'accueil revient à l'écran, sa
     * collecte reprenant alors de zéro.
     */
    override val tirage: Flow<TirageDuJour?> = dataStore.data
        .retryWhen { erreur, tentative ->
            Log.w(TAG, "Tirage illisible, aucun tirage", erreur)
            emit(emptyPreferences())
            val reprendre = tentative < MAX_TENTATIVES
            if (reprendre) delay(DELAI_REPRISE_MS)
            reprendre
        }
        .catch { emit(emptyPreferences()) }
        .map { it.enTirage() }

    override suspend fun modifier(transform: (TirageDuJour?) -> TirageDuJour?) {
        try {
            dataStore.edit { fichier ->
                val apres = transform(fichier.enTirage())
                fichier.clear()
                if (apres != null) fichier.ecrire(apres)
            }
        } catch (erreur: IOException) {
            Log.w(TAG, "Tirage non enregistré", erreur)
        }
    }

    private fun MutablePreferences.ecrire(tirage: TirageDuJour) {
        this[JOUR] = tirage.jour
        this[ESSAIS] = tirage.essais
        val epinglee = tirage.epinglee ?: return
        this[SORTE] = epinglee.sorte.name
        this[PISTE] = epinglee.songId
        val raison = epinglee.raison
        raison.ecoutes?.let { this[ECOUTES] = it }
        raison.derniereEcouteMs?.let { this[DERNIERE_ECOUTE] = it }
        raison.artisteAncre?.let { this[ARTISTE_ANCRE] = it }
        raison.ecoutesArtiste?.let { this[ECOUTES_ARTISTE] = it }
        raison.ajouteeMs?.let { this[AJOUTEE] = it }
    }

    /**
     * Ce que le fichier ne sait pas dire vaut « aucun tirage », ou « rien
     * d'épinglé » : le jour reprend alors à sa première suggestion, ce qui ne
     * coûte rien. Lu par la carte et avec `as?`, comme les préférences, pour
     * qu'une clé d'un autre type ne lève pas.
     */
    private fun Preferences.enTirage(): TirageDuJour? {
        val valeurs = asMap()
        val jour = valeurs[JOUR] as? Long ?: return null
        val essais = (valeurs[ESSAIS] as? Int)?.takeIf { it >= 0 } ?: return null
        val sorte = (valeurs[SORTE] as? String)?.let { nom -> SorteDeSuggestion.entries.firstOrNull { it.name == nom } }
        val piste = valeurs[PISTE] as? Long
        val epinglee = if (sorte != null && piste != null) {
            SuggestionEpinglee(
                sorte = sorte,
                songId = piste,
                raison = Raison(
                    ecoutes = valeurs[ECOUTES] as? Int,
                    derniereEcouteMs = valeurs[DERNIERE_ECOUTE] as? Long,
                    // Un nom d'artiste sans fin n'est pas un nom : écarté plutôt
                    // que porté jusqu'à l'écran.
                    artisteAncre = (valeurs[ARTISTE_ANCRE] as? String)?.takeIf { it.length <= NOM_MAX },
                    ecoutesArtiste = valeurs[ECOUTES_ARTISTE] as? Long,
                    ajouteeMs = valeurs[AJOUTEE] as? Long,
                ),
            )
        } else {
            null
        }
        return TirageDuJour(jour, essais, epinglee)
    }

    private companion object {
        const val TAG = "TirageStore"

        val JOUR = longPreferencesKey("jour")
        val ESSAIS = intPreferencesKey("essais")
        val SORTE = stringPreferencesKey("sorte")
        val PISTE = longPreferencesKey("piste")
        val ECOUTES = intPreferencesKey("ecoutes")
        val DERNIERE_ECOUTE = longPreferencesKey("derniere_ecoute")
        val ARTISTE_ANCRE = stringPreferencesKey("artiste_ancre")
        val ECOUTES_ARTISTE = longPreferencesKey("ecoutes_artiste")
        val AJOUTEE = longPreferencesKey("ajoutee")

        /** Bien au-delà d'un nom d'artiste réel. */
        const val NOM_MAX = 512

        const val MAX_TENTATIVES = 3L
        const val DELAI_REPRISE_MS = 200L
    }
}

/** Le tirage de l'application, sur son fichier. */
fun tirageStoreOf(context: Context): TirageStore =
    DataStoreTirageStore(context.applicationContext.tirageDataStore)

private val Context.tirageDataStore by preferencesDataStore(name = "suggestion_du_jour")
