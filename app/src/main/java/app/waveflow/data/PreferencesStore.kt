package app.waveflow.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.waveflow.model.AppPreferences
import app.waveflow.model.ThemeChoice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

/**
 * Les préférences de l'application, telles qu'elles survivent au redémarrage.
 *
 * Un flux plutôt qu'une lecture ponctuelle : le thème s'applique à toute
 * l'interface, et un écran qui le change doit repeindre celui d'à côté sans
 * qu'on ait à les faire dialoguer.
 *
 * Interface pour que les tests n'aient pas à écrire sur disque, et pour que
 * l'écran de réglages ne dépende pas de DataStore.
 */
interface PreferencesStore {

    val preferences: Flow<AppPreferences>

    suspend fun setTheme(choice: ThemeChoice)
}

/**
 * Préférences rangées dans un DataStore, à part de la session serveur.
 *
 * Deux fichiers et non un seul : la session porte un jeton qu'on efface à la
 * déconnexion, les préférences décrivent des choix qui n'ont aucune raison de
 * disparaître avec elle.
 *
 * Le [dataStore] est reçu et non fabriqué ici. Le délégué `preferencesDataStore`
 * mémorise son instance **par nom de fichier, pour toute la machine virtuelle** :
 * une classe qui l'appellerait elle-même rendrait deux tests successifs
 * dépendants l'un de l'autre, le second relisant ce que le premier a écrit,
 * sans qu'effacer le fichier n'y change rien. Voir [preferencesStoreOf] pour
 * l'instance de l'application.
 */
class DataStorePreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : PreferencesStore {

    /**
     * Ces préférences décorent l'application, elles ne la conditionnent pas :
     * une lecture en échec doit rendre les valeurs par défaut, jamais empêcher
     * de démarrer.
     *
     * On reprend au lieu de renoncer. Laisser l'erreur passer terminerait la
     * collecte, et un flux terminé fige le partage en aval : l'utilisateur
     * pourrait encore changer de thème sans que rien ne bouge à l'écran, sa
     * préférence étant bien écrite mais jamais relue. Un fichier
     * momentanément illisible se répare donc de lui-même ; un fichier
     * durablement cassé laisse l'application sur ses valeurs par défaut après
     * [MAX_TENTATIVES], sans boucler indéfiniment sur une cause qui ne
     * disparaîtra pas.
     */
    override val preferences: Flow<AppPreferences> = dataStore.data
        .retryWhen { error, tentative ->
            val reprendre = tentative < MAX_TENTATIVES
            // Un fichier illisible remonte en IOException ; le reste n'est pas
            // prévu et mérite d'être vu, mais ni l'un ni l'autre ne justifie
            // d'emporter l'application.
            if (error is IOException) {
                Log.w(TAG, "Préférences illisibles, valeurs par défaut", error)
            } else {
                Log.e(TAG, "Lecture des préférences en échec", error)
            }
            emit(emptyPreferences())
            if (reprendre) delay(DELAI_REPRISE_MS)
            reprendre
        }
        // Les tentatives épuisées, on tient sur les valeurs par défaut plutôt
        // que de laisser remonter dans la portée du ViewModel, qui n'a pas de
        // gestionnaire et ferait tomber l'application.
        .catch { emit(emptyPreferences()) }
        .map { it.toAppPreferences() }

    override suspend fun setTheme(choice: ThemeChoice) {
        dataStore.edit { it[THEME] = choice.name }
    }

    /**
     * Un nom de thème inconnu vaut le défaut.
     *
     * Le cas se présente si une version future en ajoute un puis qu'on
     * redescend : l'ancienne lit une valeur qu'elle ne connaît pas, et lever y
     * rendrait l'application inutilisable.
     */
    private fun Preferences.toAppPreferences(): AppPreferences = AppPreferences(
        theme = this[THEME]
            ?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } }
            ?: AppPreferences().theme,
    )

    private companion object {
        const val TAG = "PreferencesStore"

        val THEME = stringPreferencesKey("theme")

        /** Trois reprises : de quoi passer un incident, pas une corruption. */
        const val MAX_TENTATIVES = 3L
        const val DELAI_REPRISE_MS = 200L
    }
}

/** Les préférences de l'application, sur leur fichier. */
fun preferencesStoreOf(context: Context): PreferencesStore =
    DataStorePreferencesStore(context.applicationContext.preferencesDataStore)

private val Context.preferencesDataStore by preferencesDataStore(name = "preferences")
