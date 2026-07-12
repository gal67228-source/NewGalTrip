package com.gal.familytrips

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

enum class PlaceSearchCategory(
    val queryWords: List<String>,
    val rankingWords: List<String>
) {
    HOTEL(
        queryWords = listOf("hotel"),
        rankingWords = listOf(
            "hotel", "hostel", "motel", "resort",
            "inn", "apart", "suite", "lodge", "guest", "מלון"
        )
    ),
    RESTAURANT(
        queryWords = listOf("restaurant"),
        rankingWords = listOf(
            "restaurant", "food", "cafe", "bistro",
            "pizza", "grill", "מסעדה", "בית קפה"
        )
    ),
    ATTRACTION(
        queryWords = listOf("attraction landmark"),
        rankingWords = listOf(
            "attraction", "museum", "gallery", "castle",
            "palace", "monument", "zoo", "aquarium",
            "theme park", "מוזיאון", "אטרקציה", "ארמון", "גן חיות"
        )
    ),
    CAFE(
        queryWords = listOf("cafe coffee"),
        rankingWords = listOf(
            "cafe", "coffee", "bakery", "patisserie", "בית קפה", "מאפייה"
        )
    ),
    SHOPPING(
        queryWords = listOf("shopping mall"),
        rankingWords = listOf(
            "mall", "shopping", "market", "store", "קניון", "שוק", "קניות"
        )
    ),
    PARK(
        queryWords = listOf("park garden landmark"),
        rankingWords = listOf(
            "park", "garden", "viewpoint", "promenade",
            "פארק", "גן", "תצפית", "טיילת"
        )
    ),
    BEACH(
        queryWords = listOf("beach"),
        rankingWords = listOf("beach", "coast", "חוף")
    ),
    POOL(
        queryWords = listOf("water park swimming pool"),
        rankingWords = listOf(
            "water park", "pool", "spa", "aquapark",
            "בריכה", "פארק מים", "ספא"
        )
    ),
    STATION(
        queryWords = listOf("train station metro station"),
        rankingWords = listOf(
            "station", "railway", "metro", "terminal",
            "תחנה", "רכבת", "מטרו"
        )
    ),
    AIRPORT(
        queryWords = listOf("airport"),
        rankingWords = listOf("airport", "terminal", "שדה תעופה")
    ),
    HOSPITAL(
        queryWords = listOf("hospital clinic"),
        rankingWords = listOf("hospital", "clinic", "בית חולים", "מרפאה")
    ),
    PHARMACY(
        queryWords = listOf("pharmacy"),
        rankingWords = listOf("pharmacy", "drugstore", "בית מרקחת")
    ),
    GENERIC(
        queryWords = emptyList(),
        rankingWords = emptyList()
    )
}

data class FreePlaceSuggestion(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val source: String,
    val category: String = ""
)

object FreePlaceSearch {
    suspend fun search(
        query: String,
        destination: String,
        category: PlaceSearchCategory,
        limit: Int = 10
    ): List<FreePlaceSuggestion> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < 2) {
            return@withContext emptyList()
        }

        val photonResults = searchPhoton(
            query = normalizedQuery,
            destination = destination,
            category = category,
            limit = limit
        )

        val nominatimResults = if (photonResults.size < 5) {
            searchNominatim(
                query = normalizedQuery,
                destination = destination,
                category = category,
                limit = limit
            )
        } else {
            emptyList()
        }

        rankAndMerge(
            query = normalizedQuery,
            destination = destination,
            category = category,
            results = photonResults + nominatimResults,
            limit = limit
        )
    }

    private fun searchPhoton(
        query: String,
        destination: String,
        category: PlaceSearchCategory,
        limit: Int
    ): List<FreePlaceSuggestion> {
        val categoryText = category.queryWords.joinToString(" ")
        val variants = listOf(
            listOf(query, categoryText, destination)
                .filter { it.isNotBlank() }.joinToString(" "),
            listOf(query, destination)
                .filter { it.isNotBlank() }.joinToString(" "),
            listOf(query, categoryText)
                .filter { it.isNotBlank() }.joinToString(" "),
            query
        ).distinct()

        val results = mutableListOf<FreePlaceSuggestion>()

        variants.forEachIndexed { variantIndex, fullQuery ->
            val url = URL(
                "https://photon.komoot.io/api/?" +
                    "q=${Uri.encode(fullQuery)}" +
                    "&limit=${limit.coerceAtMost(15)}" +
                    "&lang=en"
            )

            request(url)?.let { payload ->
                results += parsePhoton(payload, "photon-$variantIndex")
            }
        }

        return results
    }

    private fun searchNominatim(
        query: String,
        destination: String,
        category: PlaceSearchCategory,
        limit: Int
    ): List<FreePlaceSuggestion> {
        val categoryText = category.queryWords.firstOrNull().orEmpty()
        val variants = listOf(
            listOf(query, categoryText, destination)
                .filter { it.isNotBlank() }.joinToString(", "),
            listOf(query, destination)
                .filter { it.isNotBlank() }.joinToString(", "),
            query
        ).distinct()

        val results = mutableListOf<FreePlaceSuggestion>()

        variants.forEachIndexed { variantIndex, fullQuery ->
            val url = URL(
                "https://nominatim.openstreetmap.org/search" +
                    "?q=${Uri.encode(fullQuery)}" +
                    "&format=jsonv2" +
                    "&addressdetails=1" +
                    "&limit=${limit.coerceAtMost(10)}"
            )

            request(url)?.let { payload ->
                results += parseNominatim(payload, "nominatim-$variantIndex")
            }
        }

        return results
    }

    private fun request(url: URL): String? {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty(
                "User-Agent",
                "GalFamilyTrips/3.7.0 Android"
            )
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "en")
        }

        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePhoton(
        payload: String,
        prefix: String
    ): List<FreePlaceSuggestion> {
        val root = runCatching { JSONObject(payload) }.getOrNull()
            ?: return emptyList()
        val features = root.optJSONArray("features")
            ?: return emptyList()

        return buildList {
            for (index in 0 until features.length()) {
                val feature = features.optJSONObject(index) ?: continue
                val properties = feature.optJSONObject("properties")
                    ?: continue
                val coordinates = feature
                    .optJSONObject("geometry")
                    ?.optJSONArray("coordinates")

                val name = firstNonBlank(
                    properties.optString("name"),
                    properties.optString("street"),
                    properties.optString("city"),
                    properties.optString("district")
                ) ?: continue

                val address = buildAddress(
                    properties.optString("housenumber"),
                    properties.optString("street"),
                    properties.optString("district"),
                    properties.optString("city"),
                    properties.optString("state"),
                    properties.optString("postcode"),
                    properties.optString("country")
                ).ifBlank { name }

                add(
                    FreePlaceSuggestion(
                        id = "$prefix-${properties.optString("osm_type")}-" +
                            "${properties.optString("osm_id")}-$index",
                        name = name,
                        address = address,
                        latitude = coordinates
                            ?.optDouble(1)
                            ?.takeUnless(Double::isNaN),
                        longitude = coordinates
                            ?.optDouble(0)
                            ?.takeUnless(Double::isNaN),
                        source = "Photon",
                        category = firstNonBlank(
                            properties.optString("osm_value"),
                            properties.optString("type")
                        ).orEmpty()
                    )
                )
            }
        }
    }

    private fun parseNominatim(
        payload: String,
        prefix: String
    ): List<FreePlaceSuggestion> {
        val array = runCatching { JSONArray(payload) }.getOrNull()
            ?: return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val addressObject = item.optJSONObject("address")
                val displayName = item.optString("display_name")

                val name = firstNonBlank(
                    item.optString("name"),
                    displayName.substringBefore(",")
                ) ?: continue

                add(
                    FreePlaceSuggestion(
                        id = "$prefix-${item.optString("osm_type")}-" +
                            "${item.optString("osm_id")}-$index",
                        name = name,
                        address = displayName.ifBlank { name },
                        latitude = item.optString("lat").toDoubleOrNull(),
                        longitude = item.optString("lon").toDoubleOrNull(),
                        source = "Nominatim",
                        category = firstNonBlank(
                            item.optString("type"),
                            item.optString("category")
                        ).orEmpty()
                    )
                )
            }
        }
    }

    private fun rankAndMerge(
        query: String,
        destination: String,
        category: PlaceSearchCategory,
        results: List<FreePlaceSuggestion>,
        limit: Int
    ): List<FreePlaceSuggestion> {
        val queryTokens = tokenize(query)
        val destinationTokens = tokenize(destination)

        return results
            .distinctBy {
                "${normalize(it.name)}|${normalize(it.address)}|" +
                    "${it.latitude}|${it.longitude}"
            }
            .map { suggestion ->
                val normalizedName = normalize(suggestion.name)
                val combined = normalize(
                    "${suggestion.name} ${suggestion.address} ${suggestion.category}"
                )
                var score = 0

                queryTokens.forEach { token ->
                    if (normalizedName.contains(token)) score += 20
                    if (combined.contains(token)) score += 8
                }

                destinationTokens.forEach { token ->
                    if (combined.contains(token)) score += 5
                }

                category.rankingWords.forEach { word ->
                    if (combined.contains(normalize(word))) score += 7
                }

                if (
                    suggestion.latitude != null &&
                    suggestion.longitude != null
                ) {
                    score += 2
                }

                suggestion to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }

    private fun tokenize(value: String): List<String> =
        normalize(value)
            .split(" ")
            .filter { it.length >= 2 }
            .distinct()

    private fun normalize(value: String): String =
        value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun firstNonBlank(vararg values: String): String? =
        values.firstOrNull { it.isNotBlank() }

    private fun buildAddress(vararg parts: String): String =
        parts
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
}

fun freePlaceMapsUrl(
    suggestion: FreePlaceSuggestion
): String {
    return if (
        suggestion.latitude != null &&
        suggestion.longitude != null
    ) {
        "https://www.google.com/maps/search/?api=1&query=" +
            Uri.encode(
                "${suggestion.latitude},${suggestion.longitude}"
            )
    } else {
        "https://www.google.com/maps/search/?api=1&query=" +
            Uri.encode(
                listOf(suggestion.name, suggestion.address)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
            )
    }
}
