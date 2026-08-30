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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
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

    override val preferences: Flow<AppPreferences> = dataStore.data
        // Un fichier illisible remonte en IOException. Ces préférences décorent
        // l'application, elles ne la conditionnent pas : repartir sur les
        // valeurs par défaut est préférable à ne pas démarrer.
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Préférences illisibles, on repart sur les valeurs par défaut", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
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
    }
}

/** Les préférences de l'application, sur leur fichier. */
fun preferencesStoreOf(context: Context): PreferencesStore =
    DataStorePreferencesStore(context.applicationContext.preferencesDataStore)

private val Context.preferencesDataStore by preferencesDataStore(name = "preferences")
