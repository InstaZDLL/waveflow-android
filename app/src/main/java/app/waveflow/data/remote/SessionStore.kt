package app.waveflow.data.remote

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.waveflow.model.ServerSession
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Là où la session survit au redémarrage.
 *
 * Interface pour que les tests n'aient pas à écrire sur disque.
 */
interface SessionStore {
    suspend fun read(): ServerSession
    suspend fun write(session: ServerSession)
    suspend fun clear()
}

/**
 * Session rangée dans un DataStore de préférences.
 *
 * Le stockage n'est pas chiffré : il repose sur le bac à sable applicatif et le
 * chiffrement de l'appareil. `security-crypto`, la seule brique androidx qui
 * offrirait mieux, n'est jamais sortie d'alpha et n'est plus maintenue. Un
 * appareil rooté ou déverrouillé donne donc accès au jeton de rafraîchissement ;
 * la contrepartie est qu'il se révoque depuis le serveur.
 *
 * Hors de Room à dessein : ce n'est pas de la donnée relationnelle, et surtout
 * ça n'a rien à faire dans une sauvegarde de playlists.
 */
class DataStoreSessionStore(context: Context) : SessionStore {

    private val dataStore: DataStore<Preferences> = context.applicationContext.sessionDataStore

    override suspend fun read(): ServerSession = dataStore.data
        // Un fichier illisible ou corrompu remonte en IOException. La lecture a
        // lieu au démarrage, hors de toute portée qui rattraperait : mieux vaut
        // repartir déconnecté et redemander le mot de passe que ne pas démarrer.
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Session illisible, on repart déconnecté", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { it.toSession() }
        .first()

    override suspend fun write(session: ServerSession) {
        when (session) {
            is ServerSession.Disconnected -> clear()
            is ServerSession.Connected -> dataStore.edit {
                it[SERVER_URL] = session.serverUrl
                it[USERNAME] = session.username
                it[ACCESS_TOKEN] = session.accessToken
                it[REFRESH_TOKEN] = session.refreshToken
                it[DEVICE_ID] = session.deviceId
                it[ACCESS_EXPIRES_AT] = session.accessExpiresAtMs
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    /**
     * Une session partiellement écrite vaut pas de session : sans l'un de ces
     * champs, aucun appel authentifié n'aboutirait, et mieux vaut redemander le
     * mot de passe que boucler sur des 401.
     */
    private fun Preferences.toSession(): ServerSession {
        val serverUrl = this[SERVER_URL] ?: return ServerSession.Disconnected
        val username = this[USERNAME] ?: return ServerSession.Disconnected
        val accessToken = this[ACCESS_TOKEN] ?: return ServerSession.Disconnected
        val refreshToken = this[REFRESH_TOKEN] ?: return ServerSession.Disconnected
        val deviceId = this[DEVICE_ID] ?: return ServerSession.Disconnected

        return ServerSession.Connected(
            serverUrl = serverUrl,
            username = username,
            accessToken = accessToken,
            refreshToken = refreshToken,
            deviceId = deviceId,
            accessExpiresAtMs = this[ACCESS_EXPIRES_AT] ?: 0L,
        )
    }

    private companion object {
        const val TAG = "SessionStore"

        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val ACCESS_EXPIRES_AT = longPreferencesKey("access_expires_at")
    }
}

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "session_serveur",
)
