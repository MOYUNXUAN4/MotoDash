package com.motodash.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val trackCount: Int,
    val isLikedSongs: Boolean = false,
    val displayOrder: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis()
)
