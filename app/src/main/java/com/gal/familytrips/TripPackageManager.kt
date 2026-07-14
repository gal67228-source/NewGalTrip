package com.gal.familytrips

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class TripPackageMetadata(
    val formatVersion: Int = 1,
    val appName: String = "Gal Family Trips",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val createdAt: Long = System.currentTimeMillis(),
    val tripId: String,
    val tripName: String
)

object TripPackageManager {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun createPackage(
        context: Context,
        trip: Trip
    ): Uri {
        val safeName = trip.name
            .replace(Regex("""[^\p{L}\p{N}\-_ ]"""), "")
            .trim()
            .ifBlank { "GalFamilyTrip" }
            .replace(" ", "_")

        val exportDir = File(
            context.cacheDir,
            "trip_exports"
        ).apply {
            mkdirs()
        }

        val outputFile = File(
            exportDir,
            "$safeName.gtrip"
        )

        ZipOutputStream(
            outputFile.outputStream().buffered()
        ).use { zip ->
            val metadata = TripPackageMetadata(
                tripId = trip.id,
                tripName = trip.name
            )

            zip.putNextEntry(
                ZipEntry("metadata.json")
            )
            zip.write(
                json.encodeToString(
                    TripPackageMetadata.serializer(),
                    metadata
                ).toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(
                ZipEntry("trip.json")
            )
            zip.write(
                json.encodeToString(
                    Trip.serializer(),
                    trip
                ).toByteArray()
            )
            zip.closeEntry()
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile
        )
    }

    fun importPackage(
        context: Context,
        uri: Uri
    ): Trip {
        val input = context.contentResolver
            .openInputStream(uri)
            ?: error("לא ניתן לפתוח את הקובץ")

        var tripJson: String? = null

        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (
                    !entry.isDirectory &&
                    entry.name == "trip.json"
                ) {
                    tripJson = zip
                        .readBytes()
                        .toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val raw = tripJson
            ?: error("קובץ הטיול אינו תקין")

        return json.decodeFromString(
            Trip.serializer(),
            raw
        )
    }
}
