package com.motodash.spotify

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class AuthCallbackResult(
    val code: String?,
    val state: String?,
    val error: String?
)

object PkceAuthHelper {
    private val secureRandom = SecureRandom()

    fun generateCodeVerifier(byteLength: Int = 64): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun generateState(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun buildAuthorizationUrl(
        clientId: String,
        codeChallenge: String,
        state: String,
        redirectUri: String = SpotifyConfig.REDIRECT_URI,
        scopes: String = SpotifyConfig.SCOPES
    ): String {
        val enc = { value: String -> URLEncoder.encode(value, StandardCharsets.UTF_8.name()) }
        return buildString {
            append(SpotifyConfig.AUTHORIZE_URL)
            append("?client_id=").append(enc(clientId))
            append("&response_type=code")
            append("&redirect_uri=").append(enc(redirectUri))
            append("&code_challenge_method=S256")
            append("&code_challenge=").append(enc(codeChallenge))
            append("&scope=").append(enc(scopes))
            append("&state=").append(enc(state))
        }
    }

    fun launchCustomTab(context: Context, authorizationUrl: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(authorizationUrl))
    }

    fun parseCallbackUri(uri: Uri?): AuthCallbackResult? {
        if (uri == null) return null
        if (uri.scheme != SpotifyConfig.CALLBACK_SCHEME || uri.host != SpotifyConfig.CALLBACK_HOST) {
            return null
        }
        return AuthCallbackResult(
            code = uri.getQueryParameter("code"),
            state = uri.getQueryParameter("state"),
            error = uri.getQueryParameter("error")
        )
    }
}
