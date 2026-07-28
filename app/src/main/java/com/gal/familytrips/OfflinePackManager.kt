package com.gal.familytrips

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import java.io.File

class OfflinePackManager(private val context: Context) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun prepare(trip: Trip): Pair<Trip, OfflinePackStatus> {
        val root = File(context.filesDir, "offline/${trip.id}").apply { mkdirs() }
        val docs = File(root, "documents").apply { mkdirs() }
        var copied = 0
        var missing = 0

        val updated = trip.documents.map { document ->
            val current = document.localCopyPath.takeIf { it.isNotBlank() && File(it).exists() }
            if (current != null) {
                copied++
                document.copy(offlineAvailable = true)
            } else {
                val path = copyUri(Uri.parse(document.uri), docs, document.name)
                if (path.isBlank()) {
                    missing++
                    document.copy(offlineAvailable = false)
                } else {
                    copied++
                    document.copy(localCopyPath = path, offlineAvailable = true)
                }
            }
        }

        val offlineTrip = trip.copy(documents = updated, offlineMode = true)
        val snapshot = File(root, "trip_snapshot.json")
        snapshot.writeText(json.encodeToString(Trip.serializer(), offlineTrip), Charsets.UTF_8)

        File(root, "emergency.txt").writeText(buildString {
            appendLine(offlineTrip.name)
            appendLine(offlineTrip.destination)
            appendLine("${offlineTrip.startDate} – ${offlineTrip.endDate}")
            appendLine("\nטיסות:")
            offlineTrip.flights.forEach {
                appendLine("${it.flightNumber} ${it.departureAirport} → ${it.arrivalAirport} ${it.departureDate} ${it.departureTime}")
            }
            appendLine("\nמלונות:")
            offlineTrip.hotels.forEach {
                appendLine("${it.name} ${it.checkIn} – ${it.checkOut} ${it.address}")
            }
        }, Charsets.UTF_8)

        val status = OfflinePackStatus(
            tripId = trip.id,
            preparedAt = System.currentTimeMillis(),
            snapshotPath = snapshot.absolutePath,
            documentCount = copied,
            missingDocumentCount = missing,
            ready = missing == 0
        )
        File(root, "status.json").writeText(
            json.encodeToString(OfflinePackStatus.serializer(), status),
            Charsets.UTF_8
        )
        return offlineTrip to status
    }

    fun readStatus(tripId: String): OfflinePackStatus? {
        val file = File(context.filesDir, "offline/$tripId/status.json")
        return if (!file.exists()) null else runCatching {
            json.decodeFromString(OfflinePackStatus.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun clear(tripId: String) {
        File(context.filesDir, "offline/$tripId").deleteRecursively()
    }

    private fun copyUri(uri: Uri, dir: File, name: String): String = runCatching {
        val safe = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "document" }
        val target = File(dir, "${System.currentTimeMillis()}_$safe")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot read file")
        target.absolutePath
    }.getOrDefault("")
}
