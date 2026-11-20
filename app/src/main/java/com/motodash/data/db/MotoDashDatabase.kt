package com.motodash.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaylistEntity::class, TrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MotoDashDatabase : RoomDatabase() {
    abstract fun spotifyDao(): SpotifyDao

    companion object {
        @Volatile
        private var instance: MotoDashDatabase? = null

        fun getInstance(context: Context): MotoDashDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MotoDashDatabase::class.java,
                    "motodash.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
