package com.motodash.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotifyDao {
    @Query("SELECT * FROM playlists ORDER BY displayOrder ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY displayOrder ASC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeTracksForPlaylist(playlistId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getTracksForPlaylist(playlistId: String): List<TrackEntity>

    // Update retained parent rows in place; REPLACE deletes them and cascades to cached tracks.
    @Upsert
    suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Query("DELETE FROM playlists WHERE id NOT IN (:retainedIds)")
    suspend fun deletePlaylistsNotIn(retainedIds: List<String>)

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksForPlaylist(playlistId: String)

    @Transaction
    suspend fun replacePlaylists(playlists: List<PlaylistEntity>) {
        if (playlists.isEmpty()) {
            deleteAllPlaylists()
        } else {
            upsertPlaylists(playlists)
            deletePlaylistsNotIn(playlists.map { it.id })
        }
    }

    @Transaction
    suspend fun replaceTracksForPlaylist(playlistId: String, tracks: List<TrackEntity>) {
        deleteTracksForPlaylist(playlistId)
        if (tracks.isNotEmpty()) {
            insertTracks(tracks)
        }
    }
}
