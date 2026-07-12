package com.gal.familytrips

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class FreeHotelSuggestion(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?
)

object FreeHotelSearch {
    suspend fun search(
        query: String,
        destination: String,
        limit: Int = 8
    ): List<FreeHotelSuggestion> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < 3) {
            return@withContext emptyList()
        }

        val fullQuery = listOf(
            normalizedQuery,
            "hotel",
            destination.trim()
        )
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")

        val url = URL(
            "https://photon.komoot.io/api/?" +
                "q=${Uri.encode(fullQuery)}" +
                "&limit=$limit" +
                "&lang=en"
        )

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty(
                "User-Agent",
                "GalFamilyTrips/3.6.1 (Android travel planner)"
            )
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (connection.responseCode !in 200..299) {
                return@withContext emptyList()
            }

            val payload = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            parseSuggestions(payload)
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSuggestions(
        payload: String
    ): List<FreeHotelSuggestion> {
        val root = JSONObject(payload)
        val features = root.optJSONArray("features")
            ?: return emptyList()

        val results = mutableListOf<FreeHotelSuggestion>()

        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index) ?: continue
            val properties = feature.optJSONObject("properties")
                ?: continue
            val geometry = feature.optJSONObject("geometry")
            val coordinates = geometry
                ?.optJSONArray("coordinates")

            val name = properties.optString("name")
                .ifBlank {
                    properties.optString("street")
                }
                .ifBlank {
                    properties.optString("city")
                }

            if (name.isBlank()) continue

            val type = properties.optString("type").lowercase()
            val osmValue = properties.optString("osm_value").lowercase()
            val isLikelyHotel =
                "hotel" in name.lowercase() ||
                "hostel" in name.lowercase() ||
                "guest" in name.lowercase() ||
                type in setOf("hotel", "hostel", "guest_house") ||
                osmValue in setOf("hotel", "hostel", "guest_house", "motel")

            if (!isLikelyHotel) continue

            val address = buildList {
                properties.optString("street")
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
                properties.optString("housenumber")
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
                properties.optString("postcode")
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
                properties.optString("city")
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
                properties.optString("state")
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
                properties.optString("country")
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
                .distinct()
                .joinToString(", ")
                .ifBlank { name }

            val longitude = coordinates
                ?.optDouble(0)
                ?.takeUnless { it.isNaN() }
            val latitude = coordinates
                ?.optDouble(1)
                ?.takeUnless { it.isNaN() }

            val osmId = properties.optString("osm_id")
            val osmType = properties.optString("osm_type")

            results += FreeHotelSuggestion(
                id = "$osmType-$osmId-$index",
                name = name,
                address = address,
                latitude = latitude,
                longitude = longitude
            )
        }

        return results
            .distinctBy {
                "${it.name.lowercase()}|${it.address.lowercase()}"
            }
    }
}
