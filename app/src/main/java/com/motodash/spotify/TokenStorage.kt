package com.motodash.spotify

import android.content.Context
import android.content.SharedPreferences

data class SpotifyTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis(), bufferMs: Long = 60_000L): Boolean {
        return nowMs >= (expiresAtMs - bufferMs)
    }
}

interface TokenStore {
    fun saveTokens(tokens: SpotifyTokens)
    fun getTokens(): SpotifyTokens?
    fun clearTokens()

    fun savePendingPkce(codeVerifier: String, state: String)
    fun getPendingCodeVerifier(): String?
    fun getPendingState(): String?
    fun clearPendingPkce()

    fun saveClientId(clientId: String)
    fun getClientId(defaultClientId: String): String
}

class SharedPrefsTokenStore(context: Context) : TokenStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveTokens(tokens: SpotifyTokens) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putLong(KEY_EXPIRES_AT_MS, tokens.expiresAtMs)
            .apply()
    }

    override fun getTokens(): SpotifyTokens? {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)
        return SpotifyTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAtMs = expiresAt
        )
    }

    override fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT_MS)
            .apply()
    }

    override fun savePendingPkce(codeVerifier: String, state: String) {
        prefs.edit()
            .putString(KEY_CODE_VERIFIER, codeVerifier)
            .putString(KEY_AUTH_STATE, state)
            .apply()
    }

    override fun getPendingCodeVerifier(): String? = prefs.getString(KEY_CODE_VERIFIER, null)

    override fun getPendingState(): String? = prefs.getString(KEY_AUTH_STATE, null)

    override fun clearPendingPkce() {
        prefs.edit()
            .remove(KEY_CODE_VERIFIER)
            .remove(KEY_AUTH_STATE)
            .apply()
    }

    override fun saveClientId(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId.trim()).apply()
    }

    override fun getClientId(defaultClientId: String): String {
        val saved = prefs.getString(KEY_CLIENT_ID, null)?.trim()
        val isSavedValid = !saved.isNullOrBlank() &&
            !saved.contains(".") &&
            !saved.equals("com.motodash", ignoreCase = true)
        return if (isSavedValid) saved!! else defaultClientId.trim()
    }

    companion object {
        private const val PREFS_NAME = "motodash_spotify_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT_MS = "expires_at_ms"
        private const val KEY_CODE_VERIFIER = "pkce_code_verifier"
        private const val KEY_AUTH_STATE = "pkce_auth_state"
        private const val KEY_CLIENT_ID = "spotify_client_id"
    }
}
