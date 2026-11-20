package com.motodash.spotify

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SpotifyAuthManager(
    private val tokenStore: TokenStore,
    private val authApi: SpotifyAuthApi,
    private val defaultClientId: String,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val refreshMutex = Mutex()

    fun getConfiguredClientId(): String = tokenStore.getClientId(defaultClientId)

    fun saveClientId(clientId: String) {
        tokenStore.saveClientId(clientId)
    }

    fun hasValidSession(): Boolean {
        val tokens = tokenStore.getTokens() ?: return false
        return !tokens.isExpired(clockMs()) || !tokens.refreshToken.isNullOrBlank()
    }

    fun startAuthorization(context: Context): Result<Unit> {
        val clientId = getConfiguredClientId()
        if (clientId.isBlank()) {
            return Result.failure(IllegalStateException("Spotify Client ID is not configured"))
        }
        val verifier = PkceAuthHelper.generateCodeVerifier()
        val challenge = PkceAuthHelper.generateCodeChallenge(verifier)
        val state = PkceAuthHelper.generateState()
        tokenStore.savePendingPkce(verifier, state)

        val authUrl = PkceAuthHelper.buildAuthorizationUrl(
            clientId = clientId,
            codeChallenge = challenge,
            state = state
        )
        return runCatching {
            PkceAuthHelper.launchCustomTab(context, authUrl)
        }
    }

    suspend fun handleAuthRedirect(uri: Uri?): Result<SpotifyTokens> {
        val parsed = PkceAuthHelper.parseCallbackUri(uri)
            ?: return Result.failure(IllegalArgumentException("Not a MotoDash Spotify callback URI"))

        if (!parsed.error.isNullOrBlank()) {
            tokenStore.clearPendingPkce()
            return Result.failure(IllegalStateException("Spotify auth error: ${parsed.error}"))
        }

        val code = parsed.code?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Missing authorization code"))

        val expectedState = tokenStore.getPendingState()
        if (!expectedState.isNullOrBlank() && parsed.state != expectedState) {
            tokenStore.clearPendingPkce()
            return Result.failure(SecurityException("OAuth state mismatch"))
        }

        val verifier = tokenStore.getPendingCodeVerifier()
            ?: return Result.failure(IllegalStateException("Missing PKCE code verifier"))

        val clientId = getConfiguredClientId()
        if (clientId.isBlank()) {
            return Result.failure(IllegalStateException("Spotify Client ID is not configured"))
        }

        return runCatching {
            val response = authApi.exchangeAuthorizationCode(
                clientId = clientId,
                code = code,
                codeVerifier = verifier
            )
            val accessToken = response.accessToken?.takeIf { it.isNotBlank() }
                ?: error("Spotify returned empty access_token")
            val tokens = SpotifyTokens(
                accessToken = accessToken,
                refreshToken = response.refreshToken,
                expiresAtMs = clockMs() + (response.expiresIn.coerceAtLeast(60L) * 1000L)
            )
            tokenStore.saveTokens(tokens)
            tokenStore.clearPendingPkce()
            tokens
        }
    }

    suspend fun getValidAccessToken(): String? = refreshMutex.withLock {
        val current = tokenStore.getTokens() ?: return@withLock null
        if (!current.isExpired(clockMs())) {
            return@withLock current.accessToken
        }

        val refreshToken = current.refreshToken?.takeIf { it.isNotBlank() } ?: return@withLock null
        val clientId = getConfiguredClientId().takeIf { it.isNotBlank() } ?: return@withLock null

        return@withLock try {
            val refreshed = authApi.refreshAccessToken(
                clientId = clientId,
                refreshToken = refreshToken
            )
            val newAccess = refreshed.accessToken?.takeIf { it.isNotBlank() } ?: return@withLock null
            val updated = SpotifyTokens(
                accessToken = newAccess,
                refreshToken = refreshed.refreshToken?.takeIf { it.isNotBlank() } ?: refreshToken,
                expiresAtMs = clockMs() + (refreshed.expiresIn.coerceAtLeast(60L) * 1000L)
            )
            tokenStore.saveTokens(updated)
            updated.accessToken
        } catch (_: Exception) {
            null
        }
    }

    fun logout() {
        tokenStore.clearTokens()
        tokenStore.clearPendingPkce()
    }
}
