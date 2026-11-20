package com.motodash.e2e.harness

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// Contract 1: GPS Speed & Status Models
data class LocationSample(
    val speedMps: Float,
    val accuracyMeters: Float,
    val timestampMs: Long
)

enum class GpsStatus {
    GREEN,  // Accuracy <= 15m
    YELLOW, // Accuracy > 15m and <= 50m
    RED     // Accuracy > 50m or signal timeout > 3s
}

data class FilteredSpeedResult(
    val displaySpeedKmh: Float,
    val isZeroDebounced: Boolean,
    val isAccuracyRejected: Boolean,
    val isTimedOut: Boolean,
    val status: GpsStatus
)

class GpsSpeedFilterEngine(
    private val alpha: Float = 0.35f,
    private val timeoutThresholdMs: Long = 3000L
) {
    private var smoothedSpeedKmh: Float = 0.0f
    private var hasFirstSample: Boolean = false
    private var hasReceivedSample: Boolean = false
    private var lastAccuracyMeters: Float = Float.POSITIVE_INFINITY
    private var lastUpdateTimestampMs: Long = 0L

    fun processSample(sample: LocationSample): FilteredSpeedResult {
        lastUpdateTimestampMs = sample.timestampMs
        hasReceivedSample = true

        if (!sample.speedMps.isFinite() || !sample.accuracyMeters.isFinite()) {
            lastAccuracyMeters = Float.POSITIVE_INFINITY
            smoothedSpeedKmh = 0.0f
            hasFirstSample = false
            return FilteredSpeedResult(
                displaySpeedKmh = 0.0f,
                isZeroDebounced = false,
                isAccuracyRejected = true,
                isTimedOut = false,
                status = GpsStatus.RED
            )
        }

        lastAccuracyMeters = sample.accuracyMeters
        val rawKmh = sample.speedMps * 3.6f

        if (!rawKmh.isFinite()) {
            lastAccuracyMeters = Float.POSITIVE_INFINITY
            smoothedSpeedKmh = 0.0f
            hasFirstSample = false
            return FilteredSpeedResult(
                displaySpeedKmh = 0.0f,
                isZeroDebounced = false,
                isAccuracyRejected = true,
                isTimedOut = false,
                status = GpsStatus.RED
            )
        }

        // 1. Accuracy cutoff (> 20.0m)
        if (sample.accuracyMeters > 20.0f) {
            smoothedSpeedKmh = 0.0f
            return FilteredSpeedResult(
                displaySpeedKmh = 0.0f,
                isZeroDebounced = false,
                isAccuracyRejected = true,
                isTimedOut = false,
                status = evaluateStatus(sample.accuracyMeters, false)
            )
        }

        // 2. Stop debounce (< 2.5 km/h)
        if (rawKmh < 2.5f) {
            smoothedSpeedKmh = 0.0f
            return FilteredSpeedResult(
                displaySpeedKmh = 0.0f,
                isZeroDebounced = true,
                isAccuracyRejected = false,
                isTimedOut = false,
                status = evaluateStatus(sample.accuracyMeters, false)
            )
        }

        // 3. EMA Smoothing (alpha = 0.35)
        smoothedSpeedKmh = if (!hasFirstSample) {
            hasFirstSample = true
            rawKmh
        } else {
            (alpha * rawKmh) + ((1.0f - alpha) * smoothedSpeedKmh)
        }

        return FilteredSpeedResult(
            displaySpeedKmh = smoothedSpeedKmh,
            isZeroDebounced = false,
            isAccuracyRejected = false,
            isTimedOut = false,
            status = evaluateStatus(sample.accuracyMeters, false)
        )
    }

    fun checkTimeout(currentTimestampMs: Long): FilteredSpeedResult {
        val elapsed = currentTimestampMs - lastUpdateTimestampMs
        val isTimedOut = !hasReceivedSample || elapsed > timeoutThresholdMs
        return FilteredSpeedResult(
            displaySpeedKmh = if (isTimedOut) 0.0f else smoothedSpeedKmh,
            isZeroDebounced = false,
            isAccuracyRejected = false,
            isTimedOut = isTimedOut,
            status = if (isTimedOut) GpsStatus.RED else evaluateStatus(lastAccuracyMeters, false)
        )
    }

    private fun evaluateStatus(accuracyMeters: Float, isTimedOut: Boolean): GpsStatus {
        return when {
            isTimedOut -> GpsStatus.RED
            accuracyMeters <= 15.0f -> GpsStatus.GREEN
            accuracyMeters <= 50.0f -> GpsStatus.YELLOW
            else -> GpsStatus.RED
        }
    }

    fun reset() {
        smoothedSpeedKmh = 0.0f
        hasFirstSample = false
        hasReceivedSample = false
        lastAccuracyMeters = Float.POSITIVE_INFINITY
        lastUpdateTimestampMs = 0L
    }
}

// Contract 2: Dashboard Math
object DashboardMathEngine {
    fun calculateDynamicZoom(speedKmh: Float): Double {
        return when {
            speedKmh <= 20.0f -> 16.5
            speedKmh >= 100.0f -> 14.5
            else -> {
                // Linear mapping from [20, 100] to [16.5, 14.5]
                val fraction = (speedKmh - 20.0f) / (100.0f - 20.0f)
                16.5 - (fraction * 2.0)
            }
        }
    }

    fun getSpeedColorHex(speedKmh: Float): String {
        return when {
            speedKmh >= 130.0f -> "#FF3B30" // Red
            speedKmh >= 110.0f -> "#FFC107" // Amber Yellow
            else -> "#FFFFFF"               // White
        }
    }

    fun getMaskOpacity(sunlightMode: Boolean): Float {
        return if (sunlightMode) 0.82f else 0.55f
    }
}

// Contract 3: PKCE Auth Generator
object PkceUtil {
    fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val code = ByteArray(64)
        secureRandom.nextBytes(code)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code)
    }

    fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(StandardCharsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun buildAuthorizationUrl(
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        scopes: List<String>
    ): String {
        val scopeParam = scopes.joinToString("%20")
        return "https://accounts.spotify.com/authorize?client_id=$clientId&response_type=code&redirect_uri=$redirectUri&code_challenge_method=S256&code_challenge=$codeChallenge&scope=$scopeParam"
    }
}

// Contract 4: Spotify Room Cache Entities & In-Memory Store
data class PlaylistEntity(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val trackCount: Int,
    val isLikedSongs: Boolean = false,
    val displayOrder: Int = 0
)

data class PlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val trackUri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val imageUrl: String?,
    val position: Int,
    val isLocal: Boolean = false
)

class FakeRoomDatabase {
    private val playlists = ConcurrentHashMap<String, PlaylistEntity>()
    private val tracks = ConcurrentHashMap<String, MutableList<PlaylistTrackEntity>>()
    private val lock = ReentrantLock()

    fun insertPlaylists(list: List<PlaylistEntity>) = lock.withLock {
        list.forEach { p -> playlists[p.id] = p }
    }

    fun getAllPlaylists(): List<PlaylistEntity> = lock.withLock {
        playlists.values.sortedBy { it.displayOrder }
    }

    fun insertTracks(playlistId: String, trackList: List<PlaylistTrackEntity>) = lock.withLock {
        val current = tracks.computeIfAbsent(playlistId) { mutableListOf() }
        // replace or insert ensuring compound key uniqueness: [playlistId, trackUri, position]
        trackList.forEach { incoming ->
            current.removeIf { it.trackUri == incoming.trackUri && it.position == incoming.position }
            current.add(incoming)
        }
        current.sortBy { it.position }
    }

    fun getTracksForPlaylist(playlistId: String): List<PlaylistTrackEntity> = lock.withLock {
        tracks[playlistId]?.sortedBy { it.position }?.toList() ?: emptyList()
    }

    fun deletePlaylist(playlistId: String) = lock.withLock {
        playlists.remove(playlistId)
        tracks.remove(playlistId) // Cascading delete
    }

    fun clearAll() = lock.withLock {
        playlists.clear()
        tracks.clear()
    }
}
