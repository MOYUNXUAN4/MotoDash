package com.motodash.e2e.harness

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

data class MockPlayerState(
    val trackUri: String = "",
    val trackTitle: String = "",
    val artistName: String = "",
    val durationMs: Long = 0L,
    val playbackPositionMs: Long = 0L,
    val isPaused: Boolean = true
)

class FakeSpotifyRemotePlayer {

    private val _playerState = MutableStateFlow<MockPlayerState?>(null)
    val playerState: StateFlow<MockPlayerState?> = _playerState.asStateFlow()

    var isConnected: Boolean = false
    var shouldFailConnection: Boolean = false
    var trackQueue: List<MockPlayerState> = emptyList()
    private var currentQueueIndex: Int = -1
    val playbackCommandLog: MutableList<String> = CopyOnWriteArrayList()

    fun connect(onConnected: () -> Unit, onError: (Throwable) -> Unit) {
        if (shouldFailConnection) {
            isConnected = false
            onError(IllegalStateException("Spotify app not installed or connection rejected"))
        } else {
            isConnected = true
            onConnected()
        }
    }

    fun play(uri: String, title: String = "Playing Track", artist: String = "Artist") {
        if (!isConnected) return
        playbackCommandLog.add("play:$uri")
        val queueIdx = trackQueue.indexOfFirst { it.trackUri == uri }
        if (queueIdx >= 0) {
            currentQueueIndex = queueIdx
            _playerState.value = trackQueue[queueIdx].copy(isPaused = false, playbackPositionMs = 0L)
        } else {
            currentQueueIndex = -1
            _playerState.value = MockPlayerState(
                trackUri = uri,
                trackTitle = title,
                artistName = artist,
                isPaused = false
            )
        }
    }

    fun pause() {
        if (!isConnected) return
        playbackCommandLog.add("pause")
        _playerState.value = _playerState.value?.copy(isPaused = true)
    }

    fun resume() {
        if (!isConnected) return
        playbackCommandLog.add("resume")
        _playerState.value = _playerState.value?.copy(isPaused = false)
    }

    fun skipNext() {
        if (!isConnected) return
        playbackCommandLog.add("skipNext")
        if (trackQueue.isNotEmpty()) {
            currentQueueIndex = if (currentQueueIndex < 0) 0 else (currentQueueIndex + 1).coerceAtMost(trackQueue.lastIndex)
            _playerState.value = trackQueue[currentQueueIndex].copy(isPaused = false, playbackPositionMs = 0L)
        } else {
            val current = _playerState.value
            _playerState.value = if (current != null) {
                current.copy(
                    trackUri = "${current.trackUri}#next",
                    trackTitle = "Next: ${current.trackTitle}",
                    playbackPositionMs = 0L,
                    isPaused = false
                )
            } else {
                MockPlayerState(
                    trackUri = "spotify:track:next",
                    trackTitle = "Next Track",
                    artistName = "Artist",
                    isPaused = false
                )
            }
        }
    }

    fun skipPrevious() {
        if (!isConnected) return
        playbackCommandLog.add("skipPrevious")
        if (trackQueue.isNotEmpty()) {
            currentQueueIndex = if (currentQueueIndex < 0) 0 else (currentQueueIndex - 1).coerceAtLeast(0)
            _playerState.value = trackQueue[currentQueueIndex].copy(isPaused = false, playbackPositionMs = 0L)
        } else {
            val current = _playerState.value
            _playerState.value = if (current != null) {
                current.copy(
                    trackUri = "${current.trackUri}#prev",
                    trackTitle = "Previous: ${current.trackTitle}",
                    playbackPositionMs = 0L,
                    isPaused = false
                )
            } else {
                MockPlayerState(
                    trackUri = "spotify:track:prev",
                    trackTitle = "Previous Track",
                    artistName = "Artist",
                    isPaused = false
                )
            }
        }
    }

    fun disconnect() {
        isConnected = false
        currentQueueIndex = -1
        _playerState.value = null
    }
}
