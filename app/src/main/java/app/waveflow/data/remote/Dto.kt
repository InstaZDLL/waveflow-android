package app.waveflow.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ce qui circule sur le fil, et qui ne sort pas de ce paquet.
 *
 * Les noms sont ceux du serveur ; le reste de l'app manipule [AuthTokens] et
 * [app.waveflow.model.ServerSession], que l'API distante ne contraint pas.
 */
@Serializable
internal data class LoginRequest(
    val username: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
internal data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
internal data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("device_id") val deviceId: String,
    val user: UserResponse,
)

@Serializable
internal data class UserResponse(
    val id: String,
    val username: String,
    val role: String,
)

/** Corps d'erreur du serveur : `{"code": "...", "message": "..."}`. */
@Serializable
internal data class ErrorBody(
    val code: String,
    val message: String,
)
