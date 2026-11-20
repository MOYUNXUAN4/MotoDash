package com.motodash.spotify

object SpotifyConfig {
    const val REDIRECT_URI = "motodash://callback"
    const val CALLBACK_SCHEME = "motodash"
    const val CALLBACK_HOST = "callback"

    const val SCOPES =
        "playlist-read-private playlist-read-collaborative user-library-read app-remote-control user-modify-playback-state user-read-playback-state"

    val SCOPE_LIST: List<String> = listOf(
        "playlist-read-private",
        "playlist-read-collaborative",
        "user-library-read",
        "app-remote-control",
        "user-modify-playback-state",
        "user-read-playback-state"
    )

    const val AUTH_BASE_URL = "https://accounts.spotify.com/"
    const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
    const val API_BASE_URL = "https://api.spotify.com/v1/"

    const val LIKED_SONGS_PLAYLIST_ID = "__motodash_liked_songs__"
    const val LIKED_SONGS_TITLE = "Liked Songs"

    const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
}
