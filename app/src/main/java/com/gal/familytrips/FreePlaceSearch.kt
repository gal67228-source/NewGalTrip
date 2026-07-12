package com.gal.familytrips

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

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
    val category: String = "",
    val distanceKm: Double? = null,
    val isLocal: Boolean = false
)

private data class DestinationArea(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val south: Double,
    val north: Double,
    val west: Double,
    val east: Double
)

object FreePlaceSearch {
    private val destinationCache =
        mutableMapOf<String, DestinationArea?>()

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

        val area = resolveDestinationArea(destination)

        // שלב 1: חיפוש מקומי ומוגבל לאזור היעד.
        val localResults = if (area != null) {
            val boundedNominatim = searchNominatim(
                query = normalizedQuery,
                destination = destination,
                category = category,
                limit = 15,
                area = area,
                bounded = true
            )

            val biasedPhoton = searchPhoton(
                query = normalizedQuery,
                destination = destination,
                category = category,
                limit = 15,
                area = area
            )

            rankAndMerge(
                query = normalizedQuery,
                destination = destination,
                category = category,
                results = boundedNominatim + biasedPhoton,
                area = area,
                localOnly = true,
                limit = limit
            )
        } else {
            emptyList()
        }

        // אם יש מספיק תוצאות מקומיות, לא מבצעים חיפוש עולמי בכלל.
        if (localResults.size >= 5) {
            return@withContext localResults.take(limit)
        }

        // שלב 2: רק במקרה שאין מספיק תוצאות מקומיות, מוסיפים תוצאות רחוקות.
        val globalResults = searchPhoton(
            query = normalizedQuery,
            destination = destination,
            category = category,
            limit = 15,
            area = area
        ) + searchNominatim(
            query = normalizedQuery,
            destination = destination,
            category = category,
            limit = 12,
            area = null,
            bounded = false
        )

        val rankedGlobal = rankAndMerge(
            query = normalizedQuery,
            destination = destination,
            category = category,
            results = globalResults,
            area = area,
            localOnly = false,
            limit = limit * 2
        )

        (localResults + rankedGlobal)
            .distinctBy {
                "${normalize(it.name)}|${normalize(it.address)}|" +
                    "${it.latitude}|${it.longitude}"
            }
            .sortedWith(
                compareByDescending<FreePlaceSuggestion> { it.isLocal }
                    .thenBy { it.distanceKm ?: Double.MAX_VALUE }
            )
            .take(limit)
    }

    private fun resolveDestinationArea(
        destination: String
    ): DestinationArea? {
        val key = destination.trim().lowercase(Locale.ROOT)
        if (key.isBlank()) return null

        synchronized(destinationCache) {
            if (destinationCache.containsKey(key)) {
                return destinationCache[key]
            }
        }

        val destinationQuery = destination
            .substringAfterLast("→")
            .trim()

        val url = URL(
            "https://nominatim.openstreetmap.org/search" +
                "?q=${Uri.encode(destinationQuery)}" +
                "&format=jsonv2" +
                "&limit=1" +
                "&addressdetails=1"
        )

        val payload = request(url)
        val result = payload?.let(::parseDestinationArea)

        synchronized(destinationCache) {
            destinationCache[key] = result
        }
        return result
    }

    private fun parseDestinationArea(
        payload: String
    ): DestinationArea? {
        val array = runCatching { JSONArray(payload) }.getOrNull()
            ?: return null
        val item = array.optJSONObject(0) ?: return null

        val latitude = item.optString("lat").toDoubleOrNull()
            ?: return null
        val longitude = item.optString("lon").toDoubleOrNull()
            ?: return null

        val bbox = item.optJSONArray("boundingbox")
        val south = bbox?.optString(0)?.toDoubleOrNull()
            ?: latitude - 0.25
        val north = bbox?.optString(1)?.toDoubleOrNull()
            ?: latitude + 0.25
        val west = bbox?.optString(2)?.toDoubleOrNull()
            ?: longitude - 0.35
        val east = bbox?.optString(3)?.toDoubleOrNull()
            ?: longitude + 0.35

        return DestinationArea(
            displayName = item.optString("display_name"),
            latitude = latitude,
            longitude = longitude,
            south = south,
            north = north,
            west = west,
            east = east
        )
    }

    private fun searchPhoton(
        query: String,
        destination: String,
        category: PlaceSearchCategory,
        limit: Int,
        area: DestinationArea?
    ): List<FreePlaceSuggestion> {
        val categoryText = category.queryWords.joinToString(" ")
        val variants = listOf(
            listOf(query, categoryText, destination)
                .filter { it.isNotBlank() }.joinToString(" "),
            listOf(query, destination)
                .filter { it.isNotBlank() }.joinToString(" "),
            listOf(query, categoryText)
                .filter { it.isNotBlank() }.joinToString(" ")
        ).distinct()

        val results = mutableListOf<FreePlaceSuggestion>()

        variants.forEachIndexed { variantIndex, fullQuery ->
            var urlText =
                "https://photon.komoot.io/api/?" +
                    "q=${Uri.encode(fullQuery)}" +
                    "&limit=${limit.coerceAtMost(20)}" +
                    "&lang=en"

            if (area != null) {
                urlText +=
                    "&lat=${area.latitude}" +
                    "&lon=${area.longitude}" +
                    "&zoom=12" +
                    "&location_bias_scale=0.05"
            }

            request(URL(urlText))?.let { payload ->
                results += parsePhoton(payload, "photon-$variantIndex")
            }
        }

        return results
    }

    private fun searchNominatim(
        query: String,
        destination: String,
        category: PlaceSearchCategory,
        limit: Int,
        area: DestinationArea?,
        bounded: Boolean
    ): List<FreePlaceSuggestion> {
        val categoryText = category.queryWords.firstOrNull().orEmpty()
        val variants = listOf(
            listOf(query, categoryText, destination)
                .filter { it.isNotBlank() }.joinToString(", "),
            listOf(query, destination)
                .filter { it.isNotBlank() }.joinToString(", ")
        ).distinct()

        val results = mutableListOf<FreePlaceSuggestion>()

        variants.forEachIndexed { variantIndex, fullQuery ->
            var urlText =
                "https://nominatim.openstreetmap.org/search" +
                    "?q=${Uri.encode(fullQuery)}" +
                    "&format=jsonv2" +
                    "&addressdetails=1" +
                    "&limit=${limit.coerceAtMost(20)}"

            if (area != null) {
                urlText +=
                    "&viewbox=${area.west},${area.north}," +
                    "${area.east},${area.south}"

                if (bounded) {
                    urlText += "&bounded=1"
                }
            }

            request(URL(urlText))?.let { payload ->
                results += parseNominatim(
                    payload,
                    "nominatim-$variantIndex"
                )
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
                "GalFamilyTrips/3.7.2 Android"
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
        area: DestinationArea?,
        localOnly: Boolean,
        limit: Int
    ): List<FreePlaceSuggestion> {
        val queryTokens = tokenize(query)
        val destinationTokens = tokenize(destination)

        return results
            .distinctBy {
                "${normalize(it.name)}|${normalize(it.address)}|" +
                    "${it.latitude}|${it.longitude}"
            }
            .mapNotNull { suggestion ->
                val normalizedName = normalize(suggestion.name)
                val combined = normalize(
                    "${suggestion.name} ${suggestion.address} ${suggestion.category}"
                )

                val distance = if (
                    area != null &&
                    suggestion.latitude != null &&
                    suggestion.longitude != null
                ) {
                    haversineKm(
                        area.latitude,
                        area.longitude,
                        suggestion.latitude,
                        suggestion.longitude
                    )
                } else {
                    null
                }

                val insideBounds = if (
                    area != null &&
                    suggestion.latitude != null &&
                    suggestion.longitude != null
                ) {
                    suggestion.latitude in area.south..area.north &&
                        suggestion.longitude in area.west..area.east
                } else {
                    false
                }

                // חיפוש מקומי: מסירים תוצאות שאינן באזור.
                if (
                    localOnly &&
                    !insideBounds &&
                    (distance == null || distance > 80.0)
                ) {
                    return@mapNotNull null
                }

                var score = 0

                queryTokens.forEach { token ->
                    if (normalizedName.contains(token)) score += 25
                    if (combined.contains(token)) score += 10
                }

                destinationTokens.forEach { token ->
                    if (combined.contains(token)) score += 8
                }

                category.rankingWords.forEach { word ->
                    if (combined.contains(normalize(word))) score += 7
                }

                if (insideBounds) score += 100
                if (distance != null) {
                    score += when {
                        distance <= 5 -> 80
                        distance <= 15 -> 60
                        distance <= 40 -> 40
                        distance <= 80 -> 20
                        distance <= 200 -> 5
                        else -> -40
                    }
                }

                suggestion.copy(
                    distanceKm = distance,
                    isLocal = insideBounds ||
                        (distance != null && distance <= 80.0)
                ) to score
            }
            .sortedWith(
                compareByDescending<Pair<FreePlaceSuggestion, Int>> {
                    it.first.isLocal
                }
                    .thenByDescending { it.second }
                    .thenBy {
                        it.first.distanceKm ?: Double.MAX_VALUE
                    }
            )
            .map { it.first }
            .take(limit)
    }

    private fun haversineKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
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
