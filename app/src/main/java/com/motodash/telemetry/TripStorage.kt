package com.motodash.telemetry

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Atomic app-private checkpoint. Interrupted recordings are recovered as completed summaries. */
class TripStorage(context: Context) {
    private val file = File(context.filesDir, "last-trip.json")
    private val pending = File(context.filesDir, "last-trip.json.new")
    private val gson = Gson()

    @Synchronized
    fun read(): TripSummary? = runCatching {
        file.bufferedReader().use { gson.fromJson(it, TripSummary::class.java) }
    }.getOrNull()

    @Synchronized
    fun save(summary: TripSummary) {
        try {
            FileOutputStream(pending).use { stream ->
                stream.write(gson.toJson(summary).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            // Same-directory atomic replacement works on Android and Windows test hosts.
            // File.renameTo() can silently fail when the destination exists on Windows.
            Files.move(pending.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            pending.delete()
            android.util.Log.w("TripStorage", "Unable to checkpoint trip", error)
        }
    }
}
