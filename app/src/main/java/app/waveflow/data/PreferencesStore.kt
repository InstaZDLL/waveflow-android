package app.waveflow.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.waveflow.model.AppPreferences
import app.waveflow.model.PlaybackSpeed
import app.waveflow.model.StreamQuality
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

    /**
     * Change la vitesse de lecture.
     *
     * Écrite ici et non posée sur le lecteur : c'est le service qui observe la
     * préférence et l'applique, si bien que le réglage tient même quand aucun
     * écran n'est ouvert — la lecture démarrée depuis Android Auto ou la
     * notification part à la bonne vitesse.
     */
    suspend fun setPlaybackSpeed(speed: Float)

    /**
     * Change la qualité de lecture des pistes du serveur.
     *
     * Comme la vitesse, c'est le service qui l'observe : une file lancée depuis
     * Android Auto doit partir dans la qualité choisie, écran fermé.
     */
    suspend fun setStreamQuality(quality: StreamQuality)
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

    override suspend fun setTheme(choice: ThemeChoice) =
        ecrire("Thème") { it[THEME] = choice.name }

    /**
     * La vitesse est bornée à l'écriture **et** à la relecture.
     *
     * Aux deux bouts et non à un seul : borner en écrivant protège le fichier
     * de ce que l'appelant apporte, borner en lisant protège l'application de
     * ce que le fichier contient déjà — une version future aux bornes plus
     * larges, ou un fichier abîmé.
     */
    override suspend fun setPlaybackSpeed(speed: Float) =
        ecrire("Vitesse de lecture") { it[PLAYBACK_SPEED] = PlaybackSpeed.borner(speed) }

    /** Écrite par son nom, comme le thème : voir [StreamQuality] pour ce que cela interdit. */
    override suspend fun setStreamQuality(quality: StreamQuality) =
        ecrire("Qualité de lecture") { it[STREAM_QUALITY] = quality.name }

    /**
     * Écrit une préférence sans faire tomber celui qui la demande.
     *
     * L'échec d'écriture est retenu ici, comme l'est déjà celui de lecture. Les
     * appelants lancent dans la portée de leur ViewModel, laquelle n'a pas de
     * gestionnaire d'exception : un disque plein y ferait tomber l'application
     * entière — pour un thème ou une vitesse de lecture. Le choix est alors
     * simplement perdu, ce que l'écran dit de lui-même en restant sur l'ancienne
     * valeur, le flux n'ayant rien émis.
     *
     * `IOException` et non tout le reste : une annulation traverse elle aussi
     * `edit`, et l'avaler ferait survivre une écriture à la portée qui l'a
     * demandée.
     *
     * @param quoi ce qu'on tentait d'enregistrer, pour que le journal dise
     *   lequel des réglages a été perdu.
     */
    private suspend fun ecrire(quoi: String, transform: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(transform)
        } catch (erreur: IOException) {
            Log.w(TAG, "$quoi non enregistré", erreur)
        }
    }

    /**
     * Ce que le fichier ne sait pas dire vaut le défaut.
     *
     * Un nom de thème inconnu, une vitesse hors bornes : le cas se présente si
     * une version future en ajoute puis qu'on redescend, l'ancienne lisant
     * alors une valeur qu'elle ne connaît pas. Lever ici rendrait
     * l'application inutilisable pour un réglage d'apparence.
     */
    private fun Preferences.toAppPreferences(): AppPreferences {
        // Lues par la carte et non par `this[cle]`, dont le cast n'est pas
        // vérifié : une clé portant un autre type que le sien y lève une
        // `ClassCastException`. Elle surviendrait **après** le `catch`, posé en
        // amont de cette conversion, et emporterait l'application dans la
        // portée du ViewModel — pour une préférence. `as?` retombe sur le
        // défaut sans rien lever, et sans terminer le flux : le fichier reste
        // relu, si bien qu'un autre réglage change encore.
        //
        // Le cas se présente si une version future change le type d'une clé
        // puis qu'on redescend.
        val valeurs = asMap()

        return AppPreferences(
            theme = (valeurs[THEME] as? String)
                ?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } }
                ?: AppPreferences().theme,
            playbackSpeed = (valeurs[PLAYBACK_SPEED] as? Float)
                ?.let(PlaybackSpeed::borner)
                ?: AppPreferences().playbackSpeed,
            // Un profil inconnu retombe sur l'original, jamais sur un autre
            // profil transcodé : ne pas savoir ce qu'on a choisi ne doit pas
            // décider à la place de l'utilisateur de ce qu'il perd en fidélité.
            streamQuality = (valeurs[STREAM_QUALITY] as? String)
                ?.let { name -> StreamQuality.entries.firstOrNull { it.name == name } }
                ?: AppPreferences().streamQuality,
        )
    }

    private companion object {
        const val TAG = "PreferencesStore"

        val THEME = stringPreferencesKey("theme")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val STREAM_QUALITY = stringPreferencesKey("stream_quality")

        /** Trois reprises : de quoi passer un incident, pas une corruption. */
        const val MAX_TENTATIVES = 3L
        const val DELAI_REPRISE_MS = 200L
    }
}

/** Les préférences de l'application, sur leur fichier. */
fun preferencesStoreOf(context: Context): PreferencesStore =
    DataStorePreferencesStore(context.applicationContext.preferencesDataStore)

private val Context.preferencesDataStore by preferencesDataStore(name = "preferences")
